package br.com.educare.api.password

import br.com.educare.api.mail.Mailer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class PasswordResetMails(private val mailer: Mailer) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRequested(event: PasswordResetRequested) {
        val text = """
            Olá, ${event.name.substringBefore(' ')}!

            Recebemos um pedido para criar uma nova senha (${event.place}). Abra o link abaixo:

            ${event.link}

            O link vale por 1 hora e só pode ser usado uma vez. Se não foi você, ignore esta mensagem:
            sua senha continua a mesma.
        """.trimIndent()
        try {
            mailer.send(event.to, "Criar nova senha na Educare", text)
        } catch (e: Exception) {
            log.error("Could not send the password reset email", e)
        }
    }
}
