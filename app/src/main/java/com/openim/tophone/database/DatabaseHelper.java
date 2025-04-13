package com.openim.tophone.database;
import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "tophone.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_NAME = "userinfo";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_VALUE = "value";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // 只获取第一条数据的方法
    @SuppressLint("Range")
    public String getValueByName(String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {COLUMN_VALUE};
        String selection = COLUMN_NAME + " =?";
        String[] selectionArgs = {name};

        Cursor cursor = db.query(TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null);

        String value = null;
        if (cursor != null && cursor.moveToFirst()) {
            value = cursor.getString(cursor.getColumnIndex(COLUMN_VALUE));
        }

        if (cursor != null) {
            cursor.close();
        }
        db.close();
        return value;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTableQuery = "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_NAME + " TEXT, " +
                COLUMN_VALUE + " TEXT)";
        db.execSQL(createTableQuery);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    // 插入数据的方法
    public void insertData(String name, String value) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("INSERT INTO " + TABLE_NAME + " (" + COLUMN_NAME + ", " + COLUMN_VALUE + ") VALUES ('" + name + "', '" + value + "')");
        db.close();
    }

    // 读取所有数据的方法
    public List<DataItem> getAllData() {
        List<DataItem> dataList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME,
                new String[]{COLUMN_ID, COLUMN_NAME, COLUMN_VALUE},
                null,
                null,
                null,
                null,
                null);

        if (cursor != null && cursor.moveToFirst()) {
            do {
                @SuppressLint("Range") int id = cursor.getInt(cursor.getColumnIndex(COLUMN_ID));
                @SuppressLint("Range") String nameValue = cursor.getString(cursor.getColumnIndex(COLUMN_NAME));
                @SuppressLint("Range") String value = cursor.getString(cursor.getColumnIndex(COLUMN_VALUE));
                DataItem item = new DataItem(id, nameValue, value);
                dataList.add(item);
            } while (cursor.moveToNext());
            cursor.close();
        }

        db.close();
        return dataList;
    }

    // 定义一个数据类来存储表中的每一行数据
    public static class DataItem {
        private int id;
        private String name;
        private String value;

        public DataItem(int id, String name, String value) {
            this.id = id;
            this.name = name;
            this.value = value;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }
}