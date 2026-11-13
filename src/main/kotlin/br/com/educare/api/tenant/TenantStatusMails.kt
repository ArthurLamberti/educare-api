package br.com.educare.api.tenant

import br.com.educare.api.mail.Mailer
import br.com.educare.api.mail.MailProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** Published when a private teacher confirms the email of a space that still waits for activation. */
data class SpaceAwaitingActivation(val slug: String, val spaceName: String, val teacherName: String, val teacherEmail: String)

/** Emails about a space's activation, sent after the change committed; failures are only logged. */
@Component
class TenantStatusMails(private val mailer: Mailer, private val properties: MailProperties) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onActivated(event: SpaceActivated) {
        val text = """
            Olá, ${event.name.substringBefore(' ')}!

            Seu espaço "${event.spaceName}" na Educare está liberado. Já dá para cadastrar alunos,
            agendar aulas e passar tarefas:

            ${event.loginUrl}
        """.trimIndent()
        send(event.to, "Seu espaço na Educare está liberado", text)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onAwaitingActivation(event: SpaceAwaitingActivation) {
        val to = properties.operator?.takeIf { it.isNotBlank() } ?: return
        val text = """
            Novo espaço aguardando liberação:

            ${event.spaceName} (${event.slug})
            ${event.teacherName} <${event.teacherEmail}>

            Libere no painel do operador (/admin no domínio principal).
        """.trimIndent()
        send(to, "Novo espaço aguardando liberação: ${event.slug}", text)
    }

    private fun send(to: String, subject: String, text: String) {
        try {
            mailer.send(to, subject, text)
        } catch (e: Exception) {
            log.error("Could not send email '{}'", subject, e)
        }
    }
}
