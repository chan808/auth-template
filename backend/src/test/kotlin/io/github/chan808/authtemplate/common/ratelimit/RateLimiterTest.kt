package io.github.chan808.authtemplate.common.ratelimit

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DataRedisTest
@Import(RateLimiter::class)
@Testcontainers
class RateLimiterTest {

    companion object {
        @Container
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:8-alpine")
            .withExposedPorts(6379)

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379).toString() }
            registry.add("spring.data.redis.password") { "" }
        }
    }

    @Autowired lateinit var rateLimiter: RateLimiter
    @Autowired lateinit var redisTemplate: StringRedisTemplate

    @AfterEach
    fun cleanup() {
        redisTemplate.connectionFactory?.connection?.flushAll()
    }

    @Test
    fun `한도 미달 요청은 null을 반환한다`() {
        val result = rateLimiter.retryAfterIfExceeded(
            key = "RATE:TEST:user1",
            ttlSeconds = 60,
            limit = 5,
        )
        assertNull(result, "한도(5) 이하 요청은 null을 반환해야 함")
    }

    @Test
    fun `한도를 초과하면 Retry-After 초(양수)를 반환한다`() {
        val key = "RATE:TEST:user2"

        // limit=2이므로 3번째 요청부터 초과
        repeat(2) { rateLimiter.retryAfterIfExceeded(key, ttlSeconds = 60, limit = 2) }
        val retryAfter = rateLimiter.retryAfterIfExceeded(key, ttlSeconds = 60, limit = 2)

        assertNotNull(retryAfter, "한도 초과 시 retryAfterSeconds를 반환해야 함")
        assertTrue(retryAfter > 0, "Retry-After는 양수여야 함")
    }

    @Test
    fun `요청 횟수가 정확히 누적된다`() {
        val key = "RATE:TEST:user3"

        // 5번 요청 중 limit=5 이므로 5번째까지 null, 6번째부터 초과
        repeat(5) {
            assertNull(
                rateLimiter.retryAfterIfExceeded(key, ttlSeconds = 60, limit = 5),
                "${it + 1}번째 요청은 한도 이하여야 함",
            )
        }
        assertNotNull(
            rateLimiter.retryAfterIfExceeded(key, ttlSeconds = 60, limit = 5),
            "6번째 요청은 한도 초과여야 함",
        )
    }

    @Test
    fun `첫 번째 요청 시 TTL이 자동으로 설정된다`() {
        // Lua 스크립트: INCR 후 count == 1이면 EXPIRE 설정
        // INCR 후 EXPIRE가 누락되면 키가 영구 잔류해 IP 영구 차단 가능
        val key = "RATE:TEST:user4"

        rateLimiter.retryAfterIfExceeded(key, ttlSeconds = 100, limit = 10)

        val ttl = redisTemplate.getExpire(key)
        assertTrue(ttl > 0, "첫 번째 요청 후 TTL이 설정되어야 함, 실제: $ttl")
    }

    @Test
    fun `TTL이 만료되면 카운트가 초기화된다`() {
        // TTL=1초 짧게 설정하여 만료 후 카운트 리셋 확인
        val key = "RATE:TEST:user5"

        repeat(3) { rateLimiter.retryAfterIfExceeded(key, ttlSeconds = 1, limit = 2) }
        // TTL 만료 대기
        Thread.sleep(1200)

        // 만료 후 첫 요청은 다시 한도 이하여야 함
        assertNull(
            rateLimiter.retryAfterIfExceeded(key, ttlSeconds = 1, limit = 2),
            "TTL 만료 후 카운트가 초기화되어야 함",
        )
    }
}
