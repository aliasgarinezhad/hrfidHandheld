package com.jeanwest.reader;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

public class MyListAdapterFind extends ArrayAdapter<String> {

    private final Activity context;
    private final ArrayList<String> title;
    private final ArrayList<String> pictureURL;
    private WebSettings webSettings;

    public MyListAdapterFind(Activity context, ArrayList<String> title, ArrayList<String> pictureURL) {
        super(context, R.layout.list, title);
        // TODO Auto-generated constructor stub

        this.context=context;
        this.title = title;
        this.pictureURL = pictureURL;
    }

    @SuppressLint("DefaultLocale")
    public View getView(int position, View view, ViewGroup parent) {

        LayoutInflater inflater=context.getLayoutInflater();
        View rowView=inflater.inflate(R.layout.list_find, null,true);

        TextView titleText = rowView.findViewById(R.id.titleViewSub);
        WebView picture = rowView.findViewById(R.id.pictureWebView);

        try {

            titleText.setText(title.get(position));
            picture.loadUrl(pictureURL.get(position));
            webSettings = picture.getSettings();
            webSettings.setUseWideViewPort(true);
            webSettings.setLoadWithOverviewMode(true);
            picture.setFocusable(false);
        }
        catch (ArrayIndexOutOfBoundsException e) {
            Toast.makeText(context, String.format("title: %d", title.size()), Toast.LENGTH_LONG).show();
        }
        return rowView;
    };
}