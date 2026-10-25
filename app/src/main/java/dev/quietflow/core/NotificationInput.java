package dev.quietflow.core;

/** All text in this object may be sensitive. Do not log it. */
public final class NotificationInput {
    public final String packageName, title, body, category;
    public final boolean ongoing;
    public NotificationInput(String packageName, String title, String body, String category, boolean ongoing) {
        this.packageName = packageName == null ? "" : packageName;
        this.title = title == null ? "" : title;
        this.body = body == null ? "" : body;
        this.category = category == null ? "" : category;
        this.ongoing = ongoing;
    }
}
