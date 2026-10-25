package dev.quietflow.core;
public final class Verdict {
    public final Level level;
    public final double confidence;
    public final String reason;
    public Verdict(Level level, double confidence, String reason) {
        this.level = level; this.confidence = confidence; this.reason = reason;
    }
}
