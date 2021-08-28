package com.jeanwest.reader.add

import org.junit.Test

class AddProductActivityTest {

    @Test
    fun epcGeneratorTest() {
        for (i in 0L until 1000000L) {

            val epc = epcGeneratorBug((i % 256).toInt(), (i % 8).toInt(), (i % 8).toInt(), (i % 4096).toInt(), (i % 4294967296), i)
            val decodedEPC = epcDecoder(epc)
            assert(decodedEPC.header == (i % 256).toInt())
            assert(decodedEPC.filter == (i % 8).toInt())
            assert(decodedEPC.partition == (i % 8).toInt())
            assert(decodedEPC.company == (i % 4096).toInt())
            assert(decodedEPC.item == (i % 4294967296))
            if(decodedEPC.serial != i) {
                println("number: $i")
                println("generated: ${decodedEPC.serial}")
            }
            assert(decodedEPC.serial == i)
        }
    }

    private fun epcGenerator(header: Int, filter: Int, partition: Int, company: Int, item: Long, serial: Long) : String {

        val headerStr = String.format("%8s", header.toString(2)).replace(" ".toRegex(), "0")
        val positionStr = String.format("%3s", partition.toString(2)).replace(" ".toRegex(), "0")
        val filterStr = String.format("%3s", filter.toString(2)).replace(" ".toRegex(), "0")
        val companynumberStr = String.format("%12s", company.toString(2)).replace(" ".toRegex(), "0")
        val itemNumberStr = String.format("%32s", item.toString(2)).replace(" ".toRegex(), "0")
        val serialNumberStr = String.format("%38s", serial.toString(2)).replace(" ".toRegex(), "0")
        val EPCStr = headerStr + positionStr + filterStr + companynumberStr + itemNumberStr + serialNumberStr // binary string of EPC (96 bit)

        var tempStr = EPCStr.substring(0, 64).toULong(2).toString(16)
        val epc0To64 = String.format("%16s", tempStr).replace(" ".toRegex(), "0")
        tempStr = EPCStr.substring(64, 96).toULong(2).toString(16)
        val epc64To96 = String.format("%8s", tempStr).replace(" ".toRegex(), "0")

        return epc0To64 + epc64To96
    }

    private fun epcGeneratorBug(header: Int, filter: Int, partition: Int, company: Int, item: Long, serial: Long): String {

        var tempStr: String = java.lang.Long.toBinaryString(header.toLong())
        val headerStr = String.format("%8s", tempStr).replace(" ".toRegex(), "0")
        tempStr = java.lang.Long.toBinaryString(filter.toLong())
        val filterStr = String.format("%3s", tempStr).replace(" ".toRegex(), "0")
        tempStr = java.lang.Long.toBinaryString(partition.toLong())
        val positionStr = String.format("%3s", tempStr).replace(" ".toRegex(), "0")
        tempStr = java.lang.Long.toBinaryString(company.toLong())
        val companynumberStr = String.format("%12s", tempStr).replace(" ".toRegex(), "0")
        tempStr = java.lang.Long.toBinaryString(item)
        val itemNumberStr = String.format("%32s", tempStr).replace(" ".toRegex(), "0")
        tempStr = java.lang.Long.toBinaryString(serial)
        val serialNumberStr = String.format("%38s", tempStr).replace(" ".toRegex(), "0")

        val EPCStr = headerStr + positionStr + filterStr + companynumberStr + itemNumberStr + serialNumberStr // binary string of EPC (96 bit)

        var tempByte = EPCStr.substring(0, 8).toInt(2)
        tempStr = Integer.toString(tempByte, 16)
        val EPC0 = String.format("%2s", tempStr).replace(" ".toRegex(), "0")
        tempByte = EPCStr.substring(8, 16).toInt(2)
        tempStr = Integer.toString(tempByte, 16)
        val EPC1 = String.format("%2s", tempStr).replace(" ".toRegex(), "0")
        tempByte = EPCStr.substring(16, 24).toInt(2)
        tempStr = Integer.toString(tempByte, 16)
        val EPC2 = String.format("%2s", tempStr).replace(" ".toRegex(), "0")
        tempByte = EPCStr.substring(24, 32).toInt(2)
        tempStr = Integer.toString(tempByte, 16)
        val EPC3 = String.format("%2s", tempStr).replace(" ".toRegex(), "0")
        tempByte = EPCStr.substring(32, 40).toInt(2)
        tempStr = Integer.toString(tempByte, 16)
        val EPC4 = String.format("%2s", tempStr).replace(" ".toRegex(), "0")
        tempByte = EPCStr.substring(40, 48).toInt(2)
        tempStr = Integer.toString(tempByte, 16)
        val EPC5 = String.format("%2s", tempStr).replace(" ".toRegex(), "0")
        tempByte = EPCStr.substring(48, 56).toInt(2)
        tempStr = Integer.toString(tempByte, 16)
        val EPC6 = String.format("%2s", tempStr).replace(" ".toRegex(), "0")
        tempByte = EPCStr.substring(56, 64).toInt(2)
        tempStr = Integer.toString(tempByte, 16)
        val EPC7 = String.format("%2s", tempStr).replace(" ".toRegex(), "0")
        tempByte = EPCStr.substring(64, 72).toInt(2)
        tempStr = Integer.toString(tempByte, 16)
        val EPC8 = String.format("%2s", tempStr).replace(" ".toRegex(), "0")
        tempByte = EPCStr.substring(72, 80).toInt()
        tempStr = Integer.toString(tempByte, 16)
        val EPC9 = String.format("%2s", tempStr).replace(" ".toRegex(), "0")
        tempByte = EPCStr.substring(80, 88).toInt(2)
        tempStr = Integer.toString(tempByte, 16)
        val EPC10 = String.format("%2s", tempStr).replace(" ".toRegex(), "0")
        tempByte = EPCStr.substring(88, 96).toInt(2)
        tempStr = Integer.toString(tempByte, 16)
        val EPC11 = String.format("%2s", tempStr).replace(" ".toRegex(), "0")
        return EPC0 + EPC1 + EPC2 + EPC3 + EPC4 + EPC5 + EPC6 + EPC7 + EPC8 + EPC9 + EPC10 + EPC11
    }
    private fun epcDecoder(epc: String) : EPC {

        val binaryEPC =
            String.format("%64s", epc.substring(0, 16).toULong(16).toString(2)).replace(" ".toRegex(), "0") +
                    String.format("%32s", epc.substring(16, 24).toULong(16).toString(2)).replace(" ".toRegex(), "0")
        val result = EPC(0, 0, 0, 0, 0L, 0L)
        result.header = binaryEPC.substring(0, 8).toInt(2)
        result.partition = binaryEPC.substring(8, 11).toInt(2)
        result.filter = binaryEPC.substring(11, 14).toInt(2)
        result.company = binaryEPC.substring(14, 26).toInt(2)
        result.item = binaryEPC.substring(26, 58).toLong(2)
        result.serial = binaryEPC.substring(58, 96).toLong(2)
        return result
    }
    data class EPC(var header: Int, var filter: Int, var partition: Int, var company: Int, var item: Long, var serial: Long)
}