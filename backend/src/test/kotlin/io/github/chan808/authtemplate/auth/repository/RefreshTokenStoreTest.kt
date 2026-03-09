package io.github.chan808.authtemplate.auth.repository

import io.github.chan808.authtemplate.auth.domain.RefreshTokenSession
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
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DataRedisTest
@Import(RefreshTokenStore::class)
@Testcontainers
class RefreshTokenStoreTest {

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

    @Autowired lateinit var store: RefreshTokenStore
    @Autowired lateinit var redisTemplate: StringRedisTemplate

    private val session = RefreshTokenSession(
        memberId = 1L,
        role = "USER",
        tokenHash = "hash-value",
        absoluteExpiryEpoch = Instant.now().plusSeconds(2592000).epochSecond,
    )

    @AfterEach
    fun cleanup() {
        // 테스트 간 간섭 방지: 사용된 모든 키 삭제
        redisTemplate.connectionFactory?.connection?.flushAll()
    }

    // ───────────────────────────── 기본 CRUD ─────────────────────────────

    @Test
    fun `세션을 저장하면 동일한 sid로 조회할 수 있다`() {
        val sid = UUID.randomUUID().toString()

        store.save(sid, session, ttlSeconds = 3600)

        val found = store.find(sid)
        assertNotNull(found)
        assertEquals(1L, found.memberId)
        assertEquals("USER", found.role)
        assertEquals("hash-value", found.tokenHash)
    }

    @Test
    fun `존재하지 않는 sid 조회 시 null을 반환한다`() {
        assertNull(store.find("non-existent-sid"))
    }

    @Test
    fun `delete 호출 후 해당 sid는 조회되지 않는다`() {
        val sid = UUID.randomUUID().toString()
        store.save(sid, session, ttlSeconds = 3600)

        store.delete(sid)

        assertNull(store.find(sid))
    }

    @Test
    fun `저장 시 지정한 TTL이 Redis 키에 설정된다`() {
        val sid = UUID.randomUUID().toString()
        store.save(sid, session, ttlSeconds = 100)

        val ttl = redisTemplate.getExpire("RT:$sid")
        // 네트워크 지연 등으로 1초 내 오차 허용
        assertTrue(ttl in 98..100, "TTL은 지정값(100)에 근접해야 함, 실제: $ttl")
    }

    // ───────────────────────────── 재발급 Lock (SETNX) ─────────────────────────────

    @Test
    fun `같은 sid로 tryLock을 두 번 호출하면 첫 번째만 성공한다`() {
        val sid = UUID.randomUUID().toString()

        assertTrue(store.tryLock(sid), "첫 번째 락 획득은 성공해야 함")
        assertFalse(store.tryLock(sid), "이미 락이 있으면 false를 반환해야 함")
    }

    @Test
    fun `releaseLock 후에는 동일한 sid로 락을 다시 획득할 수 있다`() {
        val sid = UUID.randomUUID().toString()
        store.tryLock(sid)

        store.releaseLock(sid)

        assertTrue(store.tryLock(sid), "락 해제 후 재획득이 가능해야 함")
    }

    // ───────────────────────────── 세션 세트 & 전체 삭제 (Lua) ─────────────────────────────

    @Test
    fun `addSession 후 deleteAllSessionsForMember를 호출하면 RT와 세션 세트가 모두 삭제된다`() {
        val sid = UUID.randomUUID().toString()
        store.save(sid, session, ttlSeconds = 3600)
        store.addSession(1L, sid)

        store.deleteAllSessionsForMember(1L)

        assertNull(store.find(sid))
        assertFalse(
            redisTemplate.hasKey("MEMBER_SESSIONS:1"),
            "세션 세트 키도 삭제되어야 함",
        )
    }

    @Test
    fun `여러 세션을 등록한 뒤 deleteAllSessionsForMember 호출 시 모든 세션이 원자적으로 삭제된다`() {
        // 비밀번호 변경 시나리오: 멀티 디바이스 로그인 상태에서 전체 강제 로그아웃
        val sids = List(3) { UUID.randomUUID().toString() }
        sids.forEach { sid ->
            store.save(sid, session, ttlSeconds = 3600)
            store.addSession(1L, sid)
        }

        store.deleteAllSessionsForMember(1L)

        // 3개 RT 키 전부 삭제 확인
        sids.forEach { sid ->
            assertNull(store.find(sid), "sid=$sid 의 RT 키가 삭제되지 않았음")
        }
        assertFalse(redisTemplate.hasKey("MEMBER_SESSIONS:1"), "세션 세트 키가 삭제되지 않았음")
    }

    @Test
    fun `세션이 없는 회원에게 deleteAllSessionsForMember 호출 시 예외 없이 정상 종료된다`() {
        // 세트가 비어 있거나 없는 경우도 Lua 스크립트가 정상 처리하는지 확인
        store.deleteAllSessionsForMember(99L)
    }
}
