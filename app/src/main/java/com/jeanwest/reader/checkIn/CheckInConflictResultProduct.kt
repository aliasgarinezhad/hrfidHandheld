package com.jeanwest.reader.checkIn

data class CheckInConflictResultProduct(
    var name: String,
    var KBarCode: String,
    var imageUrl: String,
    var matchedNumber: Int,
    var scannedNumber: Int,
    var result: String,
    var scan: String,
    var productCode : String
)