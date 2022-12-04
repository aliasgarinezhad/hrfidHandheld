package com.jeanwest.reader.data

data class Carton(
    val number: String,
    val date: String = "",
    val numberOfItems: Int,
    val source: Int = 0,
    val barcodeTable: MutableList<String> = mutableListOf(),
    val specification: String = ""
)
