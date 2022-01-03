package com.jeanwest.reader.checkIn

data class CheckInInputProduct(
    var name : String,
    var KBarCode : String,
    var imageUrl : String,
    var primaryKey : Long,
    var number : Int,
    var productCode : String
)