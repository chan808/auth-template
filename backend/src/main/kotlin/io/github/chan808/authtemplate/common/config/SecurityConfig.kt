package io.github.chan808.authtemplate.common.config

import io.github.chan808.authtemplate.common.security.JwtAuthenticationFilter
import io.github.chan808.authtemplate.common.security.JwtProvider
import io.github.chan808.authtemplate.common.security.SecurityExceptionHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // @PreAuthorize 등 메서드 수준 접근 제어 활성화
class SecurityConfig(
    private val jwtProvider: JwtProvider,
    private val securityExceptionHandler: SecurityExceptionHandler,
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain = http
        // JWT stateless: CSRF 불필요. RT 쿠키 엔드포인트는 SameSite=Strict + 커스텀 헤더로 보완
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests {
            it.requestMatchers("/api/auth/**").permitAll()
            it.requestMatchers(HttpMethod.POST, "/api/members").permitAll() // 회원가입
            it.requestMatchers("/api-docs/**", "/swagger-ui/**").permitAll()
            it.anyRequest().authenticated()
        }
        .addFilterBefore(JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter::class.java)
        .exceptionHandling {
            it.authenticationEntryPoint(securityExceptionHandler)
            it.accessDeniedHandler(securityExceptionHandler)
        }
        .build()

    @Bean
    // BCrypt work factor 기본값(10): 인증 서버 부하와 보안 강도 균형 → 운영 환경 요건에 따라 조정
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
