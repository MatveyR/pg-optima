package com.optima.service

import com.optima.entity.UserEntity
import com.optima.mapper.UserMapper
import com.optima.model.User
import com.optima.repository.UserRepository
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Test for [UserService].
 */
internal class UserServiceTest {

    @Test
    fun getAllTest() {
        every { repository.findAll() } returns listOf(userEntity)
        every { mapper.convertToUser(userEntity) } returns userDto
        Assertions.assertThat(service.getAll())
            .isNotEmpty
            .hasSize(1)
            .first()
            .isEqualTo(userDto)

        verify(exactly = 1) { repository.findAll() }
        verify(exactly = 1) { mapper.convertToUser(userEntity) }
    }

    @Test
    fun createUserTest() {
        every { mapper.convertToUserEntity(userDto) } returns userEntity
        every { repository.save(userEntity) } returns userEntity
        every { mapper.convertToUser(userEntity) } returns userDto

        service.create(userDto).also {
            Assertions.assertThat(it.username).isEqualTo("John Doe")
            Assertions.assertThat(it.password).isEqualTo("1")
        }

        verify(exactly = 1) { mapper.convertToUserEntity(userDto) }
        verify(exactly = 1) { repository.save(userEntity) }
        verify(exactly = 1) { mapper.convertToUser(userEntity) }
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
}

private val repository = mockk<UserRepository>()
private val mapper = mockk<UserMapper>()
private val service = UserService(repository, mapper)

private val userEntity = UserEntity().apply {
    id = 1L
    username = "John Doe"
    password = "1"
}

private val userDto = User().apply {
    username = "John Doe"
    password = "1"
}
