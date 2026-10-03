package apincer.music.room;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import apincer.music.room.entity.TrackEntity;

@RunWith(AndroidJUnit4.class)
public class PathIndexMigrationTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final String name = "path-index-test-" + UUID.randomUUID() + ".db";
    private MusicRoomDatabase room;

    @After
    public void cleanup() {
        if (room != null) room.close();
        context.deleteDatabase(name);
    }

    @Test
    public void freshDatabaseUsesNonUniqueIndexAndKeepsSharedPaths() {
        room = openRoom();
        for (int i = 1; i <= 2; i++) {
            TrackEntity track = new TrackEntity();
            track.setId(i);
            track.setUniqueKey("cue-track-" + i);
            track.setPath("/music/shared.flac");
            room.trackDao().insert(track);
        }
        assertEquals(2, room.trackDao().getByPath("/music/shared.flac").size());
        assertIndex(room.getOpenHelper().getWritableDatabase());
        room.close();
        room = openRoom();
        assertEquals(2, room.trackDao().getByPath("/music/shared.flac").size());
        assertIndex(room.getOpenHelper().getWritableDatabase());
    }

    @Test
    public void versionTwoMigrationPreservesEveryTrackAndHistoryField() throws Exception {
        List<List<String>> tracks;
        List<List<String>> history;
        try (SQLiteDatabase old = createHistorical(2)) {
            seedTracks(old);
            old.execSQL("INSERT INTO listening_history VALUES (101,123456789,4,2,'started','completed','skipped')");
            tracks = snapshot(old.rawQuery("SELECT * FROM musictag ORDER BY id", null));
            history = snapshot(old.rawQuery("SELECT * FROM listening_history ORDER BY trackId", null));
        }
        room = openRoom();
        SupportSQLiteDatabase migrated = room.getOpenHelper().getWritableDatabase();
        assertEquals(tracks, snapshot(migrated.query("SELECT * FROM musictag ORDER BY id")));
        assertEquals(history, snapshot(migrated.query("SELECT * FROM listening_history ORDER BY trackId")));
        assertEquals(2, room.trackDao().getByPath("/music/shared.flac").size());
        assertEquals(1, room.trackDao().getByPath("/music/quote'_%日本語.flac").size());
        assertIndex(migrated);
    }

    @Test
    public void versionOneMigrationChainPreservesTracksAndCreatesEmptyHistory() throws Exception {
        List<List<String>> tracks;
        try (SQLiteDatabase old = createHistorical(1)) {
            seedTracks(old);
            tracks = snapshot(old.rawQuery("SELECT * FROM musictag ORDER BY id", null));
        }
        room = openRoom();
        SupportSQLiteDatabase migrated = room.getOpenHelper().getWritableDatabase();
        assertEquals(tracks, snapshot(migrated.query("SELECT * FROM musictag ORDER BY id")));
        assertTrue(snapshot(migrated.query("SELECT * FROM listening_history")).isEmpty());
        assertEquals(2, room.trackDao().getByPath("/music/shared.flac").size());
        assertIndex(migrated);
    }

    private MusicRoomDatabase openRoom() {
        // No destructive fallback: migration or schema errors must fail this test.
        return Room.databaseBuilder(context, MusicRoomDatabase.class, name)
                .allowMainThreadQueries()
                .addMigrations(MusicRoomDatabase.MIGRATION_1_2, MusicRoomDatabase.MIGRATION_2_3)
                .build();
    }

    private SQLiteDatabase createHistorical(int version) throws Exception {
        SQLiteDatabase old = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null);
        StringBuilder script = new StringBuilder();
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                testContext.getAssets().open("room-v2.sql"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("--")) script.append(line).append('\n');
            }
        }
        for (String statement : script.toString().split(";")) {
            if (statement.trim().isEmpty()) continue;
            // Version 1 reconstructs the documented pre-listening-history schema.
            if (version == 1 && statement.contains("`listening_history`")) continue;
            old.execSQL(statement);
        }
        old.setVersion(version);
        return old;
    }

    private static void seedTracks(SQLiteDatabase old) {
        for (int i = 0; i < 4; i++) {
            ContentValues values = new ContentValues();
            try (Cursor columns = old.rawQuery("PRAGMA table_info(musictag)", null)) {
                while (columns.moveToNext()) {
                    if (columns.getInt(3) != 0) {
                        String column = columns.getString(1);
                        if ("TEXT".equals(columns.getString(2))) values.put(column, "");
                        else values.put(column, 0);
                    }
                }
            }
            values.put("id", 101 + i);
            values.put("uniqueKey", "historical-" + i);
            values.put("title", "Title " + i);
            values.put("isManaged", 1);
            values.put("audioDuration", 123.456);
            values.put("fileLastModified", 987654321L);
            if (i < 2) values.put("path", "/music/shared.flac");
            else if (i == 2) values.put("path", "/music/quote'_%日本語.flac");
            else values.putNull("path");
            old.insertOrThrow("musictag", null, values);
        }
    }

    private static List<List<String>> snapshot(Cursor cursor) {
        List<List<String>> rows = new ArrayList<>();
        try (Cursor c = cursor) {
            while (c.moveToNext()) {
                List<String> row = new ArrayList<>();
                for (int i = 0; i < c.getColumnCount(); i++) row.add(c.isNull(i) ? null : c.getString(i));
                rows.add(row);
            }
        }
        return rows;
    }

    private static void assertIndex(SupportSQLiteDatabase db) {
        assertEquals(3, db.getVersion());
        boolean found = false;
        try (Cursor indexes = db.query("PRAGMA index_list(musictag)")) {
            while (indexes.moveToNext()) {
                if ("index_musictag_path".equals(indexes.getString(1))) {
                    assertEquals(0, indexes.getInt(2));
                    found = true;
                }
            }
        }
        assertTrue(found);
        try (Cursor plan = db.query("EXPLAIN QUERY PLAN SELECT * FROM musictag WHERE path = ?",
                new Object[]{"/music/shared.flac"})) {
            assertTrue(plan.moveToFirst());
            assertTrue(plan.getString(3).contains("USING INDEX index_musictag_path"));
        }
    }
}
