package com.jeanwest.reader

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.jeanwest.reader.refill.RefillActivity
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ExceptionHandler(var context : Context, var default : Thread.UncaughtExceptionHandler) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, exception: Throwable) {

        val sdf = SimpleDateFormat("MM.dd'T'HH:mm", Locale.ENGLISH)
        val logFileName = "log" + sdf.format(Date()) + ".txt"

        val dir = File(context.getExternalFilesDir(null), "/")
        val outFile = File(dir, logFileName)
        val outputStream = FileOutputStream(outFile.absolutePath)
        outputStream.write(exception.stackTraceToString().toByteArray())
        outputStream.flush()
        outputStream.close()

        val memory = PreferenceManager.getDefaultSharedPreferences(context)
        val edit = memory.edit()
        edit.putBoolean("isAppCrashed", true)
        edit.putString("logFileName", logFileName)
        edit.apply()

        default.uncaughtException(thread, exception)
    }
}