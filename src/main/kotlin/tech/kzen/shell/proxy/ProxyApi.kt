package tech.kzen.shell.proxy

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.router


@Configuration
class ProxyApi(
        private val proxyHandler: ProxyHandler
) {
    @Bean
    fun counterRouter() = router {
        GET("/**", proxyHandler::handle)
    }
}