package com.jeanwest.reader.fileAttachment

data class ScannedProduct(
    var name: String,
    var KBarCode: String,
    var imageUrl: String,
    var primaryKey: Long,
    var scannedNumber: Int,
    var productCode : String
)