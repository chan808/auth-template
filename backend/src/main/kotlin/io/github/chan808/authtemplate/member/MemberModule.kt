@file:Suppress("unused")
package io.github.chan808.authtemplate.member
import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = ["common"],
)
class MemberModule
