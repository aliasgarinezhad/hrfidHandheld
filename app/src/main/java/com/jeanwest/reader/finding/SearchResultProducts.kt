package com.jeanwest.reader.finding

data class SearchResultProducts(
    var name: String,
    var kbarcode: String,
    var imageUrl: String,
    var shoppingNumber: Int,
    var warehouseNumber: Int,
    var productCode: String,
    var size: String,
    var color: String,
    var originalPrice : String,
    var salePrice : String,
    var primaryKey : Long,
    var rfidKey : Long
)
