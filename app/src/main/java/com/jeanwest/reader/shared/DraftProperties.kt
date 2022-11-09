package com.jeanwest.reader.shared

data class DraftProperties(
    val number: Long,
    val date: String = "",
    val numberOfItems: Int,
    val source: Int = 0,
    val destination: Int = 0,
    val barcodeTable: MutableList<String> = mutableListOf(),
    val epcTable: MutableList<String> = mutableListOf(),
    val specification: String = ""
)
