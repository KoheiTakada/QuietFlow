package dev.quietflow.core;

import java.util.Locale;
import java.util.regex.Pattern;

/** Conservative post-processing. Never let a model dismiss a protected notification. */
public final class PolicyEngine {
    private static final Pattern SENSITIVE = Pattern.compile("(?i)(otp|2fa|verification|verify|passcode|your code|otp code|認証|確認コード|確認番号|ワンタイム|二段階|ログイン|不正|異常|緊急|救急|地震|津波|災害|警報|詐欺|送金|決済エラー|支払い失敗|カード停止|password|パスワード|security alert|account locked)");
    private static final Pattern PROMO = Pattern.compile("(?i)(セール|sale|クーポン|割引|ポイント還元|タイムセール|キャンペーン|本日限定|期間限定|特別価格|広告|お得|promotion|discount|offer)");
    private static final Pattern STATUS = Pattern.compile("(?i)(配送中|配達中|同期中|アップロード中|ダウンロード中|接続中|バックアップ中|進行中|tracking|in progress|ongoing|syncing)");
    private static final Pattern ARRIVAL = Pattern.compile("(?i)(配達完了|配達しました|到着しました|受け取り完了|delivered|arrived|受取完了)");
    private static final Pattern ASK = Pattern.compile("(?i)(お願いします|お願いできますか|できますか[？?]?|変更できますか|回答|返信|至急|必要です|対応してください|please respond|action required|reply needed)");

    public boolean protectedContent(NotificationInput n) {
        String t = n.title + " " + n.body;
        String c = n.category.toLowerCase(Locale.ROOT);
        return n.ongoing || n.packageName.equals("android") || n.packageName.equals("com.android.systemui") || n.packageName.equals("com.google.android.gms")
                || n.packageName.equals("com.android.phone")
                || c.equals("call") || c.equals("alarm") || c.equals("navigation")
                || c.equals("transport") || c.equals("service")
                || SENSITIVE.matcher(t).find();
    }
    public Verdict local(NotificationInput n) {
        if (protectedContent(n)) return new Verdict(Level.P0, 1.0, "保護対象・認証・システム通知");
        String t = n.title + " " + n.body;
        if (t.trim().isEmpty()) return new Verdict(Level.P3, 0.0, "本文を取得できない");
        if (ASK.matcher(t).find()) return new Verdict(Level.P0, 0.72, "返信や対応が必要そう");
        if (STATUS.matcher(t).find()) return new Verdict(Level.P2, 0.72, "進行中の状態");
        if (ARRIVAL.matcher(t).find()) return new Verdict(Level.P1, 0.72, "完了イベント");
        if (PROMO.matcher(t).find()) return new Verdict(Level.P4, 0.70, "販促と思われる内容");
        return new Verdict(Level.P3, 0.35, "判断材料が不足");
    }
    /** Only strong, explicit model output may alter another app's notifications. */
    public boolean canAutoAct(NotificationInput n, Verdict v, boolean active, boolean modelVerified) {
        if (!active || !modelVerified || protectedContent(n)) return false;
        if (v.level == Level.P0) return false;
        double threshold;
        switch (v.level) {
            case P4: threshold = 0.98; break;
            case P3: threshold = 0.94; break;
            case P2: threshold = 0.93; break;
            case P1: threshold = 0.93; break;
            default: return false;
        }
        return v.confidence >= threshold;
    }
}
