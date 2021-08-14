package com.jeanwest.reader.finding

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

class MyListAdapterFind(
    private val context: Activity,
    private val title: ArrayList<String>,
    private val pictureURL: ArrayList<String>
) : ArrayAdapter<String>(
    context, R.layout.list, title
) {
    private lateinit var webSettings: WebSettings

    @SuppressLint("DefaultLocale")
    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        val inflater = context.layoutInflater
        val rowView = inflater.inflate(R.layout.list_find, null, true)
        val titleText = rowView.findViewById<TextView>(R.id.titleViewSub)
        val picture = rowView.findViewById<WebView>(R.id.pictureWebView)
        try {
            titleText.text = title[position]
            picture.loadUrl(pictureURL[position])
            webSettings = picture.settings
            webSettings.useWideViewPort = true
            webSettings.loadWithOverviewMode = true
            picture.isFocusable = false
        } catch (e: ArrayIndexOutOfBoundsException) {
            Toast.makeText(context, String.format("title: %d", title.size), Toast.LENGTH_LONG)
                .show()
        }
        return rowView
    }
}