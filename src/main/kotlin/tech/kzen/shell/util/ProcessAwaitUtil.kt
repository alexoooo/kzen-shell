package tech.kzen.shell.util

import java.net.HttpURLConnection
import java.net.URI


object ProcessAwaitUtil {
    fun isAvailable(portNumber: Int): Boolean {
        val rootUrl = URI("http://localhost:$portNumber/").toURL()

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