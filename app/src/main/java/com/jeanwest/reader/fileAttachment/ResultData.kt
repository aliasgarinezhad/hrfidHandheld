package com.jeanwest.reader.fileAttachment

data class ResultData(
    var name: String,
    var KBarCode: String,
    var imageUrl: String,
    var matchedNumber: Int,
    var category: String,
    var result: String,
    var scan: String,
)