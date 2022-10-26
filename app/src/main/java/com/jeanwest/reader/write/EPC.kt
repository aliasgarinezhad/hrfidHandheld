package com.jeanwest.reader.write

data class  EPC(
var header: Int,
var filter: Int,
var partition: Int,
var company: Int,
var item: Long,
var serial: Long
)

