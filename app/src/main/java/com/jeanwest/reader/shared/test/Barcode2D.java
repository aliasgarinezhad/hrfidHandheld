package com.jeanwest.reader.shared.test;

import android.content.Context;

import com.jeanwest.reader.shared.IBarcodeResult;

/**
 * Created by Administrator on 2018-6-28.
 */

public class Barcode2D {
    IBarcodeResult iBarcodeResult = null;
    Context context;
    public static String barcode = "";
    public static Boolean on = false;

    public Barcode2D(Context context) {
        this.context = context;
    }

    public void startScan(Context context) {

        assert on;
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
        on = true;
        this.iBarcodeResult = iBarcodeResult;
    }

    public void close(Context context) {
        on = false;
    }
}
