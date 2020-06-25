package in.edu.ssn.testssnapp.database;

import android.provider.BaseColumns;


public class SavedPost {

    // src: https://stackoverflow.com/questions/8434819/android-sqlite-auto-increment/30798224#30798224
    public static final String SQL_CREATE_SAVED_POST_ENTRIES =
            "CREATE TABLE IF NOT EXISTS " + SavedPostEntry.TABLE_NAME + " (" +
                    SavedPostEntry.COLUMN_NAME_POST_ID + " TEXT PRIMARY KEY," +
                    SavedPostEntry.COLUMN_NAME_POST + " TEXT,"
                    + SavedPostEntry.COLUMN_NAME_TIME + " TEXT,"
                    + SavedPostEntry.COLUMN_NAME_POST_TYPE + " TEXT" + ")";
    String rowId, post, time;

    public static class SavedPostEntry implements BaseColumns {
        public static final String TABLE_NAME = "SavedPost";
        public static final String COLUMN_NAME_POST_ID = "postid";
        public static final String COLUMN_NAME_POST = "post";
        public static final String COLUMN_NAME_TIME = "time";
        public static final String COLUMN_NAME_POST_TYPE = "posttype";
    }
}
