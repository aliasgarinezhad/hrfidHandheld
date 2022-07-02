package com.jeanwest.reader.inventory

data class ScannedProduct(
    var name: String,
    var KBarCode: String,
    var imageUrl: String,
    var primaryKey: Long,
    var scannedNumber: Int,
    var productCode: String,
    var size: String,
    var color: String,
    var originalPrice: String,
    var salePrice: String,
    var rfidKey: Long,
    var brandName : String,
)