package com.jeanwest.reader.write

data class WriteRecord(
    val epc: String,
    val barcode: String,
    val dateAndTime: String,
    val username: String,
    val deviceSerialNumber: String,
    val wroteOnRawTag : Boolean
)
