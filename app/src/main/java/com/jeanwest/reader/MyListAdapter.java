package com.jeanwest.reader;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class MyListAdapter extends ArrayAdapter<String> {

    private final Activity context;
    private final ArrayList<String> title;
    private final ArrayList<String>scanned;
    private final ArrayList<String> all;
    private final ArrayList<String> extra;

    public MyListAdapter(Activity context, ArrayList<String> title, ArrayList<String> scanned, ArrayList<String> all, ArrayList<String> extra) {
        super(context, R.layout.list, title);

        this.context=context;
        this.title = title;
        this.all = all;
        this.extra = extra;
        this.scanned = scanned;

    }

    public View getView(int position,View view,ViewGroup parent) {
        LayoutInflater inflater=context.getLayoutInflater();
        View rowView=inflater.inflate(R.layout.list, null,true);

        TextView titleText = (TextView) rowView.findViewById(R.id.titleView);
        TextView scannedText = (TextView) rowView.findViewById(R.id.scannedView);
        TextView allText = (TextView) rowView.findViewById(R.id.allView);
        TextView extraText = (TextView) rowView.findViewById(R.id.extraView);

        titleText.setText(title.get(position));
        scannedText.setText(scanned.get(position));
        allText.setText(all.get(position));
        extraText.setText(extra.get(position));

        return rowView;
    };
}