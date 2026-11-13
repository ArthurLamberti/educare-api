package br.com.educare.api.signup

import br.com.educare.api.mail.Mailer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** Sends the confirmation email after the sign-up committed; a failure is logged (the teacher can resend). */
@Component
class SignupMails(private val mailer: Mailer) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onConfirmationRequested(event: ConfirmationEmailRequested) {
        val firstName = event.name.substringBefore(' ')
        val text = """
            Olá, $firstName!

            Falta pouco para o seu espaço "${event.spaceName}" na Educare ficar pronto.
            Abra o link abaixo e confirme o seu e-mail:

            ${event.link}

            O link vale por 48 horas. Se você não se cadastrou na Educare, ignore esta mensagem.
        """.trimIndent()
        try {
            mailer.send(event.to, "Confirme seu e-mail na Educare", text)
        } catch (e: Exception) {
            log.error("Could not send the confirmation email", e)
        }
    }
}
