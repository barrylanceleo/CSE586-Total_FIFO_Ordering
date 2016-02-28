package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;

/**
 * GroupMessengerProvider is a key-value table. Once again, please note that we do not implement
 * full support for SQL as a usual ContentProvider does. We re-purpose ContentProvider's interface
 * to use it as a key-value table.
 * 
 * Please read:
 * 
 * http://developer.android.com/guide/topics/providers/content-providers.html
 * http://developer.android.com/reference/android/content/ContentProvider.html
 * 
 * before you start to get yourself familiarized with ContentProvider.
 * 
 * There are two methods you need to implement---insert() and query(). Others are optional and
 * will not be tested.
 * 
 * @author stevko
 *
 */
public class GroupMessengerProvider extends ContentProvider {

    private static final String TAG = "GroupMessengerProvider";
    private static MainDatabaseHelper dbHelper;
    private static final String DB_NAME = "KeyValueDB";
    private static final String TABLE_NAME = "KeyValueTable";
    public static  Uri CPUri;

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // You do not need to implement this.
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Log.v("insert", values.toString());

        if(CPUri.toString().equals(uri.toString()))
        {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            // Performs the insert and returns the ID of the new row.
            long rowId = db.insertWithOnConflict(
                    TABLE_NAME,            // The table to insert into.
                    null,                // A hack, SQLite sets this column value to null
                    // if values is empty.
                    values,                 // A map of column names, and the values to insert
                    // into the columns.
                    SQLiteDatabase.CONFLICT_REPLACE
            );

            // If the insert succeeded, the row ID exists.
            if (rowId > 0) {
                // Creates a URI with the note ID pattern and the new row ID appended to it.
                Uri rowUri = ContentUris.withAppendedId(CPUri, rowId);

                // Notifies observers registered against this provider that the data changed.
                getContext().getContentResolver().notifyChange(rowUri, null);
                return rowUri;
            }
            else
            {
                // If the insert didn't succeed, then the rowID is <= 0. Throws an exception.
                throw new SQLException("Failed to insert row into " + uri);
            }
        }
        else
        {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

    }

    @Override
    public boolean onCreate() {
        dbHelper = new MainDatabaseHelper(getContext());

//        //to clear existing table
//        SQLiteDatabase db = dbHelper.getWritableDatabase();
//        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
//        dbHelper.onCreate(db);


        // initialize the content provider URI
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger2.provider");
        uriBuilder.scheme("content");
        CPUri =  uriBuilder.build();

        return true;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        if(CPUri.toString().equals(uri.toString()))
        {

            SQLiteDatabase db = dbHelper.getReadableDatabase();
            SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
            qb.setTables(TABLE_NAME);

            // given query format is not right, need to re-format
            selectionArgs = new String[]{selection};
            selection = "key=?";
            //Log.v("query", selection + " " + selectionArgs[0]);

            Cursor c = qb.query(
                    db,            // The database to query
                    projection,    // The columns to return from the query
                    selection,     // The columns for the where clause
                    selectionArgs, // The values for the where clause
                    null,          // don't group the rows
                    null,          // don't filter by row groups
                    sortOrder        // The sort order
            );

            Log.v("TAG", "Query Output:");
            c.moveToFirst();
            // log the rows returned
            while(!c.isAfterLast())
            {
                String returnKey = c.getString(c.getColumnIndex("key"));
                String returnValue = c.getString(c.getColumnIndex("value"));
                Log.v(TAG, "KEY: " + returnKey + " VALUE: " +returnValue);
                c.moveToNext();
            }

            c.moveToFirst();

            // Tells the Cursor what URI to watch, so it knows when its source data changes
            c.setNotificationUri(getContext().getContentResolver(), uri);
            return c;
        }
        else
        {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    /**
     * Helper class that actually creates and manages the provider's underlying data repository.
     */
    protected static final class MainDatabaseHelper extends SQLiteOpenHelper {

        /*
         * Instantiates an open helper for the provider's SQLite data repository
         * Do not do database creation and upgrade here.
         */
        MainDatabaseHelper(Context context) {
            super(context, DB_NAME, null, 1);
        }

        /*
         * Creates the data repository. This is called when the provider attempts to open the
         * repository and SQLite reports that it doesn't exist.
         */
        @Override
        public void onCreate(SQLiteDatabase db) {

            // Creates the main table
            db.execSQL("CREATE TABLE " + TABLE_NAME + " (_id INTEGER PRIMARY KEY AUTOINCREMENT, key TEXT UNIQUE, value TEXT);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
        {
            //do nothing
            return;
        }
    }
}
