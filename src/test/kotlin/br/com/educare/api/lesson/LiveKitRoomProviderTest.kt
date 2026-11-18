package br.com.educare.api.lesson

import br.com.educare.api.common.ServiceUnavailableException
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.SignedJWT
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class LiveKitRoomProviderTest {
    private val secret = "a-livekit-api-secret-of-at-least-32-bytes"
    private val now = Instant.parse("2030-01-15T12:00:00Z")
    private val until = Instant.parse("2030-01-15T14:00:00Z")
    private val provider = LiveKitRoomProvider(
        LiveKitProperties(url = "wss://educare.livekit.cloud", apiKey = "APIkey", apiSecret = secret),
        Clock.fixed(now, ZoneOffset.UTC),
    )

    private fun claims(token: String) = SignedJWT.parse(token).also {
        assertThat(it.verify(MACVerifier(secret.toByteArray()))).isTrue()
    }.jwtClaimsSet

    @Test
    fun `signs a named, expiring token for the lesson's room, and only the teacher administers it`() {
        val access = provider.join("abc", RoomParticipant("user-7", "Ana", host = true), until)
        assertThat(access.provider).isEqualTo("livekit")
        assertThat(access.url).isEqualTo("wss://educare.livekit.cloud")

        val teacher = claims(access.token)
        assertThat(teacher.issuer).isEqualTo("APIkey")
        assertThat(teacher.subject).isEqualTo("user-7")
        assertThat(teacher.getStringClaim("name")).isEqualTo("Ana")
        assertThat(teacher.notBeforeTime.toInstant()).isEqualTo(now)
        assertThat(teacher.expirationTime.toInstant()).isEqualTo(until)
        assertThat(teacher.getJSONObjectClaim("video")).containsEntry("room", "educare-abc")
            .containsEntry("roomJoin", true).containsEntry("roomAdmin", true)

        val student = claims(provider.join("abc", RoomParticipant("user-8", "Maria", host = false), until).token)
        assertThat(student.subject).isEqualTo("user-8")
        assertThat(student.getJSONObjectClaim("video")).containsEntry("room", "educare-abc")
            .containsEntry("roomJoin", true).containsEntry("roomAdmin", false)
    }

    @Test
    fun `without credentials, joining is unavailable`() {
        val unconfigured = LiveKitRoomProvider(LiveKitProperties(), Clock.fixed(now, ZoneOffset.UTC))
        assertThatThrownBy { unconfigured.join("abc", RoomParticipant("user-7", "Ana", host = true), until) }
            .isInstanceOf(ServiceUnavailableException::class.java)
    }
}
