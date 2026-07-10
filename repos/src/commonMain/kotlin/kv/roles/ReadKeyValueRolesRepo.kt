package dev.inmo.kroles.repos.kv.roles

import dev.inmo.kroles.repos.BaseRoleSubject
import dev.inmo.kroles.repos.ReadRolesRepo
import dev.inmo.kroles.roles.BaseRole
import dev.inmo.micro_utils.pagination.Pagination
import dev.inmo.micro_utils.pagination.PaginationResult
import dev.inmo.micro_utils.pagination.changeResultsUnchecked
import dev.inmo.micro_utils.pagination.utils.doForAllWithNextPaging
import dev.inmo.micro_utils.pagination.utils.getAllByWithNextPaging
import dev.inmo.micro_utils.pagination.utils.optionallyReverse
import dev.inmo.micro_utils.pagination.utils.paginate
import dev.inmo.micro_utils.repos.ReadKeyValuesRepo
import dev.inmo.micro_utils.repos.pagination.maxPagePagination

/**
 * Read-only [ReadRolesRepo] implementation backed by a key-value store.
 *
 * Roles are persisted as values keyed by their owning [BaseRoleSubject]: each subject maps to the
 * list of [BaseRole]s directly granted to it.
 *
 * @param keyValuesRepo Underlying key-values store mapping each subject to the roles granted to it.
 */
class ReadKeyValueRolesRepo(
    private val keyValuesRepo: ReadKeyValuesRepo<BaseRoleSubject, BaseRole>,
) : ReadRolesRepo {
    /**
     * Returns all subjects that have the given [role] directly granted to them.
     */
    override suspend fun getDirectSubjects(role: BaseRole): List<BaseRoleSubject> {
        return keyValuesRepo.getAllByWithNextPaging(keyValuesRepo.maxPagePagination()) {
            keys(role, it)
        }
    }

    /**
     * Returns all roles directly granted to the given [subject].
     */
    override suspend fun getDirectRoles(subject: BaseRoleSubject): List<BaseRole> {
        return keyValuesRepo.getAll(subject)
    }

    /**
     * Returns the whole mapping of every subject to the roles directly granted to it.
     */
    override suspend fun getAll(): Map<BaseRoleSubject, List<BaseRole>> = keyValuesRepo.getAll()

    /**
     * Returns a page of the distinct roles that act as subjects (registered custom roles).
     *
     * Iterates over every stored subject, collects those that are roles acting as subjects, and
     * paginates the resulting set, honoring [reversed] ordering.
     *
     * @param pagination Requested page bounds.
     * @param reversed Whether the collected roles should be paginated in reversed order.
     */
    override suspend fun getAllRolesByPagination(
        pagination: Pagination,
        reversed: Boolean
    ): PaginationResult<BaseRole> {
        val customRoles = mutableSetOf<BaseRole>()
        doForAllWithNextPaging {
            keyValuesRepo.keys(it).also { paginationResult ->
                paginationResult.results.forEach { subject ->
                    if (subject is BaseRoleSubject.OtherRole) {
                        customRoles.add(subject.role as? BaseRole ?: return@forEach)
                    }
                }
            }
        }

        val result = customRoles.paginate(pagination.optionallyReverse(customRoles.size, reversed))
        return if (reversed) {
            result.changeResultsUnchecked(
                result.results.reversed()
            )
        } else {
            result
        }
    }

    /**
     * Returns a page of all subjects (keys) known to the underlying store.
     *
     * @param pagination Requested page bounds.
     * @param reversed Whether keys should be returned in reversed order.
     */
    override suspend fun getAllSubjectsByPagination(pagination: Pagination, reversed: Boolean): PaginationResult<BaseRoleSubject> {
        return keyValuesRepo.keys(pagination, reversed)
    }

    /**
     * Returns true if the given [role] is directly granted to the given [subject].
     */
    override suspend fun contains(subject: BaseRoleSubject, role: BaseRole): Boolean {
        return keyValuesRepo.contains(subject, role)
    }

    /**
     * Returns true if any of the given [roles] is directly granted to the given [subject].
     */
    override suspend fun containsAny(subject: BaseRoleSubject, roles: List<BaseRole>): Boolean {
        return roles.any {
            keyValuesRepo.contains(subject, it)
        }
    }
}