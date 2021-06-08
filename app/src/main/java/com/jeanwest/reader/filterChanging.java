package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyEvent;

public class filterChanging extends AppCompatActivity implements IBarcodeResult {

    Barcode2D barcode2D;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_filter_changing);

        barcode2D = new Barcode2D(this);

        open();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == 280 || keyCode == 139) {
            start();
        }
        return true;
    }

    @Override
    public void getBarcode(String barcode) throws InterruptedException {

    }


    public void start() {
        barcode2D.startScan(this);
    }
    public void stop() {
        barcode2D.stopScan(this);
    }
    public void open() {
        barcode2D.open(this,this);
    }
    public void close() {
        barcode2D.stopScan(this);
        barcode2D.close(this);
    }
}