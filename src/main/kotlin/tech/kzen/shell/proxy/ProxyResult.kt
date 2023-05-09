package tech.kzen.shell.proxy

import java.io.InputStream


data class ProxyResult(
    val data: InputStream? = null,
    val mimeType: String? = null,
    val header: Map<String, String> = mapOf(),
    val statusCode: Int? = null
)