package io.github.chan808.authtemplate.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secret: String,
    val accessTokenExpiry: Long, // seconds
    val refreshTokenExpiry: Long, // seconds
)
