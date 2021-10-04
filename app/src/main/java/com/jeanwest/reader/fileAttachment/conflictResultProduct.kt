package com.jeanwest.reader.fileAttachment

data class conflictResultProduct(
    var name: String,
    var KBarCode: String,
    var imageUrl: String,
    var matchedNumber: Int,

    var scannedNumber: Int,
    var category: String,
    var result: String,
    var scan: String,
)