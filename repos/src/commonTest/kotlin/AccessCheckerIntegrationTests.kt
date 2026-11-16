package dev.inmo.kroles.repos

import dev.inmo.kroles.roles.BaseRole
import dev.inmo.kroles.roles.rwm.AccessChecker
import dev.inmo.kroles.roles.rwm.RWMRole
import dev.inmo.micro_utils.pagination.Pagination
import dev.inmo.micro_utils.pagination.PaginationResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class AccessCheckerIntegrationTests {
    @Test
    fun checkerSeesInheritedRolesAndOnlyTheRequestedSubject() = runTest {
        val subject = BaseRoleSubject("user")
        val group = BaseRole("group")
        val role = RWMRole("groups", RWMRole.AccessRights(read = true), "42")
        val repo = TestRolesRepo(mapOf(
            subject to listOf(group),
            BaseRoleSubject(group) to listOf(role.role, BaseRole("editor"))
        ))
        val checker = AccessChecker.RWM("groups", RWMRole.AccessRights(read = true, write = true), role.identifier)

        assertTrue(repo.isAccessAllowed(subject, checker))
        assertTrue(repo.isAccessAllowed(subject, AccessChecker { it == BaseRole("editor") }))
        assertFalse(repo.isAccessAllowed(subject, AccessChecker { it == BaseRole("owner") }))
        assertFalse(repo.isAccessAllowed(BaseRoleSubject("another-user"), checker))
    }

    @Test
    fun subjectWithoutRolesDoesNotInvokeChecker() = runTest {
        val repo = TestRolesRepo(emptyMap())

        assertFalse(repo.isAccessAllowed(BaseRoleSubject("user"), AccessChecker { fail("No roles to check") }))
    }

    private class TestRolesRepo(private val roles: Map<BaseRoleSubject, List<BaseRole>>) : ReadRolesRepo {
        override suspend fun getDirectRoles(subject: BaseRoleSubject): List<BaseRole> = roles[subject].orEmpty()
        override suspend fun getDirectSubjects(role: BaseRole): List<BaseRoleSubject> =
            roles.filterValues { role in it }.keys.toList()
        override suspend fun getAll(): Map<BaseRoleSubject, List<BaseRole>> = roles
        override suspend fun getAllRolesByPagination(pagination: Pagination, reversed: Boolean): PaginationResult<BaseRole> =
            error("Pagination is not used by access checks")
        override suspend fun getAllSubjectsByPagination(pagination: Pagination, reversed: Boolean): PaginationResult<BaseRoleSubject> =
            error("Pagination is not used by access checks")
        override suspend fun contains(subject: BaseRoleSubject, role: BaseRole): Boolean =
            role in getAllRoles(subject)
        override suspend fun containsAny(subject: BaseRoleSubject, roles: List<BaseRole>): Boolean =
            getAllRoles(subject).any { it in roles }
    }
}
