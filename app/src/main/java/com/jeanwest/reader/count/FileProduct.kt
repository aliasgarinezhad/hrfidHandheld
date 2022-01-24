package com.jeanwest.reader.count

data class FileProduct(
    var name : String,
    var KBarCode : String,
    var imageUrl : String,
    var primaryKey : Long,
    var number : Int,
    var category : String,
    var productCode : String,
    var size : String,
    var color : String,
    var originalPrice : String,
    var salePrice : String,
    var rfidKey : Long
)