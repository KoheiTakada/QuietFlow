package dev.quietflow;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import dev.quietflow.core.*;

public final class MainActivity extends Activity {
    private static final int INK = 0xff161b26;
    private static final int SUB = 0xff657186;
    private static final int BG = 0xfff5f7fb;
    private static final int ACCENT = 0xff345de3;
    private static final String[] LEVEL_NAMES={"P0 · 要対応","P1 · 一時通知","P2 · 状態表示","P3 · 後で確認","P4 · 自動処理"};
    private final PolicyEngine policy=new PolicyEngine();
    private HistoryStore history;
    private SettingsStore prefs;
    private LinearLayout screen, feed;
    private TextView stats;
    private final BroadcastReceiver receiver=new BroadcastReceiver() { @Override public void onReceive(Context c,Intent i) { refreshFeed(); } };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setNavigationBarColor(BG);
        history=new HistoryStore(this); prefs=new SettingsStore(this);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        screen=new LinearLayout(this);screen.setPadding(dp(18),dp(24),dp(18),dp(24));screen.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(screen);setContentView(scroll);
        header();permissionCard();modeCard();keyCard();historyHeader();refreshFeed();
    }
    private void header() {
        TextView title=text("QuietFlow",30,true,INK);
        screen.addView(title);
        TextView desc=text("通知を仕分け、注意を必要なことに集中させる",13,false,SUB);desc.setPadding(0,dp(2),0,dp(16));screen.addView(desc);
        stats=text("通知を待機中",14,false,SUB);screen.addView(stats);space(screen,12);
    }
    private void permissionCard() {
        LinearLayout card=card(screen);
        card.addView(text("1. 通知へのアクセス",18,true,INK));
        TextView detail=text("通知の内容を取得するにはAndroidの通知アクセスを許可する必要があります。",13,false,SUB);
        detail.setPadding(0,dp(6),0,dp(12));card.addView(detail);
        Button allow=button("通知アクセスを開く");card.addView(allow);
        allow.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        space(card,8);
        Button post=button("アプリ自身の通知を許可");card.addView(post);
        post.setOnClickListener(v->{ if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1); else Toast.makeText(this,"許可済み／このOSでは不要",Toast.LENGTH_SHORT).show(); });
    }
    private void modeCard() {
        LinearLayout card=card(screen);
        card.addView(text("2. 動作モード",18,true,INK));
        TextView hint=text("初期設定は観察のみ。自動整理をオンにすると、高確信度の通知に限り削除や再表示を行います。",13,false,SUB);
        hint.setPadding(0,dp(6),0,dp(8));card.addView(hint);
        Switch mode=new Switch(this);
        mode.setText("通知を自動整理する");mode.setTextColor(INK);mode.setChecked(prefs.active());card.addView(mode);
        final boolean[] programmatic = {false};
        mode.setOnCheckedChangeListener((btn,checked)->{
            if (programmatic[0]) return;
            if(!checked){prefs.active(false);Toast.makeText(this,"観察モードに戻しました",Toast.LENGTH_SHORT).show();return;}
            programmatic[0]=true;
            mode.setChecked(false);
            programmatic[0]=false;
            new AlertDialog.Builder(this).setTitle("自動整理を有効化しますか？")
                .setMessage("AIや過去の修正で高確信度と判定した通知を、通知センターから削除・置換します。誤判定の可能性があります。まず観察モードで結果を確認することを推奨します。")
                .setNegativeButton("やめる",null)
                .setPositiveButton("有効化",(d,w)->{prefs.active(true);programmatic[0]=true;mode.setChecked(true);programmatic[0]=false;})
                .show();
        });
    }
    private void keyCard() {
        LinearLayout card=card(screen);
        card.addView(text("3. Jev 判定エンジン",18,true,INK));
        TextView desc=text("APIキーを設定するとJevがP0〜P4を分類します。未設定や通信失敗時はローカル推定のみ（自動削除なし）。",13,false,SUB);
        desc.setPadding(0,dp(6),0,dp(10));card.addView(desc);
        TextView status=text(prefs.hasApiKey()?"APIキー：保存済み":"APIキー：未設定",13,true,SUB);card.addView(status);
        EditText field=new EditText(this);
        field.setSingleLine(true);field.setTextSize(14);field.setHint(prefs.hasApiKey()?"変更する場合のみ新しいキーを入力":"TypeSafe AI APIキー");
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(field,new LinearLayout.LayoutParams(-1,dp(52)));
        space(card,8);
        Button save=button("APIキーを暗号化して保存");card.addView(save);
        save.setOnClickListener(v->{try {String key=field.getText().toString().trim();if(key.isEmpty())return;prefs.saveKey(key);field.setText("");status.setText("APIキー：保存済み");Toast.makeText(this,"保存しました",Toast.LENGTH_SHORT).show();}catch(Exception ex){Toast.makeText(this,"保存できませんでした",Toast.LENGTH_LONG).show();}});
        CheckBox consent=new CheckBox(this);
        consent.setText("通知の本文をJev APIへ送信することを許可");consent.setTextColor(INK);consent.setTextSize(13);consent.setChecked(prefs.cloudConsent());
        card.addView(consent);
        consent.setOnCheckedChangeListener((b,checked)->prefs.cloudConsent(checked));
        TextView caution=text("認証・金融・緊急・システム関連はAPI送信対象から除外。その他の通知は数字コード・メールアドレス・電話番号を一部マスクして送信します。完全匿名化ではありません。",12,false,SUB);
        caution.setPadding(0,dp(4),0,dp(8));card.addView(caution);
        Button delete=button("APIキーを削除");card.addView(delete);
        delete.setOnClickListener(v->{prefs.removeKey();status.setText("APIキー：未設定");Toast.makeText(this,"キーを削除しました",Toast.LENGTH_SHORT).show();});
    }
    private void historyHeader() {
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(16);screen.addView(row,p);
        TextView label=text("通知履歴・判断の修正",20,true,INK);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        Button refresh=button("更新");row.addView(refresh);refresh.setOnClickListener(v->refreshFeed());
        TextView hint=text("各通知の分類を変更すると、同じアプリ・同じタイトル・同じ本文の次回通知に反映します。履歴は最新1000件まで端末内に保持。",12,false,SUB);
        hint.setPadding(0,dp(8),0,dp(10));screen.addView(hint);
        feed=new LinearLayout(this);feed.setOrientation(LinearLayout.VERTICAL);screen.addView(feed);
        space(screen,18);
        Button demo=button("サンプル4件で分類を試す");screen.addView(demo);demo.setOnClickListener(v->seedExamples());
        space(screen,8);
        Button erase=button("履歴と修正学習を消去");screen.addView(erase);
        erase.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("通知履歴を削除").setMessage("記録と修正済みの分類をすべて消去します。元アプリの通知には影響しません。")
            .setNegativeButton("キャンセル",null).setPositiveButton("消去",(d,w)->{history.deleteHistory();refreshFeed();}).show());
    }
    private void refreshFeed() {
        if(feed==null)return;
        feed.removeAllViews();
        List<HistoryStore.Item> items=history.latest(60);
        int[] counts=new int[5];for(HistoryStore.Item i:items)counts[Level.parse(i.level).ordinal()]++;
        stats.setText("履歴（最新60件）  要対応 "+counts[0]+"  ·  後で "+counts[3]+"  ·  自動処理 "+counts[4]);
        if(items.isEmpty()) {
            LinearLayout empty=card(feed);
            empty.addView(text("まだ通知はありません",16,true,INK));
            empty.addView(text("通知アクセス許可後、受信した通知がここに記録されます。",13,false,SUB));
            return;
        }
        for(HistoryStore.Item item:items) {
            LinearLayout c=card(feed);
            LinearLayout titleRow=new LinearLayout(this);titleRow.setOrientation(LinearLayout.HORIZONTAL);titleRow.setGravity(Gravity.CENTER_VERTICAL);c.addView(titleRow);
            TextView label=text(item.level+"  "+Level.parse(item.level).japanese,12,true,levelColor(item.level));titleRow.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            TextView time=text(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(item.timestamp)),11,false,SUB);titleRow.addView(time);
            TextView app=text(appLabel(item.app),12,false,SUB);app.setPadding(0,dp(6),0,0);c.addView(app);
            c.addView(text(item.title.isEmpty()?"（タイトルなし）":item.title,15,true,INK));
            if(!item.body.isEmpty()) {
                TextView body=text(item.body,13,false,INK);
                body.setMaxLines(3);body.setPadding(0,dp(4),0,0);c.addView(body);
            }
            TextView why=text(item.reason+"  ·  "+item.origin,11,false,SUB);why.setPadding(0,dp(7),0,dp(7));c.addView(why);
            Button change=button("分類を修正");c.addView(change);
            change.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("この通知の分類を変更")
                .setSingleChoiceItems(LEVEL_NAMES,Level.parse(item.level).ordinal(),(dialog,which)->{
                    history.correct(item,Level.values()[which]);dialog.dismiss();refreshFeed();
                }).setNegativeButton("キャンセル",null).show());
        }
    }
    private void seedExamples() {
        NotificationInput[] inputs={
            new NotificationInput("demo.chat","チャットの相手","明日の予定を変更できますか？","msg",false),
            new NotificationInput("demo.delivery","配達中","荷物は配達中です","status",false),
            new NotificationInput("demo.shopping","タイムセール","本日限定クーポンを配布","promo",false),
            new NotificationInput("demo.system","認証コード","ログインコード 123456","msg",false)
        };
        for(int i=0;i<inputs.length;i++) history.record("demo-"+System.currentTimeMillis()+"-"+i,inputs[i],policy.local(inputs[i]),"demo-local");
        refreshFeed();
    }
    @Override protected void onResume() { super.onResume();if(feed!=null)refreshFeed(); }
    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter=new IntentFilter(HistoryStore.UPDATED);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver,filter);
    }
    @Override protected void onStop() {unregisterReceiver(receiver);super.onStop();}
    @Override protected void onDestroy() {if(history!=null)history.close();super.onDestroy();}
    private LinearLayout card(LinearLayout target) {
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(16),dp(16),dp(16));
        c.setBackground(round(Color.WHITE,16));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);target.addView(c,p);return c;
    }
    private Button button(String text) {Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(12);b.setMinHeight(0);b.setMinimumHeight(0);return b;}
    private TextView text(String value,int size,boolean bold,int color) {TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable round(int color,int radius) {GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private void space(LinearLayout layout,int height){View v=new View(this);layout.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
    private String appLabel(String pkg) {
        if(pkg.startsWith("demo.")) return pkg.substring(5) + "（デモ）";
        try {return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}
        catch(Exception e) {return pkg;}
    }
    private int levelColor(String level) {switch(Level.parse(level)){case P0:return 0xffbe2945;case P1:return 0xffaa6610;case P2:return 0xff3765ba;case P3:return 0xff596376;default:return 0xff75818e;}}
}
