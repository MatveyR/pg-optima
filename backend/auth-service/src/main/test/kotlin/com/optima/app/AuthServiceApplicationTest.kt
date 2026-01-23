package com.optima.app

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Spring Boot test for application context loading.
 */
@SpringBootTest
@ActiveProfiles("test")
internal class AuthServiceApplicationTest {

    @Test
    fun contextLoads() = Unit

    @Test
    fun mainMethodStartsApplication() {
        AuthServiceApplication.main(arrayOf())
    }
}
