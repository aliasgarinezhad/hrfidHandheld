package com.jeanwest.reader.refill

data class RefillConflictResultProduct(
    var name: String,
    var KBarCode: String,
    var imageUrl: String,
    var matchedNumber: Int,
    var scannedNumber: Int,
    var result: String,
    var scan: String,
    var productCode : String,
    var size: String,
    var color: String,
    var originalPrice : String,
    var salePrice : String,
    var primaryKey : Long,
    var rfidKey : Long
)