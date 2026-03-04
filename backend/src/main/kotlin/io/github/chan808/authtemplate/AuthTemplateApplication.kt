package io.github.chan808.authtemplate

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class AuthTemplateApplication

fun main(args: Array<String>) {
	runApplication<AuthTemplateApplication>(*args)
}
