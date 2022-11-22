package com.jeanwest.reader.data

import kotlin.math.abs

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
    var countedWarehouseNumber: Int = 0,
    var storeNumber: Int = 0,
    var countedStoreNumber: Int = 0,
    var brandName: String = "",
    var scannedEPCs: MutableList<String> = mutableListOf(),
    var scannedBarcode: String = "",
    var scannedBarcodeNumber: Int = 0,
    var requestedNumber: Int = 0,
    var draftNumber: Int = 0,
    var inventoryOnDepo: Boolean = true
) {
    val scannedNumber: Int
        get() = scannedEPCNumber + scannedBarcodeNumber

    val scannedEPCNumber: Int
        get() = scannedEPCs.size

    val conflictNumber: Int
        get() = abs(scannedNumber - draftNumber)

    val conflictType: String
        get() {
            return when {
                scannedNumber > draftNumber -> {
                    "اضافی"
                }
                scannedNumber < draftNumber -> {
                    "کسری"
                }
                else -> {
                    "تایید شده"
                }

            }
        }

    val inventoryNumber: Int
        get() = if (inventoryOnDepo) wareHouseNumber else storeNumber

    val inventoryCountedNumber: Int
        get() = if (inventoryOnDepo) countedWarehouseNumber else countedStoreNumber

    val inventoryConflictAbs: Int
        get() = abs(inventoryConflictNumber)

    val inventoryConflictNumber: Int
        get() {

            val currentConflictNumber = scannedNumber - inventoryNumber

            return if (inventoryCountedNumber == 0) {
                inventoryCountedNumber
            } else if (inventoryCountedNumber > 0) {
                if (currentConflictNumber == 0 || (currentConflictNumber > inventoryCountedNumber)) {
                    currentConflictNumber
                } else {
                    inventoryCountedNumber
                }
            } else {
                if (currentConflictNumber > inventoryCountedNumber) {
                    currentConflictNumber
                } else {
                    inventoryCountedNumber
                }
            }
        }

    val inventoryConflictType: String
        get() {
            return when {
                inventoryConflictNumber > 0 -> {
                    "اضافی"
                }
                inventoryConflictNumber < 0 -> {
                    "کسری"
                }
                else -> {
                    "تایید شده"
                }
            }
        }
}
