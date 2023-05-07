package tech.kzen.shell.proxy

//import org.springframework.context.annotation.Bean
//import org.springframework.context.annotation.Configuration
//import org.springframework.web.reactive.function.server.router
//
//
//@Configuration
//class ProxyApi(
//        private val proxyHandler: ProxyHandler
//) {
//    @Bean
//    fun counterRouter() = router {
//        GET("/shell/project", proxyHandler::list)
//        GET("/shell/project/start", proxyHandler::start)
//        GET("/shell/project/stop", proxyHandler::stop)
//
//        GET("/**", proxyHandler::handle)
//        PUT("/**", proxyHandler::handle)
//        POST("/**", proxyHandler::handle)
//    }
//}