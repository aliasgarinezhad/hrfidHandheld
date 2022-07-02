package com.jeanwest.reader.sharedClassesAndFiles

data class Product(
    var KBarCode: String,
    var name: String = "",
    var imageUrl: String = "",
    var kName: String = "",
    var primaryKey: Long = 0,
    var productCode: String = "",
    var size: String = "",
    var color: String = "",
    var originalPrice: String = "",
    var salePrice: String = "",
    var rfidKey: Long = 0,
    var wareHouseNumber: Int = 0,
    var storeNumber: Int = 0,
    var scannedEPCs: MutableList<String> = mutableListOf(),
    var scannedBarcode: String = "",
    var scannedBarcodeNumber: Int = 0,
    var scannedEPCNumber: Int = 0,
    var requestedNum: Int = 0,
    var matchedNumber: Int = 0,
    var result: String = "",
    var scan: String = "",
    var fileNumber: Int = 0,
    var category: String = "",
    var checkInNumber: Long = 0L
) {
    var scannedNumber: Int
        get() = scannedEPCNumber + scannedBarcodeNumber
        set(value) {
            scannedEPCNumber = value
        }
}