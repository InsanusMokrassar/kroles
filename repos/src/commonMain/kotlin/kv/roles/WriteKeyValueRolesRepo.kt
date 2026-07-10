package dev.inmo.kroles.repos.kv.roles

import dev.inmo.kroles.repos.BaseRoleSubject
import dev.inmo.kroles.repos.ReadProtectedRolesRepo
import dev.inmo.kroles.repos.WriteRolesRepo
import dev.inmo.kroles.roles.BaseRole
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.TagLogger
import dev.inmo.kslog.common.w
import dev.inmo.micro_utils.pagination.utils.doForAllWithNextPaging
import dev.inmo.micro_utils.repos.KeyValuesRepo
import dev.inmo.micro_utils.repos.MapsReposDefaultMutableSharedFlow
import dev.inmo.micro_utils.repos.add
import dev.inmo.micro_utils.repos.pagination.maxPagePagination
import dev.inmo.micro_utils.repos.remove
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Mutating [WriteRolesRepo] implementation backed by a key-value store.
 *
 * Grants and revokes roles by adding/removing values in [keyValuesRepo], honoring the access-rights
 * protection reported by [protectedRolesRepo] and emitting change events through the repo flows. Any
 * failure of the underlying store is caught, logged, and reported as a `false` result rather than
 * propagated.
 *
 * @param keyValuesRepo Underlying key-values store mapping each subject to the roles granted to it.
 * @param protectedRolesRepo Source of include/exclude access-rights checks; unprotected by default.
 * @param logger Logger used to report protection denials and store failures.
 */
class WriteKeyValueRolesRepo(
    private val keyValuesRepo: KeyValuesRepo<BaseRoleSubject, BaseRole>,
    private val protectedRolesRepo: ReadProtectedRolesRepo = ReadProtectedRolesRepo.AlwaysUnprotected,
    private val logger: KSLog = TagLogger("WriteKeyValueRolesRepo")
) : WriteRolesRepo {
    private val _roleIncluded = MapsReposDefaultMutableSharedFlow<Pair<BaseRoleSubject, BaseRole>>()
    /** Emits each (subject, role) pair when a role is successfully granted to a subject. */
    override val roleIncluded: Flow<Pair<BaseRoleSubject, BaseRole>> = _roleIncluded.asSharedFlow()
    private val _roleExcluded = MapsReposDefaultMutableSharedFlow<Pair<BaseRoleSubject, BaseRole>>()
    /** Emits each (subject, role) pair when a role is successfully revoked from a subject. */
    override val roleExcluded: Flow<Pair<BaseRoleSubject, BaseRole>> = _roleExcluded.asSharedFlow()
    private val _roleCreated = MapsReposDefaultMutableSharedFlow<BaseRole>()
    /** Emits each role when it is successfully created (registered). */
    override val roleCreated: Flow<BaseRole> = _roleCreated.asSharedFlow()
    private val _roleRemoved = MapsReposDefaultMutableSharedFlow<BaseRole>()
    /** Emits each role when it is successfully removed. */
    override val roleRemoved: Flow<BaseRole> = _roleRemoved.asSharedFlow()

    /**
     * Grants [role] to [subject] directly.
     *
     * Refuses (and logs) if inclusion is not allowed by [protectedRolesRepo]. Does nothing if the
     * role is already present. On success emits to [roleIncluded].
     *
     * @return true if the role was newly added, false if it was denied, already present, or the
     * store operation failed.
     */
    override suspend fun includeDirect(subject: BaseRoleSubject, role: BaseRole): Boolean {
        if (!protectedRolesRepo.allowInclude(subject)) {
            logger.w { "Unable to include role \"$role\" to the subject \"$subject\" (protected)" }
            return false
        }

        return runCatching {
            if (keyValuesRepo.contains(subject, role)) {
                false
            } else {
                keyValuesRepo.add(
                    subject,
                    role
                )
                true
            }
        }.getOrElse {
            logger.w(it) { "Unable to include role \"$role\" to the subject \"$subject\"" }
            false
        }.also {
            if (it) {
                _roleIncluded.emit(subject to role)
            }
        }
    }

    /**
     * Revokes [role] from [subject] directly.
     *
     * Refuses (and logs) if exclusion is not allowed by [protectedRolesRepo]. On success emits to
     * [roleExcluded].
     *
     * @return true if the removal was performed, false if it was denied or the store operation
     * failed.
     */
    override suspend fun excludeDirect(subject: BaseRoleSubject, role: BaseRole): Boolean {
        if (!protectedRolesRepo.allowExclude(subject)) {
            logger.w { "Unable to exclude role \"$role\" to the subject \"$subject\" (protected)" }
            return false
        }

        return runCatching {
            keyValuesRepo.remove(
                subject,
                role
            )
            true
        }.getOrElse {
            logger.w(it) { "Unable to exclude role \"$role\" to the subject \"$subject\"" }
            false
        }.also {
            if (it) {
                _roleExcluded.emit(subject to role)
            }
        }
    }

    /**
     * Registers [newRole] as a known role.
     *
     * Registration is represented by granting the empty marker role to the role acting as its own
     * subject. Does nothing if the role is already registered. On success emits to [roleCreated].
     *
     * @return true if the role was newly registered, false if it already existed or the operation
     * failed.
     */
    override suspend fun createRole(newRole: BaseRole): Boolean {
        return runCatching {
            val registered = keyValuesRepo.contains(
                BaseRoleSubject(newRole),
                BaseRole.EMPTY
            )
            if (registered) {
                false
            } else {
                includeDirect(BaseRoleSubject.OtherRole(newRole), BaseRole.EMPTY)
            }
        }.getOrElse {
            logger.w(it) { "Unable to create role \"$newRole\"" }
            false
        }.also {
            if (it) {
                _roleCreated.emit(newRole)
            }
        }
    }

    /**
     * Removes [role] entirely.
     *
     * Clears the entry for the role acting as its own subject and revokes the role from every
     * subject that currently holds it (found by paging through the store). On success emits to
     * [roleRemoved].
     *
     * @return true if the removal completed, false if the operation failed.
     */
    override suspend fun removeRole(role: BaseRole): Boolean {
        return runCatching {
            val subject = BaseRoleSubject.OtherRole(role)

            val subjectsWithRole = mutableListOf<BaseRoleSubject>()

            val pagination = keyValuesRepo.maxPagePagination()

            doForAllWithNextPaging(pagination) {
                keyValuesRepo.keys(role, pagination).also { keysPagination ->
                    subjectsWithRole.addAll(keysPagination.results)
                }
            }

            val roleList = listOf(role)

            keyValuesRepo.clear(subject)
            keyValuesRepo.remove(subjectsWithRole.associateWith { roleList })
            true
        }.getOrElse {
            logger.w(it) { "Unable to remove role \"$role\"" }
            false
        }.also {
            if (it) {
                _roleRemoved.emit(role)
            }
        }
    }
}