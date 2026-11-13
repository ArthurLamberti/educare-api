package br.com.educare.api.tenant

/** Rules for a school's or space's address (`<slug>.<root domain>`). */
object Slugs {
    const val PATTERN = "^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$"
    private val regex = Regex(PATTERN)

    /** Subdomains used by the platform itself or easy to confuse with it. */
    private val reserved = setOf(
        "www", "app", "api", "admin", "administrador", "root", "login", "logout", "signup", "cadastro", "entrar",
        "mail", "email", "smtp", "static", "assets", "cdn", "media", "help", "ajuda", "support", "suporte",
        "status", "blog", "docs", "dev", "staging", "test", "teste", "demo-educare", "educare", "platform",
        "plataforma", "billing", "pagamento", "security", "seguranca", "account", "conta", "dashboard",
    )

    fun isValid(slug: String) = regex.matches(slug)

    fun isReserved(slug: String) = slug in reserved
}
