package com.jeanwest.reader;

/**
 * Created by Administrator on 2018-6-28.
 */

public interface IBarcodeResult {
    public void getBarcode(String barcode) throws InterruptedException;
}