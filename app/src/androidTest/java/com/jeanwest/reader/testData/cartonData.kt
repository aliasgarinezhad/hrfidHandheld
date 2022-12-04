package com.jeanwest.reader.testData

import com.jeanwest.reader.data.Carton
import com.jeanwest.reader.data.StockDraft

val carton = Carton(
    number = "CN3114100044694",
    date = "1401/5/30",
    numberOfItems = 4,
    source = 1911,
    specification = "100(1) | 120(1) | 130(2)"
)

var cartonProducts = """
[
{
"BarcodeMain_ID": "114100011333",
"kbarcode": "14022100J-2380-100"
},
{
"BarcodeMain_ID": "114100011335",
"kbarcode": "14022100J-2380-120"
},
{
"BarcodeMain_ID": "114100011336",
"kbarcode": "14022100J-2380-130"
},
{
"BarcodeMain_ID": "114100011336",
"kbarcode": "14022100J-2380-130"
}
]
""".trimIndent()
