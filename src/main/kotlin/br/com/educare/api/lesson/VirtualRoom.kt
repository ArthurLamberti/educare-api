package br.com.educare.api.lesson

import br.com.educare.api.common.ServiceUnavailableException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Date

/** Who joins a virtual room. [id] is stable per user; the host (the lesson's teacher) can manage the call. */
data class RoomParticipant(val id: String, val name: String, val host: Boolean)

/**
 * How the web connects [participant] to a room: the provider's client ([provider]) gets the server [url]
 * and the participant's [token].
 */
data class RoomAccess(val provider: String, val url: String, val token: String)

/**
 * Hosts the virtual rooms that Educare creates for online lessons. Lessons store only a provider-neutral
 * room key; the provider is asked for access when someone joins (`GET /api/lessons/{id}/room`), so switching
 * providers moves every room, also those of lessons created before, and users never see the change.
 */
interface VirtualRoomProvider {
    /**
     * Access for [participant] to the room [roomKey] until [until], shown inside an Educare page.
     * Throws [ServiceUnavailableException] when the provider is not available.
     */
    fun join(roomKey: String, participant: RoomParticipant, until: Instant): RoomAccess
}

/** Random room keys: 128 bits, hex. */
object RoomKeys {
    private val random = SecureRandom()

    fun newKey(): String = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
}

@ConfigurationProperties("educare.virtual-room.livekit")
data class LiveKitProperties(
    /** Project URL, e.g. `wss://educare-xxxx.livekit.cloud` (EDUCARE_LIVEKIT_URL). */
    val url: String = "",
    /** API key and secret of the project (EDUCARE_LIVEKIT_API_KEY, EDUCARE_LIVEKIT_API_SECRET). */
    val apiKey: String = "",
    val apiSecret: String = "",
)

/**
 * LiveKit (Cloud's free Build plan, no credit card): Educare signs an access token per participant (named,
 * expiring, room admin for the teacher). No call to LiveKit is needed: the room is created when the first
 * participant connects. Without a token nobody gets in.
 */
@Component
class LiveKitRoomProvider(
    private val properties: LiveKitProperties,
    private val clock: Clock = Clock.systemUTC(),
) : VirtualRoomProvider {

    override fun join(roomKey: String, participant: RoomParticipant, until: Instant): RoomAccess {
        if (properties.url.isBlank() || properties.apiKey.isBlank() || properties.apiSecret.isBlank()) {
            throw ServiceUnavailableException("Virtual rooms are not configured")
        }
        val claims = JWTClaimsSet.Builder()
            .issuer(properties.apiKey)
            .subject(participant.id)
            .claim("name", participant.name)
            .notBeforeTime(Date.from(clock.instant()))
            .expirationTime(Date.from(until))
            .claim(
                "video",
                mapOf(
                    "room" to "educare-$roomKey",
                    "roomJoin" to true,
                    "roomAdmin" to participant.host,
                    "canPublish" to true,
                    "canSubscribe" to true,
                    "canPublishData" to true,
                ),
            )
            .build()
        val jwt = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims)
        jwt.sign(MACSigner(properties.apiSecret.toByteArray()))
        return RoomAccess(PROVIDER, properties.url, jwt.serialize())
    }

    companion object {
        const val PROVIDER = "livekit"
    }
}
