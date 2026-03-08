package io.github.chan808.authtemplate.common.config

import io.github.chan808.authtemplate.auth.oauth2.CustomOAuth2UserService
import io.github.chan808.authtemplate.auth.oauth2.OAuth2FailureHandler
import io.github.chan808.authtemplate.auth.oauth2.OAuth2SuccessHandler
import io.github.chan808.authtemplate.common.security.JwtAuthenticationFilter
import io.github.chan808.authtemplate.common.security.JwtProvider
import io.github.chan808.authtemplate.common.security.SecurityExceptionHandler
import org.springframework.beans.factory.annotation.Value
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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // @PreAuthorize 등 메서드 수준 접근 제어 활성화
class SecurityConfig(
    private val jwtProvider: JwtProvider,
    private val securityExceptionHandler: SecurityExceptionHandler,
    private val customOAuth2UserService: CustomOAuth2UserService,
    private val oauth2SuccessHandler: OAuth2SuccessHandler,
    private val oauth2FailureHandler: OAuth2FailureHandler,
    @Value("\${cors.allowed-origin:http://localhost:3000}") private val allowedOrigin: String,
    @Value("\${cookie.secure:false}") private val cookieSecure: Boolean,
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain = http
        // CORS: Spring Security 필터보다 먼저 적용되도록 여기서 설정 (WebMvcConfigurer 단독으로는 부족)
        .cors { it.configurationSource(corsConfigurationSource()) }
        // JWT stateless: CSRF 불필요. RT 쿠키 엔드포인트는 SameSite=Strict + 커스텀 헤더로 보완
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .headers { headers ->
            // HSTS: 운영 HTTPS 환경에서만 활성화 (cookieSecure = true)
            // 로컬 HTTP에서 적용 시 브라우저가 localhost를 HTTPS로 강제해 개발 환경 접속 불가
            if (cookieSecure) {
                headers.httpStrictTransportSecurity { it.maxAgeInSeconds(31536000).includeSubDomains(true) }
            } else {
                headers.httpStrictTransportSecurity { it.disable() }
            }
            // Referrer-Policy: API 응답에 Referer 헤더 미포함 → 내부 URL 유출 방지
            headers.referrerPolicy { it.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER) }
        }
        .authorizeHttpRequests {
            it.requestMatchers("/api/auth/**").permitAll()
            it.requestMatchers(HttpMethod.POST, "/api/members").permitAll() // 회원가입
            it.requestMatchers("/api-docs/**", "/swagger-ui/**").permitAll()
            it.requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll() // OAuth2 흐름
            it.anyRequest().authenticated()
        }
        .oauth2Login { oauth2 ->
            oauth2.authorizationEndpoint { it.baseUri("/oauth2/authorization") }
            oauth2.redirectionEndpoint { it.baseUri("/login/oauth2/code/*") }
            oauth2.userInfoEndpoint { it.userService(customOAuth2UserService) }
            oauth2.successHandler(oauth2SuccessHandler)
            oauth2.failureHandler(oauth2FailureHandler)
        }
        .addFilterBefore(JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter::class.java)
        .exceptionHandling {
            it.authenticationEntryPoint(securityExceptionHandler)
            it.accessDeniedHandler(securityExceptionHandler)
        }
        .build()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        // allowedOrigins: 와일드카드 불가 — allowedOriginPatterns + credentials 조합은 보안 위험
        config.allowedOrigins = listOf(allowedOrigin)
        config.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        // X-CSRF-GUARD: reissue/logout의 CSRF 이중 방어용 커스텀 헤더
        config.allowedHeaders = listOf("Authorization", "Content-Type", "X-CSRF-GUARD")
        // withCredentials: true 대응 — RT HttpOnly 쿠키 자동 전송 허용
        config.allowCredentials = true
        // preflight 캐시 1시간: OPTIONS 사전 요청 빈도 절감
        config.maxAge = 3600L
        return UrlBasedCorsConfigurationSource().also {
            it.registerCorsConfiguration("/**", config)
        }
    }

    @Bean
    // BCrypt work factor 기본값(10): 인증 서버 부하와 보안 강도 균형 → 운영 환경 요건에 따라 조정
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
