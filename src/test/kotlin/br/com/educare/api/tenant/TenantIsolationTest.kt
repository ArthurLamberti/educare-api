package br.com.educare.api.tenant

import br.com.educare.api.support.IntegrationTest
import br.com.educare.api.user.Role
import br.com.educare.api.user.User
import br.com.educare.api.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TenantIsolationTest : IntegrationTest() {

    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var transactions: TransactionTemplate

    private fun <T> inTenant(tenantId: Long, block: () -> T): T =
        TenantContext.runAs(tenantId) { transactions.execute { block() } as T }

    @Test
    fun `rows of one school are invisible to another`() {
        val alpha = provisionSchool("alpha").id
        val beta = provisionSchool("beta").id
        val saved = inTenant(alpha) {
            users.save(User(name = "Ana", email = "ana@x.com", passwordHash = "x", role = Role.TEACHER))
        }
        assertEquals(alpha, saved.tenantId)

        inTenant(beta) {
            assertTrue(users.findById(requireNotNull(saved.id)).isEmpty, "findById must not cross tenants")
            assertNull(users.findByEmail("ana@x.com"))
            assertTrue(users.findAll().none { it.email == "ana@x.com" })
        }
    }

    @Test
    fun `without a tenant nothing is visible`() {
        provisionSchool("alpha")
        TenantContext.clear()
        assertTrue(users.findAll().isEmpty())
    }

    @Test
    fun `same email can exist in two schools`() {
        val alpha = provisionSchool("alpha").id
        val beta = provisionSchool("beta").id
        inTenant(alpha) { users.save(User(name = "Ana", email = "ana@x.com", passwordHash = "x", role = Role.TEACHER)) }
        inTenant(beta) { users.save(User(name = "Ana", email = "ana@x.com", passwordHash = "x", role = Role.TEACHER)) }
    }

    @Test
    fun `unknown tenant is rejected with 404`() {
        mockMvc.post("/api/auth/login") { header("X-Tenant", "no-such-school") }.andExpect {
            status { isNotFound() }
            jsonPath("$.detail") { value("School not found") }
        }
    }

    @Test
    fun `public school information without login`() {
        provisionSchool("alpha")
        mockMvc.get("/api/tenant") { header("X-Tenant", "alpha") }.andExpect {
            status { isOk() }
            jsonPath("$.slug") { value("alpha") }
            jsonPath("$.name") { value("School alpha") }
            jsonPath("$.timeZone") { value("America/Sao_Paulo") }
        }
        mockMvc.get("/api/tenant") { header("X-Tenant", "nope") }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `missing tenant is rejected with 404`() {
        mockMvc.post("/api/auth/login").andExpect { status { isNotFound() } }
    }
}
