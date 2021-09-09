package com.jeanwest.reader.transfer

import android.annotation.SuppressLint
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import com.jeanwest.reader.R
import java.util.*

class MyListAdapterTransfer(
    private val context: Activity,
    private val title: ArrayList<String>,
    private val spec: ArrayList<String>,
    private val scanned: ArrayList<String>,
    private val all: ArrayList<String>,
    private val notScanned: ArrayList<String>,
    private val pictureURL: ArrayList<String>
) : ArrayAdapter<String>(
    context, R.layout.list_sub, title
) {
    private lateinit var webSettings: WebSettings
    @SuppressLint("DefaultLocale")
    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        val inflater = context.layoutInflater
        val rowView = inflater.inflate(R.layout.list_sub, null, true)
        val titleText = rowView.findViewById<TextView>(R.id.titleViewSub)
        val specText = rowView.findViewById<TextView>(R.id.specViewSub)
        val scannedText = rowView.findViewById<TextView>(R.id.scannedViewSub)
        val allText = rowView.findViewById<TextView>(R.id.notScannedViewSub)
        val notScannedText = rowView.findViewById<TextView>(R.id.extraViewSub)
        val picture = rowView.findViewById<WebView>(R.id.pictureWebView)
        try {
            titleText.text = title[position]
            specText.text = spec[position]
            scannedText.text = scanned[position]
            allText.text = all[position]
            notScannedText.text = notScanned[position]
            picture.loadUrl(pictureURL[position])
            webSettings = picture.settings
            webSettings.useWideViewPort = true
            webSettings.loadWithOverviewMode = true
            picture.isFocusable = false

            if (pictureURL[position] == " ") {
                picture.visibility = View.INVISIBLE
            }

        } catch (e: ArrayIndexOutOfBoundsException) {
            Toast.makeText(
                context,
                String.format(
                    "title: %d spec: %d scanned: %d all: %d notScanned: %d",
                    title.size,
                    spec.size,
                    scanned.size,
                    all.size,
                    notScanned.size
                ),
                Toast.LENGTH_LONG
            ).show()
        }
        return rowView
    }
}