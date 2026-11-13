package br.com.educare.api.mail

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

/** Sends plain-text emails. Tests replace it with a recording implementation. */
fun interface Mailer {
    fun send(to: String, subject: String, text: String)
}

@ConfigurationProperties("educare.mail")
data class MailProperties(
    /** Sender address, e.g. `Educare <nao-responda@educare.com>`. */
    val from: String = "Educare <nao-responda@educare.localhost>",
    /** Where to tell the operator that a new space waits for activation; unset = no notice. */
    val operator: String? = null,
)

/** SMTP when `spring.mail.host` is configured (Mailpit in development); otherwise emails are only logged. */
@Configuration(proxyBeanMethods = false)
class MailConfiguration {
    private val log = LoggerFactory.getLogger(Mailer::class.java)

    @Bean
    fun mailer(sender: ObjectProvider<JavaMailSender>, properties: MailProperties): Mailer {
        val smtp = sender.ifAvailable
        if (smtp == null) {
            log.warn("spring.mail.host is not set: emails will only be logged")
            return Mailer { to, subject, text -> log.info("Email to {}: {}\n{}", to, subject, text) }
        }
        return Mailer { to, subject, text ->
            smtp.send(SimpleMailMessage().apply {
                from = properties.from
                setTo(to)
                this.subject = subject
                this.text = text
            })
        }
    }
}
