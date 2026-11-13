package br.com.educare.api.tenant

import org.hibernate.cfg.MultiTenancySettings
import org.hibernate.context.spi.CurrentTenantIdentifierResolver
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class TenantHibernateConfig {

    @Bean
    fun tenantIdentifierResolver(): CurrentTenantIdentifierResolver<Long> = object : CurrentTenantIdentifierResolver<Long> {
        override fun resolveCurrentTenantIdentifier(): Long = TenantContext.get() ?: TenantContext.UNRESOLVED
        override fun validateExistingCurrentSessions(): Boolean = false
        override fun isRoot(tenantId: Long): Boolean = tenantId == TenantContext.ROOT
    }

    @Bean
    fun tenantHibernateProperties(resolver: CurrentTenantIdentifierResolver<Long>) =
        HibernatePropertiesCustomizer { it[MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER] = resolver }
}
