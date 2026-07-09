package tech.kzen.shell.util

import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration


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


    // Poll until the child serves HTTP 200, and return true; return false if the child process dies
    //  first or the timeout elapses (so the caller can transition the project to FAILED instead of
    //  hanging forever, as the old unbounded wait did).
    fun awaitAvailable(portNumber: Int, process: Process, timeout: Duration): Boolean {
        val deadlineNanos = System.nanoTime() + timeout.toNanos()

        while (true) {
            if (isAvailable(portNumber)) {
                return true
            }
            if (! process.isAlive) {
                return false
            }
            if (System.nanoTime() >= deadlineNanos) {
                return false
            }
            Thread.sleep(250)
        }
    }
}