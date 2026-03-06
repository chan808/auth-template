package io.github.chan808.authtemplate

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

// Docker 기반 통합 테스트: 실제 MySQL + Redis 컨테이너로 애플리케이션 컨텍스트 전체 로딩 검증
@SpringBootTest
@Testcontainers
class AuthTemplateApplicationTests {

    companion object {
        @Container
        @JvmField
        val mysql = MySQLContainer("mysql:8.0")
            .withDatabaseName("auth_template_test")
            .withUsername("test")
            .withPassword("test")

        @Container
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:8-alpine")
            .withExposedPorts(6379)

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mysql.jdbcUrl }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379).toString() }
            registry.add("spring.data.redis.password") { "" }
            // contextLoads 목적은 빈 생성 검증 — 스키마는 create-drop으로 자동 생성
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }
            registry.add("spring.flyway.enabled") { "false" }
        }
    }

    @Test
    fun contextLoads() {}
}
