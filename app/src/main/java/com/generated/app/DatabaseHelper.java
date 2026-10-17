package com.generated.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "offline_youtube.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_VIDEOS = "videos";
    public static final String COL_ID = "id";
    public static final String COL_URI = "uri";
    public static final String COL_PATH = "path";
    public static final String COL_TITLE = "title";
    public static final String COL_DURATION = "duration";
    public static final String COL_SIZE = "size";
    public static final String COL_DATE_ADDED = "date_added";
    public static final String COL_VIEW_COUNT = "view_count";
    public static final String COL_COMPLETED_COUNT = "completed_count";
    public static final String COL_IS_FAVORITE = "is_favorite";
    public static final String COL_TAGS = "tags";
    public static final String COL_LAST_WATCHED = "last_watched";

    public static final String TABLE_PLAYLISTS = "playlists";
    public static final String COL_PL_ID = "pl_id";
    public static final String COL_PL_NAME = "name";
    public static final String COL_PL_CREATED = "created_at";

    public static final String TABLE_PLAYLIST_ITEMS = "playlist_items";
    public static final String COL_PI_ID = "pi_id";
    public static final String COL_PI_PLAYLIST_ID = "playlist_id";
    public static final String COL_PI_VIDEO_ID = "video_id";

    public static final String TABLE_HISTORY = "history";
    public static final String COL_H_ID = "h_id";
    public static final String COL_H_VIDEO_ID = "video_id";
    public static final String COL_H_TIMESTAMP = "timestamp";

    private static DatabaseHelper sInstance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new DatabaseHelper(context.getApplicationContext());
        }
        return sInstance;
    }

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_VIDEOS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_URI + " TEXT UNIQUE, "
                + COL_PATH + " TEXT, "
                + COL_TITLE + " TEXT, "
                + COL_DURATION + " INTEGER DEFAULT 0, "
                + COL_SIZE + " INTEGER DEFAULT 0, "
                + COL_DATE_ADDED + " INTEGER DEFAULT 0, "
                + COL_VIEW_COUNT + " INTEGER DEFAULT 0, "
                + COL_COMPLETED_COUNT + " INTEGER DEFAULT 0, "
                + COL_IS_FAVORITE + " INTEGER DEFAULT 0, "
                + COL_TAGS + " TEXT DEFAULT '', "
                + COL_LAST_WATCHED + " INTEGER DEFAULT 0"
                + ")");

        db.execSQL("CREATE TABLE " + TABLE_PLAYLISTS + " ("
                + COL_PL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_PL_NAME + " TEXT, "
                + COL_PL_CREATED + " INTEGER"
                + ")");

        db.execSQL("CREATE TABLE " + TABLE_PLAYLIST_ITEMS + " ("
                + COL_PI_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_PI_PLAYLIST_ID + " INTEGER, "
                + COL_PI_VIDEO_ID + " INTEGER, "
                + "UNIQUE(" + COL_PI_PLAYLIST_ID + ", " + COL_PI_VIDEO_ID + ")"
                + ")");

        db.execSQL("CREATE TABLE " + TABLE_HISTORY + " ("
                + COL_H_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_H_VIDEO_ID + " INTEGER, "
                + COL_H_TIMESTAMP + " INTEGER"
                + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PLAYLIST_ITEMS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PLAYLISTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_VIDEOS);
        onCreate(db);
    }

    public synchronized long insertOrUpdateVideo(String uri, String path, String title, long duration, long size, long dateAdded, String tags) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = db.query(TABLE_VIDEOS, new String[]{COL_ID}, COL_URI + "=?", new String[]{uri}, null, null, null);
        long id = -1;
        if (cursor != null && cursor.moveToFirst()) {
            id = cursor.getLong(0);
            cursor.close();
            ContentValues cv = new ContentValues();
            cv.put(COL_PATH, path);
            cv.put(COL_TITLE, title);
            cv.put(COL_DURATION, duration);
            cv.put(COL_SIZE, size);
            if (tags != null && !tags.isEmpty()) {
                cv.put(COL_TAGS, tags);
            }
            db.update(TABLE_VIDEOS, cv, COL_ID + "=?", new String[]{String.valueOf(id)});
        } else {
            if (cursor != null) cursor.close();
            ContentValues cv = new ContentValues();
            cv.put(COL_URI, uri);
            cv.put(COL_PATH, path);
            cv.put(COL_TITLE, title);
            cv.put(COL_DURATION, duration);
            cv.put(COL_SIZE, size);
            cv.put(COL_DATE_ADDED, dateAdded);
            cv.put(COL_TAGS, tags != null ? tags : "");
            id = db.insert(TABLE_VIDEOS, null, cv);
        }
        return id;
    }

    public synchronized void recordView(long videoId) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        db.execSQL("UPDATE " + TABLE_VIDEOS + " SET "
                + COL_VIEW_COUNT + " = " + COL_VIEW_COUNT + " + 1, "
                + COL_LAST_WATCHED + " = ? WHERE " + COL_ID + " = ?", new Object[]{now, videoId});

        ContentValues cv = new ContentValues();
        cv.put(COL_H_VIDEO_ID, videoId);
        cv.put(COL_H_TIMESTAMP, now);
        db.insert(TABLE_HISTORY, null, cv);
    }

    public synchronized void recordCompleted(long videoId) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_VIDEOS + " SET "
                + COL_COMPLETED_COUNT + " = " + COL_COMPLETED_COUNT + " + 1 WHERE " + COL_ID + " = ?", new Object[]{videoId});
    }

    public synchronized void toggleFavorite(long videoId, boolean isFavorite) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_IS_FAVORITE, isFavorite ? 1 : 0);
        db.update(TABLE_VIDEOS, cv, COL_ID + "=?", new String[]{String.valueOf(videoId)});
    }

    public synchronized List<VideoItem> getAllVideos() {
        return queryVideos(null, null, COL_DATE_ADDED + " DESC");
    }

    public synchronized List<VideoItem> getFavoriteVideos() {
        return queryVideos(COL_IS_FAVORITE + "=1", null, COL_LAST_WATCHED + " DESC, " + COL_ID + " DESC");
    }

    public synchronized List<VideoItem> getHistoryVideos() {
        List<VideoItem> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT v." + COL_ID + ", v." + COL_URI + ", v." + COL_PATH + ", v." + COL_TITLE + ", "
                + "v." + COL_DURATION + ", v." + COL_SIZE + ", v." + COL_DATE_ADDED + ", v." + COL_VIEW_COUNT + ", "
                + "v." + COL_COMPLETED_COUNT + ", v." + COL_IS_FAVORITE + ", v." + COL_TAGS + ", v." + COL_LAST_WATCHED + ", "
                + "MAX(h." + COL_H_TIMESTAMP + ") as last_seen "
                + "FROM " + TABLE_HISTORY + " h "
                + "JOIN " + TABLE_VIDEOS + " v ON h." + COL_H_VIDEO_ID + " = v." + COL_ID + " "
                + "GROUP BY v." + COL_ID + " "
                + "ORDER BY last_seen DESC LIMIT 50";
        Cursor c = db.rawQuery(query, null);
        if (c != null) {
            while (c.moveToNext()) {
                list.add(cursorToVideo(c));
            }
            c.close();
        }
        return list;
    }

    public synchronized List<VideoItem> queryVideos(String selection, String[] selectionArgs, String orderBy) {
        List<VideoItem> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_VIDEOS, null, selection, selectionArgs, null, null, orderBy);
        if (c != null) {
            while (c.moveToNext()) {
                list.add(cursorToVideo(c));
            }
            c.close();
        }
        return list;
    }

    public synchronized VideoItem getVideoById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_VIDEOS, null, COL_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        VideoItem item = null;
        if (c != null) {
            if (c.moveToFirst()) {
                item = cursorToVideo(c);
            }
            c.close();
        }
        return item;
    }

    private VideoItem cursorToVideo(Cursor c) {
        VideoItem v = new VideoItem();
        v.id = c.getLong(c.getColumnIndexOrThrow(COL_ID));
        v.uri = c.getString(c.getColumnIndexOrThrow(COL_URI));
        v.path = c.getString(c.getColumnIndexOrThrow(COL_PATH));
        v.title = c.getString(c.getColumnIndexOrThrow(COL_TITLE));
        v.duration = c.getLong(c.getColumnIndexOrThrow(COL_DURATION));
        v.size = c.getLong(c.getColumnIndexOrThrow(COL_SIZE));
        v.dateAdded = c.getLong(c.getColumnIndexOrThrow(COL_DATE_ADDED));
        v.viewCount = c.getInt(c.getColumnIndexOrThrow(COL_VIEW_COUNT));
        v.completedCount = c.getInt(c.getColumnIndexOrThrow(COL_COMPLETED_COUNT));
        v.isFavorite = c.getInt(c.getColumnIndexOrThrow(COL_IS_FAVORITE)) == 1;
        v.tags = c.getString(c.getColumnIndexOrThrow(COL_TAGS));
        v.lastWatched = c.getLong(c.getColumnIndexOrThrow(COL_LAST_WATCHED));
        return v;
    }

    public synchronized long createPlaylist(String name) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_PL_NAME, name);
        cv.put(COL_PL_CREATED, System.currentTimeMillis());
        return db.insert(TABLE_PLAYLISTS, null, cv);
    }

    public synchronized List<PlaylistItem> getPlaylists() {
        List<PlaylistItem> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT p." + COL_PL_ID + ", p." + COL_PL_NAME + ", p." + COL_PL_CREATED + ", "
                + "COUNT(pi." + COL_PI_ID + ") as item_count "
                + "FROM " + TABLE_PLAYLISTS + " p "
                + "LEFT JOIN " + TABLE_PLAYLIST_ITEMS + " pi ON p." + COL_PL_ID + " = pi." + COL_PI_PLAYLIST_ID + " "
                + "GROUP BY p." + COL_PL_ID + " ORDER BY p." + COL_PL_CREATED + " DESC";
        Cursor c = db.rawQuery(sql, null);
        if (c != null) {
            while (c.moveToNext()) {
                PlaylistItem pl = new PlaylistItem();
                pl.id = c.getLong(0);
                pl.name = c.getString(1);
                pl.createdAt = c.getLong(2);
                pl.videoCount = c.getInt(3);
                list.add(pl);
            }
            c.close();
        }
        return list;
    }

    public synchronized void addVideoToPlaylist(long playlistId, long videoId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_PI_PLAYLIST_ID, playlistId);
        cv.put(COL_PI_VIDEO_ID, videoId);
        db.insertWithOnConflict(TABLE_PLAYLIST_ITEMS, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public synchronized List<VideoItem> getPlaylistVideos(long playlistId) {
        List<VideoItem> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT v.* FROM " + TABLE_VIDEOS + " v "
                + "JOIN " + TABLE_PLAYLIST_ITEMS + " pi ON v." + COL_ID + " = pi." + COL_PI_VIDEO_ID + " "
                + "WHERE pi." + COL_PI_PLAYLIST_ID + " = ? ORDER BY pi." + COL_PI_ID + " ASC";
        Cursor c = db.rawQuery(sql, new String[]{String.valueOf(playlistId)});
        if (c != null) {
            while (c.moveToNext()) {
                list.add(cursorToVideo(c));
            }
            c.close();
        }
        return list;
    }

    public synchronized int getTotalVideosCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_VIDEOS, null);
        int count = 0;
        if (c != null && c.moveToFirst()) {
            count = c.getInt(0);
            c.close();
        }
        return count;
    }

    public synchronized int getTotalViewsCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT SUM(" + COL_VIEW_COUNT + ") FROM " + TABLE_VIDEOS, null);
        int sum = 0;
        if (c != null && c.moveToFirst()) {
            sum = c.getInt(0);
            c.close();
        }
        return sum;
    }
}
