package dev.inmo.kroles.repos

import dev.inmo.kroles.roles.BaseRole
import dev.inmo.kslog.common.e
import dev.inmo.kslog.common.logger
import dev.inmo.micro_utils.coroutines.*
import dev.inmo.micro_utils.pagination.Pagination
import dev.inmo.micro_utils.pagination.PaginationResult
import dev.inmo.micro_utils.pagination.utils.paginate
import dev.inmo.micro_utils.repos.cache.CacheRepo
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Caching decorator over another [RolesRepo]. It keeps a precomputed in-memory snapshot of the whole subject/role
 * hierarchy (built via [buildRolesNodesGraph]) so that read operations are served without touching the underlying
 * repo, while writes are delegated to it and trigger a cache refresh.
 *
 * The snapshot is rebuilt whenever the [originalRepo] emits a change event, and the refresh work is serialized
 * through an internal queue guarded by [locker].
 *
 * @param originalRepo the underlying repository this cache decorates and delegates writes to
 * @param scope coroutine scope used for the background cache-update job and event subscriptions
 * @param locker read/write lock protecting the cached state
 * @param onIdentifierRemovedFlow optional flow of direct identifiers whose roles should be excluded when emitted
 */
class CacheRolesRepo(
    private val originalRepo: RolesRepo,
    scope: CoroutineScope,
    private val locker: SmartRWLocker,
    private val onIdentifierRemovedFlow: Flow<BaseRolSubjectDirectIdentifier> = emptyFlow()
) : RolesRepo, CacheRepo {
    private val directSubjectRoles = mutableMapOf<BaseRoleSubject, MutableSet<BaseRole>>()
    private var currentTree: Map<BaseRoleSubject, Set<BaseRole>> = emptyMap()
    private var currentTreeLists: Map<BaseRoleSubject, List<BaseRole>> = emptyMap()
    private var currentRootNodesBySubject: Map<BaseRoleSubject, GraphNode<BaseRoleSubject>> = emptyMap()
    private var directRolesBySubjects: Map<BaseRoleSubject, List<BaseRole>> = emptyMap()
    private var directRolesSubjects: Map<BaseRole, List<BaseRoleSubject>> = emptyMap()
    private var rolesSubjects: Map<BaseRole, List<BaseRoleSubject>> = emptyMap()
    private var rolesBySubjects: Map<BaseRoleSubject, List<BaseRole>> = emptyMap()
//    private val customRoles = mutableListOf<BaseRole>()
//    private val allSubjectRoles = mutableMapOf<RoleSubject, Set<BaseRole>>()
//    private val directRoleSubjects = mutableMapOf<Role, MutableSet<RoleSubject>>()

    private val _roleIncluded = MutableSharedFlow<Pair<BaseRoleSubject, BaseRole>>()
    override val roleIncluded: Flow<Pair<BaseRoleSubject, BaseRole>> = _roleIncluded.asSharedFlow()

    private val _roleExcluded = MutableSharedFlow<Pair<BaseRoleSubject, BaseRole>>()
    override val roleExcluded: Flow<Pair<BaseRoleSubject, BaseRole>> = _roleExcluded.asSharedFlow()

    private val _roleCreated = MutableSharedFlow<BaseRole>()
    override val roleCreated: Flow<BaseRole> = _roleCreated.asSharedFlow()
    private val _roleRemoved = MutableSharedFlow<BaseRole>()
    override val roleRemoved: Flow<BaseRole> = _roleRemoved.asSharedFlow()
    private val updatesQueue = Channel<CompletableJob>(UNLIMITED)
    private val updatesJob = scope.launchSafelyWithoutExceptions {
        for (updateRequested in updatesQueue) {
            val pendingDeferreds = mutableListOf(updateRequested)
            do {
                pendingDeferreds.add(updatesQueue.tryReceive().getOrNull() ?: break)
            } while (currentCoroutineContext().isActive)
            locker.withWriteLock {
                try {
                    fullUpdate()

                    pendingDeferreds.forEach {
                        runCatchingLogging { it.complete() }
                    }
                } catch (e: Throwable) {
                    this@CacheRolesRepo.logger.e(e) { "Unable to update roles cache" }
                }
            }
        }
    }
    private suspend fun fullUpdate() {
        while (currentCoroutineContext().isActive) {
            try {
                currentTreeLists = originalRepo.getAll()
                currentTree = currentTreeLists.mapValues { it.value.toSet() }
                currentRootNodesBySubject = buildRolesNodesGraph(currentTree)

                val newDirectRolesSubjects = mutableMapOf<BaseRole, List<BaseRoleSubject>>()
                val newRolesSubjects = mutableMapOf<BaseRole, List<BaseRoleSubject>>()
                val newRolesBySubjects = mutableMapOf<BaseRoleSubject, List<BaseRole>>()
                val newDirectRolesBySubjects = mutableMapOf<BaseRoleSubject, List<BaseRole>>()
                currentRootNodesBySubject.forEach { (subject, node) ->
                    val subjectAsRole = (subject as? BaseRoleSubject.OtherRole) ?.role
                    subjectAsRole ?.let {
                        newDirectRolesSubjects[it] = node.parents.map { it.value }
                        newRolesSubjects[it] = node.allParents.map { it.value }
                    }
                    newRolesBySubjects[subject] = node.allChildren.mapNotNull { (it.value as? BaseRoleSubject.OtherRole) ?.role }
                    newDirectRolesBySubjects[subject] = node.children.mapNotNull { (it.value as? BaseRoleSubject.OtherRole) ?.role }
                }
                directRolesSubjects = newDirectRolesSubjects.toMap()
                rolesSubjects = newRolesSubjects.toMap()
                rolesBySubjects = newRolesBySubjects.toMap()
                directRolesBySubjects = newDirectRolesBySubjects.toMap()
                return
            } catch (e: Throwable) {
                this@CacheRolesRepo.logger.e(e) { "Unable to update roles cache" }
            }
        }
    }

    private fun requestUpdateCache(): Job {
        val deferred = Job()

        updatesQueue.trySend(deferred)

        return deferred
    }

    init {
        val initDeferred = requestUpdateCache()
        scope.launchSafelyWithoutExceptions {
            initDeferred.join()
            directSubjectRoles.filter { it.key is BaseRoleSubject.Direct }.map { (subject, roles) ->
                roles.forEach {
                    _roleIncluded.emit(subject to it)
                }
            }
        }

        originalRepo.roleIncluded.subscribeSafelyWithoutExceptions(scope) { (subject, role) ->
            requestUpdateCache().join()
            _roleIncluded.emit(subject to role)
        }
        originalRepo.roleExcluded.subscribeSafelyWithoutExceptions(scope) { (subject, role) ->
            requestUpdateCache().join()
            _roleExcluded.emit(subject to role)
        }
        originalRepo.roleCreated.subscribeSafelyWithoutExceptions(scope) { role ->
            requestUpdateCache().join()
            _roleCreated.emit(role)
        }
        originalRepo.roleRemoved.subscribeSafelyWithoutExceptions(scope) { role ->
            requestUpdateCache().join()
            _roleRemoved.emit(role)
        }
        onIdentifierRemovedFlow.subscribeSafelyWithoutExceptions(scope) {
            val subject = BaseRoleSubject.Direct(it)
            getDirectRoles(subject).forEach { role ->
                excludeDirect(subject, role)
            }
        }
    }

    override suspend fun getDirectSubjects(role: BaseRole): List<BaseRoleSubject> {
        return locker.withReadAcquire {
            directRolesSubjects[role] ?: emptyList()
        }
    }

    override suspend fun getDirectRoles(subject: BaseRoleSubject): List<BaseRole> {
        return locker.withReadAcquire {
            directRolesBySubjects[subject] ?: emptyList()
        }
    }

    override suspend fun getAll(): Map<BaseRoleSubject, List<BaseRole>> {
        return locker.withReadAcquire {
            currentTreeLists.toMap()
        }
    }

    override suspend fun getAllRolesByPagination(pagination: Pagination, reversed: Boolean): PaginationResult<BaseRole> {
        return locker.withReadAcquire {
            rolesSubjects.keys
        }.paginate(pagination, reversed)
    }

    override suspend fun getAllRoles(subject: BaseRoleSubject): List<BaseRole> {
        return locker.withReadAcquire {
            rolesBySubjects[subject] ?: emptyList()
        }
    }

    override suspend fun getAllSubjectsByPagination(
        pagination: Pagination,
        reversed: Boolean
    ): PaginationResult<BaseRoleSubject> {
        return locker.withReadAcquire {
            directRolesBySubjects.keys.paginate(pagination, reversed)
        }
    }

    override suspend fun contains(subject: BaseRoleSubject, role: BaseRole): Boolean {
        return getAllRoles(subject).contains(role)
    }

    override suspend fun containsAny(subject: BaseRoleSubject, roles: List<BaseRole>): Boolean {
        return getAllRoles(subject).any { it in roles }
    }

    override suspend fun includeDirect(subject: BaseRoleSubject, role: BaseRole): Boolean {
        val included = locker.withWriteLock {
            originalRepo.includeDirect(subject, role)
        }

        if (included) {
            requestUpdateCache().join()
        }

        return included
    }

    override suspend fun includeDirect(subject: BaseRoleSubject, roles: List<BaseRole>): Boolean {
        val included = locker.withWriteLock {
            originalRepo.includeDirect(subject, roles)
        }

        if (included) {
            requestUpdateCache().join()
        }

        return included
    }

    override suspend fun excludeDirect(subject: BaseRoleSubject, role: BaseRole): Boolean {
        val excluded = locker.withWriteLock {
            originalRepo.excludeDirect(subject, role)
        }

        if (excluded) {
            requestUpdateCache().join()
        }

        return excluded
    }

    override suspend fun excludeDirect(subject: BaseRoleSubject, roles: List<BaseRole>): Boolean {
        val excluded = locker.withWriteLock {
            originalRepo.excludeDirect(subject, roles)
        }

        if (excluded) {
            requestUpdateCache().join()
        }

        return excluded
    }

    override suspend fun modifyDirect(subject: BaseRoleSubject, toExclude: List<BaseRole>, toInclude: List<BaseRole>): Boolean {
        val modified = locker.withWriteLock {
            originalRepo.modifyDirect(subject, toExclude, toInclude)
        }

        if (modified) {
            requestUpdateCache().join()
        }

        return modified
    }

    override suspend fun createRole(newRole: BaseRole): Boolean {
        val created = locker.withWriteLock {
            originalRepo.createRole(newRole)
        }

        if (created) {
            requestUpdateCache().join()
        }

        return created
    }

    override suspend fun removeRole(role: BaseRole): Boolean {
        val removed = locker.withWriteLock {
            originalRepo.removeRole(role)
        }

        if (removed) {
            requestUpdateCache().join()
        }

        return removed
    }

    override suspend fun getAllSubjectsByPagination(subject: BaseRoleSubject): Set<BaseRoleSubject> {
        return locker.withReadAcquire {
            when (subject) {
                is BaseRoleSubject.Direct -> emptySet()
                is BaseRoleSubject.OtherRole -> rolesSubjects[subject.role] ?.toSet() ?: emptySet()
            }
        }
    }

    override suspend fun invalidate() {
        requestUpdateCache().join()
    }
}