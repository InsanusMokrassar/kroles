# kroles.repos

Coroutine-based repositories for assigning roles to subjects and following role hierarchies. This module provides read/write interfaces, a default in-memory adapter, storage adapters for MicroUtils repositories, subject protection policies, and a cache.

See the [project overview](../README.md), [role types and rights checks](../roles/README.md), and [Ktor integration](ktor/README.md).

## Installation

Add the dependency to a JVM/Android project or to `commonMain.dependencies` in a Kotlin Multiplatform project:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("dev.inmo:kroles.repos:0.0.4")
}
```

`0.0.4` is the version configured in this repository. The module targets JVM (Java 17), JavaScript IR (browser and Node.js), and Android (minimum SDK 21). It exposes `kroles.roles`, MicroUtils repository/cache APIs, and KSLog as API dependencies.

## Subjects and directed grants

`BaseRole` wraps a role's string value. `BaseRoleSubject` identifies the recipient of a grant:

| Subject | Meaning |
| --- | --- |
| `BaseRoleSubject.Direct("user:alice")` or `BaseRoleSubject("user:alice")` | An application-defined identifier, such as a user ID. |
| `BaseRoleSubject.OtherRole(editor)` or `BaseRoleSubject(editor)` | A role acting as a subject, allowing it to inherit other roles. |

`includeDirect(subject, role)` adds a directed edge from the subject to the role. Granting `reader` to `editor`, then `editor` to Alice, gives Alice both roles. It does not give every reader the editor role. A direct subject and a role subject remain distinct even when their string values match.

`KeyValueRolesRepo()` uses a MicroUtils `MapKeyValuesRepo` by default, so this example needs no database or additional in-memory module:

```kotlin
import dev.inmo.kroles.repos.BaseRoleSubject
import dev.inmo.kroles.repos.kv.roles.KeyValueRolesRepo
import dev.inmo.kroles.roles.BaseRole

suspend fun roleHierarchyExample() {
    val repo = KeyValueRolesRepo()
    val alice = BaseRoleSubject("user:alice")
    val editor = BaseRole("editor")
    val reader = BaseRole("reader")

    check(repo.includeDirect(BaseRoleSubject(editor), reader))
    check(repo.includeDirect(alice, editor))

    check(repo.getDirectRoles(alice) == listOf(editor))
    check(repo.getAllRoles(alice).toSet() == setOf(editor, reader))

    repo.excludeDirect(alice, editor)
    check(repo.getAllRoles(alice).isEmpty())
}
```

The class named `InMemoryRolesRepo` is currently an empty placeholder; use `KeyValueRolesRepo()` for an in-memory implementation.

## Reading and changing grants

Use `ReadRolesRepo` for consumers that only query grants, `WriteRolesRepo` for mutation, and `RolesRepo` when both are needed. Repository operations are suspending functions.

| Read operation | Result |
| --- | --- |
| `getDirectRoles(subject)` | Roles granted directly to the subject. |
| `getDirectSubjects(role)` | Subjects directly granted that role, including role subjects. |
| `getAll()` | A map of subjects to their directly granted roles. |
| `getAllRoles(subject)` | Distinct direct and inherited roles. |
| `getAllSubjectsByPagination(BaseRoleSubject(role))` | Subjects reached by following grants backwards from a role, including intermediate role subjects. Despite the name, this overload returns a set, not a page. |
| `getDirects(role)` | An extension returning only the concrete identifiers reached by that reverse traversal. |
| `getAllRolesByPagination(pagination, reversed)` / `getAllRoles()` | A page of known roles / all such pages combined. |
| `getAllSubjectsByPagination(pagination, reversed)` | A page of known subjects. |

Pagination uses MicroUtils `Pagination` and `PaginationResult`. Import extensions such as `getDirects` from `dev.inmo.kroles.repos` when needed. `getAllRoles` also has convenience extensions accepting a `BaseRole` or a direct identifier string.

**Membership checks differ by implementation.** `ReadKeyValueRolesRepo` and `KeyValueRolesRepo` implement `contains(subject, role)` and `containsAny(subject, roles)` as direct membership checks. `CacheRolesRepo` includes inherited roles. Use `role in repo.getAllRoles(subject)` when inheritance must be checked consistently across these implementations. The `contains(identifier: String, role)` extension also traverses inherited roles, while the corresponding `containsAny` extension delegates to the implementation.

`includeDirect` and `excludeDirect` grant and revoke edges; `createRole` and `removeRole` manage registered roles. `WriteRolesRepo` exposes `roleIncluded` and `roleExcluded` flows carrying `(subject, role)` pairs, and `roleCreated` and `roleRemoved` flows carrying roles.

The list overloads of `includeDirect` and `excludeDirect` attempt every item and return `true` only if every call returns `true`. The default `modifyDirect(subject, toExclude, toInclude)` excludes first and skips inclusion if exclusion returns `false`; it is not a transaction. For the key-value adapter, duplicate inclusion returns `false`, while exclusion reports success when the storage call completes, even if no grant existed. Storage failures in these write operations are logged and reported as `false`.

## MicroUtils storage adapters

Grant storage is a **key-values** repository: each `BaseRoleSubject` maps to multiple `BaseRole` values. Supply your own backend through the `keyValuesRepo` constructor parameter:

| Adapter in `dev.inmo.kroles.repos.kv.roles` | Required backend |
| --- | --- |
| `ReadKeyValueRolesRepo` | `ReadKeyValuesRepo<BaseRoleSubject, BaseRole>` |
| `WriteKeyValueRolesRepo` | `KeyValuesRepo<BaseRoleSubject, BaseRole>`; writes also need membership queries. |
| `KeyValueRolesRepo` | `KeyValuesRepo<BaseRoleSubject, BaseRole>`, defaulting to `MapKeyValuesRepo()`. |

These adapters keep storage selection outside the role model. Persistent backends and their dependencies must be supplied by the application.

Role registration has observable storage details: `createRole(role)` grants `BaseRole.EMPTY` to `BaseRoleSubject(role)`. That empty marker can appear in direct and inherited query results. Registration is optional for assigning a role. The key-value adapter's global role listing enumerates role subjects stored as keys; a role used only as a grant value is not necessarily listed. The cache instead enumerates role nodes in the full graph, including roles appearing only as values.

`removeRole` clears the role's own grants and removes grants of that role to other subjects. In the key-value implementation it bypasses subject protection and emits `roleRemoved`, without individual `roleExcluded` events. Direct changes to the underlying MicroUtils store also do not emit the role adapter's events.

## Protection policies

`ReadProtectedRolesRepo` supplies `allowInclude(subject)` and `allowExclude(subject)`. Pass it to `KeyValueRolesRepo` or `WriteKeyValueRolesRepo` to control direct grant/revoke operations. The default `AlwaysUnprotected` permits both.

For example, an application can freeze one subject's direct grants:

```kotlin
import dev.inmo.kroles.repos.BaseRoleSubject
import dev.inmo.kroles.repos.ReadProtectedRolesRepo
import dev.inmo.kroles.repos.kv.roles.KeyValueRolesRepo

fun repositoryWithFrozenSubject(frozen: BaseRoleSubject): KeyValueRolesRepo {
    val policy = object : ReadProtectedRolesRepo {
        override suspend fun allowInclude(subject: BaseRoleSubject) = subject != frozen
        override suspend fun allowExclude(subject: BaseRoleSubject) = subject != frozen
    }
    return KeyValueRolesRepo(protectedRolesRepo = policy)
}
```

This policy applies to changes targeting that subject. Changes to roles it inherits can still alter its effective permissions, and `removeRole` bypasses this policy as described above. The policy does not identify or authorize the caller making a request; caller authorization belongs in the application or [Ktor server integration](ktor/server/README.md).

`WriteProtectedRolesRepo` adds `protect(subject, allowInclude = true)` and `unprotect(subject)`. `KeyValueProtectedRolesRepo` combines reads and writes over a MicroUtils **key-value** `KeyValueRepo<BaseRoleSubject, Boolean>`; separate `ReadKeyValueProtectedRolesRepo` and `WriteKeyValueProtectedRolesRepo` adapters accept the corresponding read/write backends. These classes live in `dev.inmo.kroles.repos.kv.protected_roles`.

The current key-value policy's actual behavior is determined by the stored flag:

| Stored state | `allowInclude` | `allowExclude` |
| --- | --- | --- |
| No entry, including after `unprotect(subject)` | `false` | `false` |
| `true`, including after `protect(subject)` | `true` | `true` |
| `false`, after `protect(subject, allowInclude = false)` | `false` | `true` |

These results differ from what the protection method names may suggest. Use a custom `ReadProtectedRolesRepo` when the required policy is “protected subjects cannot lose roles.”

## Caching

`CacheRolesRepo` wraps a `RolesRepo`, precomputes direct and inherited lookups, and delegates writes to the wrapped repository. It requires an application-owned `CoroutineScope` and MicroUtils `SmartRWLocker`. It implements MicroUtils `CacheRepo`; `invalidate()` requests a full rebuild and waits for it.

```kotlin
import dev.inmo.kroles.repos.CacheRolesRepo
import dev.inmo.kroles.repos.RolesRepo
import dev.inmo.micro_utils.coroutines.SmartRWLocker
import kotlinx.coroutines.CoroutineScope

suspend fun createInitializedCache(
    original: RolesRepo,
    scope: CoroutineScope,
    locker: SmartRWLocker
): CacheRolesRepo {
    val cache = CacheRolesRepo(original, scope, locker)
    cache.invalidate()
    return cache
}
```

Initialization and event subscriptions run in the supplied scope. Keep it active for the cache's lifetime and await `invalidate()` before relying on initial reads. Successful writes through the cache request a refresh before returning; changes observed through the original repository's flows are refreshed asynchronously. Call `invalidate()` after changes that bypass those flows. The optional `onIdentifierRemovedFlow` removes the emitted identifier's direct grants through the repository.

Keep the cached hierarchy acyclic: `GraphNode.allChildren` and `allParents`, used during refresh, recursively traverse without cycle detection. Refresh failures are logged and retried; callers waiting for a successful refresh can remain suspended. Another implementation difference is that `getAllSubjectsByPagination(directSubject)` returns an empty set in the cache, while the default interface traversal returns the direct subject itself.

## Resource rights helpers

Extensions in `dev.inmo.kroles.repos` apply the [RWM role model](../roles/README.md) to a subject's effective roles: `isIdentifierAllowed`, `isAccessAllowed`, `getAllowedIdentifiers`, and `includesBaseRole`. `getAllowedIdentifiers` returns `null` for unrestricted access under a prefix. Read the role module's caveats before choosing rights constructors and checks.

The `RolesRepo` mutation extensions accepting `RWMRole` modify the rights of existing direct roles with matching prefixes and identifiers. They do not create a new resource-role entry for an unseen prefix/identifier. To assign an initial RWM role, pass its `.role` (`BaseRole`) to the ordinary `includeDirect` operation.
