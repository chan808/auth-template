package io.github.chan808.authtemplate.common.config

import jakarta.persistence.EntityManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import com.querydsl.jpa.impl.JPAQueryFactory

@Configuration
@EnableJpaAuditing
class JpaConfig {

    @Bean
    fun jpaQueryFactory(em: EntityManager): JPAQueryFactory = JPAQueryFactory(em)
}
