import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Inspect the installed Room database without reading track names or changing its schema. */
public final class ReadOnlyPathPlan {
    public static void main(String[] args) {
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(args[0], null,
                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS)) {
            print(db, "SELECT sqlite_version()", null);
            print(db, "PRAGMA user_version", null);
            print(db, "SELECT count(*) FROM musictag", null);
            print(db, "SELECT name,sql FROM sqlite_master WHERE type='index' AND tbl_name='musictag' ORDER BY name", null);
            print(db, "EXPLAIN QUERY PLAN SELECT * FROM musictag WHERE path = ?", new String[]{"__query_plan_probe__"});
        }
    }

    private static void print(SQLiteDatabase db, String sql, String[] args) {
        System.out.println("QUERY " + sql);
        try (Cursor cursor = db.rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                for (int i = 0; i < cursor.getColumnCount(); i++) {
                    if (i != 0) System.out.print("\t");
                    System.out.print(cursor.isNull(i) ? "NULL" : cursor.getString(i));
                }
                System.out.println();
            }
        }
    }
}
