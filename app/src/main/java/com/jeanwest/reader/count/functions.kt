package com.jeanwest.reader.count

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.jeanwest.reader.MainActivity
import com.jeanwest.reader.R
import com.jeanwest.reader.testClasses.RFIDWithUHFUART
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

fun getConflicts(
    fileProducts: MutableList<FileProduct>,
    scannedProducts: MutableMap<String, ScannedProduct>,
): SnapshotStateMap<String, ConflictResultProduct> {

    val result = mutableStateMapOf<String, ConflictResultProduct>()

    fileProducts.forEach { fileProduct ->

        if (fileProduct.KBarCode in scannedProducts) {

            val resultData = ConflictResultProduct(
                name = fileProduct.name,
                KBarCode = fileProduct.KBarCode,
                imageUrl = fileProduct.imageUrl,
                category = fileProduct.category,
                matchedNumber = abs(scannedProducts[fileProduct.KBarCode]!!.scannedNumber - fileProduct.number),
                scannedNumber = scannedProducts[fileProduct.KBarCode]!!.scannedNumber,
                fileNumber = fileProduct.number,
                result =
                when {
                    scannedProducts[fileProduct.KBarCode]!!.scannedNumber > fileProduct.number -> {
                        "اضافی"
                    }
                    scannedProducts[fileProduct.KBarCode]!!.scannedNumber < fileProduct.number -> {
                        "کسری"
                    }
                    else -> {
                        "تایید شده"
                    }
                },
                scan = when {
                    scannedProducts[fileProduct.KBarCode]!!.scannedNumber > fileProduct.number -> {
                        "اضافی فایل"
                    }
                    scannedProducts[fileProduct.KBarCode]!!.scannedNumber < fileProduct.number -> {
                        "کسری"
                    }
                    else -> {
                        "تایید شده"
                    }
                },
                productCode = fileProduct.productCode,
                size = fileProduct.size,
                color = fileProduct.color,
                originalPrice = fileProduct.originalPrice,
                salePrice = fileProduct.salePrice,
                rfidKey = fileProduct.rfidKey,
                primaryKey = fileProduct.primaryKey
            )
            result[fileProduct.KBarCode] = resultData

        } else {
            val resultData = ConflictResultProduct(
                name = fileProduct.name,
                KBarCode = fileProduct.KBarCode,
                imageUrl = fileProduct.imageUrl,
                category = fileProduct.category,
                matchedNumber = fileProduct.number,
                scannedNumber = 0,
                result = "کسری",
                scan = "کسری",
                productCode = fileProduct.productCode,
                size = fileProduct.size,
                color = fileProduct.color,
                originalPrice = fileProduct.originalPrice,
                salePrice = fileProduct.salePrice,
                rfidKey = fileProduct.rfidKey,
                primaryKey = fileProduct.primaryKey,
                fileNumber = fileProduct.number
            )
            result[fileProduct.KBarCode] = (resultData)
        }
    }

    scannedProducts.forEach {
        if (it.key !in result.keys) {
            val resultData = ConflictResultProduct(
                name = it.value.name,
                KBarCode = it.value.KBarCode,
                imageUrl = it.value.imageUrl,
                category = "نامعلوم",
                matchedNumber = it.value.scannedNumber,
                scannedNumber = it.value.scannedNumber,
                result = "اضافی",
                scan = "اضافی",
                productCode = it.value.productCode,
                size = it.value.size,
                color = it.value.color,
                originalPrice = it.value.originalPrice,
                salePrice = it.value.salePrice,
                rfidKey = it.value.rfidKey,
                primaryKey = it.value.primaryKey,
                fileNumber = 0
            )
            result[it.key] = (resultData)
        }
    }

    return result
}

fun checkLogin(context: Context): Boolean {

    val memory = PreferenceManager.getDefaultSharedPreferences(context)

    return if (memory.getString("username", "") != "") {

        MainActivity.username = memory.getString("username", "")!!
        MainActivity.token = memory.getString("accessToken", "")!!
        true
    } else {
        Toast.makeText(
            context,
            "لطفا ابتدا به حساب کاربری خود وارد شوید",
            Toast.LENGTH_LONG
        ).show()
        false
    }
}

fun exportFile(
    conflictResultProducts: MutableMap<String, ConflictResultProduct>,
    shortagesNumber: Int,
    additionalNumber: Int,
    number: Int,
    signedProductCodes: MutableList<String>,
    fileName: String,
    context: Context
) {

    val workbook = XSSFWorkbook()
    val sheet = workbook.createSheet("conflicts")

    val headerRow = sheet.createRow(sheet.physicalNumberOfRows)
    headerRow.createCell(0).setCellValue("کد جست و جو")
    headerRow.createCell(1).setCellValue("تعداد")
    headerRow.createCell(2).setCellValue("دسته")
    headerRow.createCell(3).setCellValue("کسری")
    headerRow.createCell(4).setCellValue("اضافی")
    headerRow.createCell(5).setCellValue("نشانه")

    conflictResultProducts.values.forEach {
        val row = sheet.createRow(sheet.physicalNumberOfRows)
        row.createCell(0).setCellValue(it.KBarCode)
        row.createCell(1).setCellValue(it.scannedNumber.toDouble())
        row.createCell(2).setCellValue(it.category)

        if (it.scan == "کسری") {
            row.createCell(3).setCellValue(it.matchedNumber.toDouble())
        } else if (it.scan == "اضافی" || it.scan == "اضافی فایل") {
            row.createCell(4).setCellValue(it.matchedNumber.toDouble())
        }

        if (it.KBarCode in signedProductCodes) {
            row.createCell(5).setCellValue("نشانه دار")
        }
    }

    val row = sheet.createRow(sheet.physicalNumberOfRows)
    row.createCell(0).setCellValue("مجموع")
    row.createCell(1).setCellValue(number.toDouble())
    row.createCell(3).setCellValue(shortagesNumber.toDouble())
    row.createCell(4).setCellValue(additionalNumber.toDouble())

    val sheet2 = workbook.createSheet("کسری")

    val header2Row = sheet2.createRow(sheet2.physicalNumberOfRows)
    header2Row.createCell(0).setCellValue("کد جست و جو")
    header2Row.createCell(1).setCellValue("موجودی")
    header2Row.createCell(2).setCellValue("دسته")
    header2Row.createCell(3).setCellValue("کسری")
    header2Row.createCell(4).setCellValue("نشانه")

    conflictResultProducts.values.forEach {

        if (it.scan == "کسری") {
            val shortageRow = sheet2.createRow(sheet2.physicalNumberOfRows)
            shortageRow.createCell(0).setCellValue(it.KBarCode)
            shortageRow.createCell(1)
                .setCellValue(it.scannedNumber.toDouble() + it.matchedNumber.toDouble())
            shortageRow.createCell(2).setCellValue(it.category)
            shortageRow.createCell(3).setCellValue(it.matchedNumber.toDouble())

            if (it.KBarCode in signedProductCodes) {
                shortageRow.createCell(4).setCellValue("نشانه دار")
            }
        }
    }

    val sheet3 = workbook.createSheet("اضافی")

    val header3Row = sheet3.createRow(sheet3.physicalNumberOfRows)
    header3Row.createCell(0).setCellValue("کد جست و جو")
    header3Row.createCell(1).setCellValue("موجودی")
    header3Row.createCell(2).setCellValue("دسته")
    header3Row.createCell(3).setCellValue("اضافی")
    header3Row.createCell(4).setCellValue("نشانه")

    conflictResultProducts.values.forEach {

        if (it.scan == "اضافی") {
            val additionalRow = sheet3.createRow(sheet3.physicalNumberOfRows)
            additionalRow.createCell(0).setCellValue(it.KBarCode)
            additionalRow.createCell(1)
                .setCellValue(it.matchedNumber - it.scannedNumber.toDouble())
            additionalRow.createCell(2).setCellValue(it.category)
            additionalRow.createCell(3).setCellValue(it.matchedNumber.toDouble())

            if (it.KBarCode in signedProductCodes) {
                additionalRow.createCell(4).setCellValue("نشانه دار")
            }
        }
    }

    val dir = File(context.getExternalFilesDir(null), "/")

    val outFile = File(dir, "$fileName.xlsx")

    val outputStream = FileOutputStream(outFile.absolutePath)
    workbook.write(outputStream)
    outputStream.flush()
    outputStream.close()

    val uri = FileProvider.getUriForFile(
        context,
        context.applicationContext.packageName + ".provider",
        outFile
    )
    val shareIntent = Intent(Intent.ACTION_SEND)
    shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
    shareIntent.type = "application/octet-stream"
    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(shareIntent)
}
