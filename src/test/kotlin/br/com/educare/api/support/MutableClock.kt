package br.com.educare.api.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock tests can pin to an instant; follows the system clock until [now] is set. */
class MutableClock : Clock() {
    @Volatile
    var now: Instant? = null

    override fun instant(): Instant = now ?: Instant.now()
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
}

@TestConfiguration(proxyBeanMethods = false)
class ClockTestConfiguration {
    @Bean
    @Primary
    fun testClock(): MutableClock = MutableClock()
}
