
package com.jeanwest.reader.checkOut

data class CheckOutProduct(
    var name: String,
    var KBarCode: String,
    var imageUrl: String,
    var primaryKey: Long,
    var productCode : String,
    var size: String,
    var color: String,
    var originalPrice : String,
    var salePrice : String,
    var rfidKey : Long,
    var wareHouseNumber : Int,
    var scannedEPCs : MutableList<String>,
    var scannedBarcode : String,
    var kName: String,
    var scannedEPCNumber : Int,
    var scannedBarcodeNumber : Int,
)