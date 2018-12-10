package tech.kzen.shell.util

import java.net.HttpURLConnection
import java.net.URL


object ProcessAwaitUtil {
    fun isAvailable(portNumber: Int): Boolean {
        val rootUrl = URL("http://localhost:$portNumber/")

        return try {
            val huc = rootUrl.openConnection() as HttpURLConnection
            huc.requestMethod = "GET"
            huc.connect()
            val code = huc.responseCode

            code == 200
        }
        catch (e: Exception) {
            false
        }
    }


    fun waitUntilAvailable(portNumber: Int) {
        while (true) {
            if (isAvailable(portNumber)) {
                break
            }
            Thread.sleep(250)
        }
    }
}