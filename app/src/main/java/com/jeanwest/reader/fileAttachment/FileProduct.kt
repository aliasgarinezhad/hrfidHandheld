package com.jeanwest.reader.fileAttachment

data class FileProduct(
    var name : String,
    var KBarCode : String,
    var imageUrl : String,
    var primaryKey : Long,
    var number : Int,
    var category : String,
    var productCode : String
)