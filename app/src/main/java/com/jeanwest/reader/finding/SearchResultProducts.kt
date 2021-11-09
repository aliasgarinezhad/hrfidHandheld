package com.jeanwest.reader.finding

data class SearchResultProducts(
    var name: String,
    var KBarCode: String,
    var imageUrl: String,
    var storeNumber: Int,
    var warehouseNumber: Int,
    var productCode : String,
    var size : String,
    var color : String
)