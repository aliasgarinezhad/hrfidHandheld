package com.jeanwest.reader.testClasses;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.barcode.BarcodeUtility;
import com.jeanwest.reader.IBarcodeResult;

/**
 * Created by Administrator on 2018-6-28.
 */

public class Barcode2D {
    IBarcodeResult iBarcodeResult=null;
    Context context;
    public Barcode2D(Context context){
        this.context=context;
    }
    //开始扫码
    public void startScan(Context context){

        String barcode = "J64822109801099001";

        if(iBarcodeResult!=null) {
            try {
                iBarcodeResult.getBarcode(barcode);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    //停止扫描
    public void stopScan(Context context){

    }

    //打开
    public void open(Context context, IBarcodeResult iBarcodeResult){
        this.iBarcodeResult=iBarcodeResult;
    }
    //关闭
    public void close(Context context){

    }
}