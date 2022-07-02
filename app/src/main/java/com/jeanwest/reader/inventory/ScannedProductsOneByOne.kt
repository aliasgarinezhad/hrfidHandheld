package com.jeanwest.reader.inventory

data class ScannedProductsOneByOne (
    var name: String,
    var KBarCode: String,
    var imageUrl: String,
    var primaryKey: Long,
    var productCode: String,
    var size: String,
    var color: String,
    var originalPrice: String,
    var salePrice: String,
    var rfidKey: Long,
    var warehouseNumber : Int,
    var storeNumber : Int,
    var brandName : String,
)