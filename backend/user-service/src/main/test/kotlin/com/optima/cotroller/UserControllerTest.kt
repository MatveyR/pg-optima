package com.optima.cotroller

import com.optima.controller.UserController

import com.optima.model.User
import com.optima.service.UserService
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Test for [UserController].
 */
internal class UserControllerTest {

    @AfterEach
    fun tearDown() = clearAllMocks()

    @Test
    fun getAllTest() {
        every { service.getAll() } returns listOf(userDto)

        Assertions.assertThat(controller.all)
            .isNotEmpty
            .hasSize(1)
            .first()
            .isEqualTo(userDto)

        verify(exactly = 1) { service.getAll() }
    }

    @Test
    fun createUserTest() {
        every { service.create(userDto) } returns userDto

        controller.create(userDto).also {
            Assertions.assertThat(it).isEqualTo(userDto)
        }

        verify(exactly = 1) { service.create(userDto) }
    }
}

private val service = mockk<UserService>()
private val controller = UserController(service)

private val userDto = User().apply {
    username = "John Doe"
    password = "1"
}
