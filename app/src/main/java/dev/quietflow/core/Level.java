package dev.quietflow.core;

public enum Level {
    P0("要対応"), P1("一時通知"), P2("状態表示"), P3("後で確認"), P4("自動処理");
    public final String japanese;
    Level(String japanese) { this.japanese = japanese; }
    public static Level parse(String name) {
        if (name == null) return P3;
        try { return Level.valueOf(name); } catch (IllegalArgumentException ex) { return P3; }
    }
}
