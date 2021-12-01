package apincer.android.tripmate.repository;

import android.app.Application;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SimpleSQLiteQuery;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.List;

import apincer.android.tripmate.database.OutdoorMateDatabase;
import apincer.android.tripmate.database.PlaceDao;
import apincer.android.tripmate.database.model.Place;
import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

public class PlaceRepository {
    private OutdoorMateDatabase db;
    private PlaceDao mPlaceDao;
    private LiveData<List<Place>> mAllPlaces;

    public PlaceRepository(Application application) {
        db = OutdoorMateDatabase.getDatabase(application);
        //mWordDao = db.wordDao();
        //mAllWords = mWordDao.getAllWords();
    }

    public void exportCSV() {
        ExportDataAsyncTask task = new ExportDataAsyncTask(db);
        task.execute();
    }

    public void importCSV() {
        ImportDataAsyncTask task = new ImportDataAsyncTask(db);
        task.execute();
    }

    private static void exports( RoomDatabase db, File exportDir, String tableName) {
       // File exportDir = new File(Environment.getExternalStorageDirectory(), "");
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }

        File file = new File(exportDir, tableName + ".csv");
        try {
            file.createNewFile();
            CSVWriter csvWrite = new CSVWriter(new FileWriter(file));
            Cursor curCSV = db.query("SELECT * FROM " + tableName, null);
            csvWrite.writeNext(curCSV.getColumnNames());
            while (curCSV.moveToNext()) {
                //Which column you want to exprort
                String arrStr[] = new String[curCSV.getColumnCount()];
                for (int i = 0; i < curCSV.getColumnCount() - 1; i++)
                    arrStr[i] = curCSV.getString(i);
                csvWrite.writeNext(arrStr);
            }
            csvWrite.close();
            curCSV.close();
        } catch (Exception sqlEx) {
            Log.e("PlaceRepository", sqlEx.getMessage(), sqlEx);
        }
    }

    private static void imports(RoomDatabase db, File exportDir, String tableName) {
        try {
            // Read CSV
            CSVReader csvReader = new CSVReader(new FileReader(new File(exportDir ,tableName+".csv")));
            String[] nextLine;
            int count = 0;
            StringBuilder columns = new StringBuilder();
            StringBuilder value = new StringBuilder();

            while ((nextLine = csvReader.readNext()) != null) {
                // nextLine[] is an array of values from the line
                for (int i = 0; i < nextLine.length - 1; i++) {
                    if (count == 0) {
                        if (i == nextLine.length - 2)
                            columns.append(nextLine[i]);
                        else
                            columns.append(nextLine[i]).append(",");
                    } else {
                        if (i == nextLine.length - 2)
                            value.append("'").append(nextLine[i]).append("'");
                        else
                            value.append("'").append(nextLine[i]).append("',");
                    }
                }

                if(value.length()>0) {
                    Log.d("PlaceRepository", columns + "-------" + value);
                    // Insert Data to Database
                    SimpleSQLiteQuery query = new SimpleSQLiteQuery("Insert INTO " + tableName + " (" + columns + ") " + "values(" + value + ")",
                            new Object[]{});
                    db.getOpenHelper().getWritableDatabase().execSQL(query.getSql());
                    value.setLength(0);
                }
            }
        } catch (Exception sqlEx) {
            Log.e("PlaceRepository", sqlEx.getMessage(), sqlEx);
        }
    }

    private static class InsertAsyncTask extends AsyncTask<Place, Void, Void> {
        private PlaceDao mAsyncTaskDao;
        InsertAsyncTask(PlaceDao dao) {
            mAsyncTaskDao = dao;
        }

        @Override
        protected Void doInBackground(final Place... params) {
            mAsyncTaskDao.insert(params[0]);
            return null;
        }
    }

    private static class ExportDataAsyncTask extends AsyncTask<Place, Void, Void> {
        private RoomDatabase db;
        ExportDataAsyncTask(RoomDatabase db) {
            this.db = db;
        }

        @Override
        protected Void doInBackground(final Place... params) {
            File exportDir = new File(Environment.getExternalStorageDirectory(), "OutdoorMate");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }
            exports(db, exportDir, "feature");
            exports(db, exportDir, "country");
            exports(db, exportDir, "category");
            exports(db, exportDir, "place");
            exports(db, exportDir, "place_features");
            return null;
        }
    }

    private static class ImportDataAsyncTask extends AsyncTask<Place, Void, Void> {
        private RoomDatabase db;
        ImportDataAsyncTask(RoomDatabase db) {
            this.db = db;
        }

        @Override
        protected Void doInBackground(final Place... params) {
            File exportDir = new File(Environment.getExternalStorageDirectory(), "OutdoorMate");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }
            imports(db, exportDir, "feature");
            imports(db, exportDir, "country");
            imports(db, exportDir, "category");
            imports(db, exportDir, "place");
            imports(db, exportDir, "place_feature");
            return null;
        }
    }
}
