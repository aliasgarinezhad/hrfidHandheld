package com.jeanwest.reader;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

public class databaseHelperClass2 extends SQLiteOpenHelper {

    public databaseHelperClass2(@Nullable Context context) {
        super(context, "counterDatabase", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("create table counterDatabase (value long, max long, min long, header int, filter int, partition int, company int, power int, password text, step int, counterModified long);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
