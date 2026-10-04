package apincer.music.room;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.os.Debug;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Opt-in measurements on private copies: this never changes the app's live database. */
@RunWith(AndroidJUnit4.class)
public class PathIndexPhoneBenchmark {
    private static final String QUERY = "SELECT * FROM musictag WHERE path = ?";
    private static final String URL = "http://127.0.0.1:9000/music/2122216336/file";
    private final List<File> copies = new ArrayList<>();
    private long nextRequest;
    private byte[] golden;

    @Test
    public void measurePathLookupAndStreamingContention() throws Exception {
        Assume.assumeTrue("Opt-in benchmark requires a private phone-v2.db asset",
                "true".equals(InstrumentationRegistry.getArguments().getString("pathIndexBenchmark")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SQLiteDatabase before = null;
        SQLiteDatabase indexed = null;
        try {
            before = copyDatabase(context, "before");
            indexed = copyDatabase(context, "indexed");
            indexed.execSQL("CREATE INDEX index_musictag_path ON musictag(path)");
            List<String> paths = new ArrayList<>();
            try (Cursor c = before.rawQuery("SELECT DISTINCT path FROM musictag WHERE path IS NOT NULL ORDER BY path", null)) {
                while (c.moveToNext()) paths.add(c.getString(0));
            }
            assertTrue(!paths.isEmpty());
            String[] probes = new String[64];
            for (int i = 0; i < 48; i++) probes[i] = paths.get(i * paths.size() / 48);
            for (int i = 48; i < 64; i++) probes[i] = "__missing_path_probe_" + i;
            for (String path : probes) assertEquals(rows(before, path), rows(indexed, path));
            emit(new JSONObject().put("kind", "query_plans").put("before", plan(before, probes[0]))
                    .put("indexed", plan(indexed, probes[0])).put("probe_count", 64)
                    .put("sqlite_version", scalar(before, "SELECT sqlite_version()"))
                    .put("tracks", scalar(before, "SELECT count(*) FROM musictag"))
                    .put("before_pages", scalar(before, "PRAGMA page_count"))
                    .put("indexed_pages", scalar(indexed, "PRAGMA page_count"))
                    .put("page_size", scalar(indexed, "PRAGMA page_size")));
            for (String variant : new String[]{"before", "indexed", "indexed", "before", "before", "indexed"}) {
                SQLiteDatabase db = variant.equals("before") ? before : indexed;
                for (String probe : probes) lookup(db, probe);
                long cpu = Debug.threadCpuTimeNanos();
                long start = System.nanoTime();
                long checksum = 0;
                for (int i = 0; i < 128; i++) checksum += lookup(db, probes[i % 64]);
                emit(new JSONObject().put("kind", "lookup").put("variant", variant).put("operations", 128)
                        .put("wall_ns", System.nanoTime() - start).put("cpu_ns", Debug.threadCpuTimeNanos() - cpu)
                        .put("checksum", checksum));
            }
            golden = request("GET", null, "reference", null).body;
            assertEquals("edb1ab13ad8e8dc03e8e283a40dd30d6344672671b0186a2b72a58bb01f1b676", sha256(golden));
            for (int i = 0; i < 64; i++) request("HEAD", null, "warmup", null);
            for (int i = 0; i < 16; i++) request("GET", seekRange(i), "warmup", null);
            for (String variant : new String[]{"idle", "before", "indexed", "indexed", "before", "idle"}) {
                phase(variant, variant.equals("before") ? before : indexed, probes);
            }
        } finally {
            if (before != null) before.close();
            if (indexed != null) indexed.close();
            for (File file : copies) context.deleteDatabase(file.getAbsolutePath());
        }
    }

    private SQLiteDatabase copyDatabase(Context context, String label) throws Exception {
        File file = new File(context.getCacheDir(), "path-probe-" + label + "-" + System.nanoTime() + ".db");
        copies.add(file);
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("phone-v2.db");
             OutputStream out = Files.newOutputStream(file.toPath())) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        }
        return SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING);
    }

    private void phase(String variant, SQLiteDatabase db, String[] probes) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch gate = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger(variant.equals("idle") ? 0 : 4);
        List<Future<JSONObject>> workers = new ArrayList<>();
        JSONObject environmentBefore = environment();
        long phaseStart = System.nanoTime();
        long deadline = phaseStart + TimeUnit.SECONDS.toNanos(45);
        try {
            if (!variant.equals("idle")) {
                for (int worker = 0; worker < 4; worker++) {
                    final int index = worker;
                    workers.add(pool.submit(() -> {
                        Thread.currentThread().setName("path-probe-" + index);
                        gate.await();
                        long start = System.nanoTime(), cpu = Debug.threadCpuTimeNanos(), checksum = 0;
                        int count = 0;
                        try {
                            for (; count < 2048 && System.nanoTime() < deadline && !Thread.currentThread().isInterrupted(); count++) {
                                checksum += lookup(db, probes[(count * 13 + index * 7) % 64]);
                            }
                            return new JSONObject().put("operations", count).put("start_ns", start)
                                    .put("end_ns", System.nanoTime()).put("cpu_ns", Debug.threadCpuTimeNanos() - cpu)
                                    .put("checksum", checksum);
                        } finally { active.decrementAndGet(); }
                    }));
                }
            }
            gate.countDown();
            JSONArray requests = new JSONArray();
            for (int i = 0; i < 64; i++) {
                requests.put(request("HEAD", null, "head", active).stats);
                requests.put(request("GET", "bytes=0-43", "header", active).stats);
                if (i < 48) requests.put(request("GET", seekRange(i), "seek", active).stats);
            }
            JSONArray completed = new JSONArray();
            for (Future<JSONObject> future : workers) {
                JSONObject result = future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                assertEquals(2048, result.getInt("operations"));
                completed.put(result);
            }
            emit(new JSONObject().put("kind", "phase").put("variant", variant).put("phase_start_ns", phaseStart)
                    .put("workers", completed).put("requests", requests)
                    .put("environment_before", environmentBefore).put("environment_after", environment()));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private JSONObject environment() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return new JSONObject().put("battery_temperature_tenths_c", battery == null ? -1
                : battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1))
                .put("thermal_status", power.getCurrentThermalStatus())
                .put("benchmark_process_gc_count", Debug.getRuntimeStat("art.gc.gc-count"));
    }

    private String seekRange(int index) {
        int start = 44 + index * 313337 % (golden.length - 44 - 65536);
        return "bytes=" + start + "-" + (start + 65535);
    }

    private Response request(String method, String range, String kind, AtomicInteger active) throws Exception {
        long wait = nextRequest - System.nanoTime();
        if (wait > 0) TimeUnit.NANOSECONDS.sleep(wait);
        nextRequest = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(50);
        java.net.HttpURLConnection con = (java.net.HttpURLConnection) new java.net.URL(URL).openConnection();
        con.setConnectTimeout(10000); con.setReadTimeout(20000); con.setRequestMethod(method);
        con.setRequestProperty("User-Agent", "LG webOS TV DLNADOC/1.50");
        con.setRequestProperty("Connection", "close");
        if (range != null) con.setRequestProperty("Range", range);
        long start = System.nanoTime();
        int activeAtStart = active == null ? 0 : active.get();
        try {
            int status = con.getResponseCode();
            long headers = System.nanoTime();
            byte[] body;
            try (InputStream in = con.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[65536]; int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                body = out.toByteArray();
            }
            if (method.equals("HEAD")) {
                assertEquals(200, status); assertEquals(0, body.length); assertEquals(golden.length, con.getContentLength());
            } else if (range != null) {
                String[] parts = range.substring(6).split("-");
                int lo = Integer.parseInt(parts[0]), hi = Integer.parseInt(parts[1]);
                assertEquals(206, status); assertTrue(Arrays.equals(Arrays.copyOfRange(golden, lo, hi + 1), body));
            } else { assertEquals(200, status); assertEquals(con.getContentLength(), body.length); }
            return new Response(body, new JSONObject().put("kind", kind).put("start_ns", start)
                    .put("headers_ns", headers).put("end_ns", System.nanoTime()).put("status", status)
                    .put("active_lookup_workers_at_start", activeAtStart).put("range", range == null ? JSONObject.NULL : range));
        } finally { con.disconnect(); }
    }

    private static long lookup(SQLiteDatabase db, String path) {
        long checksum = 0;
        try (Cursor c = db.rawQuery(QUERY, new String[]{path})) {
            while (c.moveToNext()) for (int col = 0; col < c.getColumnCount(); col++) {
                if (!c.isNull(col)) checksum += c.getString(col).length();
            }
        }
        return checksum;
    }

    private static List<List<String>> rows(SQLiteDatabase db, String path) {
        List<List<String>> result = new ArrayList<>();
        try (Cursor c = db.rawQuery(QUERY, new String[]{path})) {
            while (c.moveToNext()) {
                List<String> row = new ArrayList<>();
                for (int col = 0; col < c.getColumnCount(); col++) row.add(c.isNull(col) ? null : c.getString(col));
                result.add(row);
            }
        }
        return result;
    }

    private static String scalar(SQLiteDatabase db, String sql) {
        try (Cursor c = db.rawQuery(sql, null)) { assertTrue(c.moveToFirst()); return c.getString(0); }
    }

    private static String plan(SQLiteDatabase db, String path) {
        try (Cursor c = db.rawQuery("EXPLAIN QUERY PLAN " + QUERY, new String[]{path})) {
            assertTrue(c.moveToFirst()); return c.getString(3);
        }
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder text = new StringBuilder();
        for (byte value : digest) text.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return text.toString();
    }

    private static void emit(JSONObject object) {
        Bundle bundle = new Bundle();
        bundle.putString("stream", "\nBENCHJSON " + object + "\n");
        InstrumentationRegistry.getInstrumentation().sendStatus(0, bundle);
    }

    private static class Response {
        final byte[] body;
        final JSONObject stats;
        Response(byte[] body, JSONObject stats) { this.body = body; this.stats = stats; }
    }
}
