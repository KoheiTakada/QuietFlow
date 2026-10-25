package dev.quietflow;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import dev.quietflow.core.Level;
import dev.quietflow.core.NotificationInput;
import dev.quietflow.core.Verdict;

/** App-private bounded history. Notification data never goes to Android logs or backups. */
final class HistoryStore extends SQLiteOpenHelper {
    static final String UPDATED = "dev.quietflow.HISTORY_UPDATED";
    static class Item {
        long id, timestamp;
        String key, app, title, body, level, reason, origin;
        double confidence;
    }
    HistoryStore(Context context) { super(context, "history.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY AUTOINCREMENT, event_key TEXT NOT NULL UNIQUE, package TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, priority TEXT NOT NULL, confidence REAL NOT NULL, reason TEXT NOT NULL, origin TEXT NOT NULL, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE corrections(package TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, priority TEXT NOT NULL, PRIMARY KEY(package,title,body))");
        db.execSQL("CREATE INDEX events_newest ON events(created_at DESC)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { throw new IllegalStateException("Migration missing"); }
    synchronized void record(String eventKey, NotificationInput input, Verdict verdict, String origin) {
        ContentValues values = new ContentValues();
        values.put("event_key", eventKey);
        values.put("package", input.packageName);
        values.put("title", input.title);
        values.put("body", input.body);
        values.put("priority", verdict.level.name());
        values.put("confidence", verdict.confidence);
        values.put("reason", verdict.reason);
        values.put("origin", origin);
        values.put("created_at", System.currentTimeMillis());
        SQLiteDatabase db = getWritableDatabase();
        db.insertWithOnConflict("events", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.execSQL("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY created_at DESC,id DESC LIMIT 1000)");
    }
    synchronized List<Item> latest(int limit) {
        List<Item> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,event_key,package,title,body,priority,confidence,reason,origin,created_at FROM events ORDER BY created_at DESC,id DESC LIMIT " + Math.min(1000, Math.max(1,limit)), null)) {
            while(c.moveToNext()) {
                Item i = new Item();
                i.id=c.getLong(0); i.key=c.getString(1); i.app=c.getString(2); i.title=c.getString(3); i.body=c.getString(4); i.level=c.getString(5); i.confidence=c.getDouble(6); i.reason=c.getString(7); i.origin=c.getString(8); i.timestamp=c.getLong(9);
                result.add(i);
            }
        }
        return result;
    }
    synchronized Verdict correctionFor(NotificationInput input) {
        if (input.title.trim().isEmpty()) return null;
        try (Cursor c=getReadableDatabase().rawQuery("SELECT priority FROM corrections WHERE package=? AND title=? AND body=?",new String[]{input.packageName,input.title,input.body})) {
            if(c.moveToFirst()) return new Verdict(Level.parse(c.getString(0)),1.0,"以前の分類修正を適用");
        }
        return null;
    }
    synchronized void correct(Item item, Level level) {
        SQLiteDatabase db=getWritableDatabase();
        ContentValues update=new ContentValues();
        update.put("priority",level.name()); update.put("confidence",1.0); update.put("reason","ユーザーが分類を修正"); update.put("origin","user");
        db.update("events",update,"id=?",new String[]{Long.toString(item.id)});
        if(!item.title.trim().isEmpty()) {
            ContentValues v = new ContentValues();
            v.put("package", item.app); v.put("title",item.title); v.put("body",item.body); v.put("priority",level.name());
            db.insertWithOnConflict("corrections",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        }
    }
    synchronized void deleteHistory() {
        SQLiteDatabase db=getWritableDatabase();
        db.delete("events",null,null);
        db.delete("corrections",null,null);
    }
}
