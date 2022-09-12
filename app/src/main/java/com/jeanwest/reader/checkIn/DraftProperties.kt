package com.jeanwest.reader.checkIn

data class DraftProperties(
    val number : Long,
    val date : String = "",
    val numberOfItems : Int,
    val source : Int = 0,
    val destination : Int = 0,
    val barcodeTable : MutableList<String> = mutableListOf(),
    val epcTable : MutableList<String> = mutableListOf(),
)
