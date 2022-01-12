package com.jeanwest.reader.refill

data class RefillInputProduct(
    var name : String,
    var KBarCode : String,
    var imageUrl : String,
    var number : Int,
    var productCode : String,
    var size: String,
    var color: String,
    var originalPrice : String,
    var salePrice : String,
    var primaryKey : Long,
    var rfidKey : Long
)