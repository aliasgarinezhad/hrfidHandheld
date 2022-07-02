package com.jeanwest.reader.testClasses;

import android.content.Context;

import com.jeanwest.reader.sharedClassesAndFiles.IBarcodeResult;

/**
 * Created by Administrator on 2018-6-28.
 */

public class Barcode2D {
    IBarcodeResult iBarcodeResult = null;
    Context context;
    public static String barcode = "J64822109801099001";

    public Barcode2D(Context context) {
        this.context = context;
    }

    public void startScan(Context context) {

        if (iBarcodeResult != null) {
            try {
                iBarcodeResult.getBarcode(barcode);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void stopScan(Context context) {

    }

    public void open(Context context, IBarcodeResult iBarcodeResult) {
        this.iBarcodeResult = iBarcodeResult;
    }

    public void close(Context context) {

    }
}