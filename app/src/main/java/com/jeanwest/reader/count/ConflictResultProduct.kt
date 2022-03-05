package com.jeanwest.reader.count

data class ConflictResultProduct(
    var name: String,
    var KBarCode: String,
    var imageUrl: String,
    var matchedNumber: Int,
    var scannedNumber: Int,
    var category: String,
    var result: String,
    var scan: String,
    var productCode : String,
    var size : String,
    var color : String,
    var originalPrice : String,
    var salePrice : String,
    var primaryKey : Long,
    var rfidKey : Long,
    var fileNumber : Int
)