package br.com.educare.api.support

import br.com.educare.api.mail.Mailer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.CopyOnWriteArrayList

/** Keeps sent emails in memory so tests can read the links. */
class RecordingMailer : Mailer {
    data class Mail(val to: String, val subject: String, val text: String)

    val sent = CopyOnWriteArrayList<Mail>()

    override fun send(to: String, subject: String, text: String) {
        sent += Mail(to, subject, text)
    }

    /** The confirmation token in the last email sent to [to]. */
    fun lastTokenFor(to: String): String {
        val mail = sent.lastOrNull { it.to == to } ?: error("No email sent to $to")
        return Regex("""token=([A-Za-z0-9_-]+)""").find(mail.text)?.groupValues?.get(1) ?: error("No link in: ${mail.text}")
    }
}

@TestConfiguration(proxyBeanMethods = false)
class MailTestConfiguration {
    @Bean
    @Primary
    fun recordingMailer(): RecordingMailer = RecordingMailer()
}
