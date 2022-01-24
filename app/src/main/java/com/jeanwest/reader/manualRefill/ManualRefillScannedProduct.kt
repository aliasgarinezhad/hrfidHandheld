package com.jeanwest.reader.manualRefill

data class ManualRefillScannedProduct(
    var name: String,
    var imageUrl: String,
    var scannedNumber: Int,
    var productCode : String,
    var color: String,
    var originalPrice : String,
    var salePrice : String,
    var shoppingNumber: Int,
    var warehouseNumber: Int,
    var refillNumber: Int
)