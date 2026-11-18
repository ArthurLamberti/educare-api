package br.com.educare.api.support

import br.com.educare.api.lesson.RoomAccess
import br.com.educare.api.lesson.RoomParticipant
import br.com.educare.api.lesson.VirtualRoomProvider
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

/** Stands in for the video provider: a predictable link per room and participant, no network. */
class FakeRoomProvider : VirtualRoomProvider {
    override fun join(roomKey: String, participant: RoomParticipant, until: Instant): RoomAccess {
        val name = URLEncoder.encode(participant.name, StandardCharsets.UTF_8)
        val url = "https://rooms.test/$roomKey?name=$name&host=${participant.host}&until=${until.epochSecond}"
        return RoomAccess("fake", url, "token-${participant.id}")
    }
}

@TestConfiguration(proxyBeanMethods = false)
class RoomTestConfiguration {
    @Bean
    @Primary
    fun fakeRoomProvider(): FakeRoomProvider = FakeRoomProvider()
}
