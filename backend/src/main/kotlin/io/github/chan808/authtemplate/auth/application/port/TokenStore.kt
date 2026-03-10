package io.github.chan808.authtemplate.auth.application.port

import io.github.chan808.authtemplate.auth.domain.RefreshTokenSession

/**
 * 리프레시 토큰 세션 저장소 포트.
 * 인프라 레이어(Redis 등)에서 구현한다.
 */
interface TokenStore {
    fun save(sid: String, session: RefreshTokenSession, ttlSeconds: Long)
    fun find(sid: String): RefreshTokenSession?
    fun delete(sid: String)
    fun tryLock(sid: String): Boolean
    fun releaseLock(sid: String)
    fun addSession(memberId: Long, sid: String)
    fun deleteAllSessionsForMember(memberId: Long)
}
