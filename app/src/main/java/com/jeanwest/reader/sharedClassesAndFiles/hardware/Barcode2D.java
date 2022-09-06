package com.jeanwest.reader.sharedClassesAndFiles.hardware;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.barcode.BarcodeUtility;
import com.jeanwest.reader.sharedClassesAndFiles.IBarcodeResult;

/**
 * Created by Administrator on 2018-6-28.
 */

public class Barcode2D {
    String TAG="Scanner_barcodeTest";
    BarcodeUtility barcodeUtility=null;
    BarcodeDataReceiver barcodeDataReceiver=null;
    IBarcodeResult iBarcodeResult=null;
    Context context;
    public Barcode2D(Context context){
        barcodeUtility= BarcodeUtility.getInstance();
        this.context=context;
    }
    //开始扫码
    public void startScan(Context context){
        if(barcodeUtility!=null) {
            Log.i(TAG,"ScanBarcode");
            barcodeUtility.startScan(context, BarcodeUtility.ModuleType.BARCODE_2D);
        }
    }
    //停止扫描
    public void stopScan(Context context){
        if(barcodeUtility!=null) {
            Log.i(TAG,"stopScan");
            barcodeUtility.stopScan(context, BarcodeUtility.ModuleType.BARCODE_2D);
        }
    }

    //打开
    public void open(Context context, IBarcodeResult iBarcodeResult){
        if(barcodeUtility!=null) {
            this.iBarcodeResult=iBarcodeResult;
            barcodeUtility.setOutputMode(context, 2);//设置广播接收数据
            barcodeUtility.setScanResultBroadcast(context, "com.scanner.broadcast", "data");//设置接收数据的广播
            barcodeUtility.open(context, BarcodeUtility.ModuleType.BARCODE_2D);//打开2D
            barcodeUtility.setReleaseScan(context,false);//设置松开扫描按键，不停止扫描
            barcodeUtility.setScanFailureBroadcast(context,true);//扫描失败也发送广播
            barcodeUtility.enableContinuousScan(context,false);//关闭键盘助手连续扫描
            barcodeUtility.enablePlayFailureSound(context,false);//关闭键盘助手 扫描失败的声音
            barcodeUtility.enablePlaySuccessSound(context,false);//关闭键盘助手 扫描成功的声音
            barcodeUtility.enableEnter(context,false);//关闭回车
            barcodeUtility.setBarcodeEncodingFormat(context,1);

            if(barcodeDataReceiver==null) {
                barcodeDataReceiver=new BarcodeDataReceiver();
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction("com.scanner.broadcast");
                context.registerReceiver(barcodeDataReceiver, intentFilter);
            }
        }
    }
    //关闭
    public void close(Context context){
        if(barcodeUtility!=null) {
            barcodeUtility.close(context, BarcodeUtility.ModuleType.BARCODE_2D);//关闭2D
            if(barcodeDataReceiver!=null) {
                context.unregisterReceiver(barcodeDataReceiver);
                barcodeDataReceiver=null;
            }
        }
    }

    class BarcodeDataReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String barCode = intent.getStringExtra("data");
            String status=intent.getStringExtra("SCAN_STATE");

            if(status!=null && (status.equals("cancel"))){
                return;
            }else{
                if(barCode==null)
                {
                   barCode="Scan fail";
                 } 
                if(iBarcodeResult!=null) {
                    try {
                        iBarcodeResult.getBarcode(barCode);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}






 //if (barCode != null && !barCode.equals("")) {
                    //success
                    //byte[] barcodeBytes = intent.getByteArrayExtra("dataBytes");//获取原始的bytes数据
                    //if(barcodeBytes!=null) {
                    //    byte[] decodeData= Base64.decode(barcodeBytes,Base64.DEFAULT);
                    //    barCode=StringUtility.bytes2HexString(decodeData);
                    //}

                //}
