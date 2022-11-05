package com.jeanwest.reader.write

import org.junit.Test

class AddProductActivityTest {

    @Test
    fun epcGeneratorTest() {
        /*for (i in 0L until 1000000L) {

            val epc = epcGenerator((i % 256).toInt(), (i % 8).toInt(), (i % 8).toInt(), (i % 4096).toInt(), (i % 4294967296), i)
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
        }*/
        println(epcGenerator(48, 0, 0, 101, 130290L, 0L))
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