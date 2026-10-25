package dev.quietflow;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import dev.quietflow.core.*;

/** Observe by default. Handles single notifications on a background worker with stale-event guards. */
public final class NotificationMonitor extends NotificationListenerService {
    private static final String CH_P1 = "quietflow_once";
    private static final String CH_P2 = "quietflow_state";
    private static final int MAX_TEXT = 1800;
    private static final String OWN_PACKAGE = "dev.quietflow";
    // Bound queued events. Overload must never grow without limit or silently delete notifications.
    private final ExecutorService worker = new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(96),new ThreadPoolExecutor.DiscardOldestPolicy());
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final PolicyEngine policy = new PolicyEngine();
    private HistoryStore history;
    private SettingsStore settings;
    private NotificationManager manager;

    @Override public void onCreate() {
        super.onCreate();
        history=new HistoryStore(this);
        settings=new SettingsStore(this);
        manager=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CH_P1,"一時通知",NotificationManager.IMPORTANCE_HIGH));
        manager.createNotificationChannel(new NotificationChannel(CH_P2,"状態",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public void onListenerConnected() {
        super.onListenerConnected();
        // Never dismiss pre-existing notifications on connection/reconnection.
    }
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn==null || OWN_PACKAGE.equals(sbn.getPackageName())) return;
        Notification n=sbn.getNotification();
        if(n==null) return;
        android.os.Bundle extras=n.extras;
        if(extras==null) return;
        String title=safe(extras.getCharSequence(Notification.EXTRA_TITLE));
        String text=safe(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if(text.isEmpty()) text=safe(extras.getCharSequence(Notification.EXTRA_TEXT));
        if(text.isEmpty()) text=safe(extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES));
        // StatusBarNotification instances may be reused; only pass immutable primitive snapshots.
        boolean ongoing=(n.flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE | Notification.FLAG_NO_CLEAR | Notification.FLAG_GROUP_SUMMARY))!=0;
        NotificationInput input=new NotificationInput(sbn.getPackageName(),limit(title,220),limit(text,MAX_TEXT),n.category,ongoing);
        String eventKey=sbn.getKey()+"/"+sbn.getPostTime();
        String originalKey=sbn.getKey();
        long postTime=sbn.getPostTime();
        android.app.PendingIntent openIntent=n.contentIntent;
        worker.execute(()->handle(input,eventKey,originalKey,postTime,openIntent));
    }
    private void handle(NotificationInput input, String eventKey, String notificationKey, long postedAt, android.app.PendingIntent openIntent) {
        Verdict verdict;
        String origin="local";
        boolean verified=false;
        if(policy.protectedContent(input)) {
            verdict=new Verdict(Level.P0,1.0,"安全保護：元の通知を維持");
            origin="protected";
        } else {
            Verdict correction=history.correctionFor(input);
            if(correction!=null) {
                verdict=correction;
                origin="learned";
                verified=true; // Explicitly user-corrected title for this package.
            } else {
                verdict=policy.local(input);
                String apiKey=settings.cloudConsent()? settings.readKey():"";
                if(!apiKey.isEmpty() && !input.title.concat(input.body).trim().isEmpty()) {
                    try {
                        JevClassifier.Result ai=new JevClassifier().classify(input,apiKey);
                        verdict=ai.verdict;
                        verified=true;
                        origin="jev:"+ai.model;
                    } catch (Exception e) {
                        String msg=e.getMessage();
                        origin=msg!=null && msg.matches("Jev HTTP [0-9]{3}") ? msg : "local-api-unavailable";
                        // Never log request/response bodies, credentials or exception URLs.
                    }
                }
            }
        }
        history.record(eventKey,input,verdict,origin);
        Intent changed=new Intent(HistoryStore.UPDATED).setPackage(getPackageName());
        sendBroadcast(changed);
        if(!policy.canAutoAct(input,verdict,settings.active(),verified)) return;
        Verdict accepted=verdict;
        ui.post(()->{
            if (!settings.active() || !isStillCurrent(notificationKey,postedAt,input)) return;
            if(accepted.level==Level.P1 || accepted.level==Level.P2) {
                if(!canPostOwnNotifications()) return; // Never lose the only copy.
                if(!publishManaged(notificationKey,input,accepted.level,openIntent)) return;
            }
            try { cancelNotification(notificationKey); }
            catch(Exception ignored) { /* Some notifications cannot be dismissed. */ }
        });
    }
    private boolean isStillCurrent(String key,long postedAt, NotificationInput input) {
        try {
            StatusBarNotification[] active=getActiveNotifications();
            if(active==null) return false;
            for(StatusBarNotification n:active) {
                if(n.getKey().equals(key) && n.getPostTime()==postedAt) {
                    android.os.Bundle b=n.getNotification().extras;
                    if (b==null || !limit(safe(b.getCharSequence(Notification.EXTRA_TITLE)),220).equals(input.title)) return false;
                    String body=safe(b.getCharSequence(Notification.EXTRA_BIG_TEXT));
                    if(body.isEmpty()) body=safe(b.getCharSequence(Notification.EXTRA_TEXT));
                    if(body.isEmpty()) body=safe(b.getCharSequenceArray(Notification.EXTRA_TEXT_LINES));
                    return limit(body,MAX_TEXT).equals(input.body);
                }
            }
        } catch(Exception ignored) { }
        return false;
    }
    private boolean canPostOwnNotifications() {
        return manager.areNotificationsEnabled() && (Build.VERSION.SDK_INT<33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED);
    }
    private boolean publishManaged(String key, NotificationInput input, Level level, android.app.PendingIntent openIntent) {
        String channelId=level==Level.P1?CH_P1:CH_P2;
        NotificationChannel channel=manager.getNotificationChannel(channelId);
        if(channel==null || channel.getImportance()==NotificationManager.IMPORTANCE_NONE) return false;
        Notification.Builder b = new Notification.Builder(this,channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(input.title.isEmpty()? "通知":input.title)
                .setContentText(input.body)
                .setStyle(new Notification.BigTextStyle().bigText(input.body))
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setGroup("quietflow-managed")
                .setAutoCancel(level==Level.P1);
        if(openIntent!=null) b.setContentIntent(openIntent);
        if(level==Level.P1) b.setTimeoutAfter(12_000);
        else b.setOnlyAlertOnce(true).setOngoing(false).setSilent(true);
        try {
            manager.notify("managed-"+key,Math.abs(key.hashCode()),b.build());
            return true;
        } catch(Exception ignored) { return false; }
    }
    private static String limit(String str,int max) { return str.length()>max?str.substring(0,max):str; }
    private static String safe(Object value) {
        if(value==null) return "";
        if(value instanceof CharSequence[]) return TextUtils.join(" / ",(CharSequence[])value);
        return value.toString();
    }
    @Override public void onDestroy() {
        worker.shutdownNow();
        if(history!=null)history.close();
        super.onDestroy();
    }
}
