package com.jeanwest.reader.warehouseScanning

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.jeanwest.reader.R
import java.util.*

class MyListAdapter(
    private val context: Activity,
    private val title: ArrayList<String>,
    private val scanned: ArrayList<String>,
    private val all: ArrayList<String>,
    private val extra: ArrayList<String>
) : ArrayAdapter<String>(
    context, R.layout.list, title
) {
    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        val inflater = context.layoutInflater
        val rowView = inflater.inflate(R.layout.list, null, true)
        val titleText = rowView.findViewById<View>(R.id.titleView) as TextView
        val scannedText = rowView.findViewById<View>(R.id.scannedView) as TextView
        val allText = rowView.findViewById<View>(R.id.allView) as TextView
        val extraText = rowView.findViewById<View>(R.id.extraView) as TextView
        titleText.text = title[position]
        scannedText.text = scanned[position]
        allText.text = all[position]
        extraText.text = extra[position]
        return rowView
    }
}