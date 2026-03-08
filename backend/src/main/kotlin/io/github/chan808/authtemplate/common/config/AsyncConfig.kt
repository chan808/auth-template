package io.github.chan808.authtemplate.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync

// @Async 활성화: 메일 발송 등 I/O 작업을 별도 스레드에서 처리해 요청 스레드 블로킹 방지
@Configuration
@EnableAsync
class AsyncConfig
