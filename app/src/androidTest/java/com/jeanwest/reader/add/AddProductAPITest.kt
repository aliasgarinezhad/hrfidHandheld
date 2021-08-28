package com.jeanwest.reader.add

import android.util.Log
import org.junit.Test

class AddProductAPITest {

    @Test
    fun apiTest() {

        val api = AddProductAPI()
        api.barcode = "J64822109801099001"
        api.start()
        while (api.run){}
        Log.e("response", api.response)
    }
}