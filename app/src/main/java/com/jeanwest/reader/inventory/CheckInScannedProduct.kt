package com.jeanwest.reader.inventory

data class CheckInScannedProduct(
    var name: String,
    var KBarCode: String,
    var imageUrl: String,
    var primaryKey: Long,
    var productCode: String,
    var size: String,
    var color: String,
    var originalPrice: String,
    var salePrice: String,
    var rfidKey: Long,
    var scannedBarcode: String,
    var scannedEPCs: MutableList<String>,
    var scannedEPCNumber: Int,
    var scannedBarcodeNumber: Int,
) {
    val scannedNumber: Int
        get() = scannedBarcodeNumber + scannedEPCNumber
}