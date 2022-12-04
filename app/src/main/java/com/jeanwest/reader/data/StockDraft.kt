package com.jeanwest.reader.data

data class StockDraft(
    val number: Long,
    val date: String = "",
    val numberOfItems: Int,
    val source: Int = 0,
    val destination: Int = 0,
    val barcodeTable: MutableList<String> = mutableListOf(),
    val epcTable: MutableList<String> = mutableListOf(),
    val specification: String = ""
)
