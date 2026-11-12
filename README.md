# kroles

kroles is a Kotlin Multiplatform library for modelling roles, assigning them to subjects, and checking access through role hierarchies. It provides plain string roles, read/write/manage permissions, repository interfaces and implementations, and Ktor client/server integration.

## Installation

Add Maven Central and the module you need. For a Kotlin Multiplatform project, put the dependency in `commonMain`:

```kotlin
repositories {
    mavenCentral()
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.inmo:kroles.repos:0.0.4")
        }
    }
}
```

In a Kotlin/JVM project, use the same coordinate in the regular `dependencies` block:

```kotlin
dependencies {
    implementation("dev.inmo:kroles.repos:0.0.4")
}
```

The examples use `0.0.4`, the version in [gradle.properties](gradle.properties). All library artifacts use the `dev.inmo` group. Choose an artifact from the table below; higher-level modules expose their kroles dependencies transitively, so `kroles.repos` already includes `kroles.roles`.

## Modules

| Module and documentation | Artifact | Purpose | Targets |
| --- | --- | --- | --- |
| [Roles](roles/README.md) | `kroles.roles` | Role values, read/write/manage permissions, and permission checkers | JVM, JS, Android |
| [Repositories](repos/README.md) | `kroles.repos` | Subjects, role inheritance, key-value storage, caching, and write protection | JVM, JS, Android |
| [Ktor protocol](repos/ktor/README.md) | `kroles.repos.ktor` | Shared route names, query parameters, and serializable request bodies | JVM, JS, Android |
| [Ktor client](repos/ktor/client/README.md) | `kroles.repos.ktor.client` | Remote repository access through HTTP and WebSocket event flows | JVM, JS, Android |
| [Ktor server](repos/ktor/server/README.md) | `kroles.repos.ktor.server` | Repository routes and access-check response helpers | JVM |

JS modules target browsers and Node.js. JVM builds use JDK 17; Android modules have a minimum SDK of 21. Add the server dependency to a JVM project or a Multiplatform project's `jvmMain` source set.

## Assign roles and check access

A `BaseRole` wraps a string. A `BaseRoleSubject` is either a direct identifier, such as a user ID, or another role. Granting a permission to a role lets its holders inherit that permission.

`KeyValueRolesRepo()` provides an in-memory repository using the default MicroUtils map store. Repository operations are suspending functions:

```kotlin
import dev.inmo.kroles.repos.BaseRoleSubject
import dev.inmo.kroles.repos.isAccessAllowed
import dev.inmo.kroles.repos.kv.roles.KeyValueRolesRepo
import dev.inmo.kroles.roles.BaseRole
import dev.inmo.kroles.roles.rwm.RWMRole
import dev.inmo.kroles.roles.rwm.RightsChecker

suspend fun main() {
    val repo = KeyValueRolesRepo()
    val alice = BaseRoleSubject("alice")
    val reader = BaseRole("reader")
    val readArticles = RWMRole(
        prefix = "articles",
        rights = RWMRole.AccessRights(read = true)
    ).role

    // A reader can read articles; Alice is a reader.
    check(repo.includeDirect(BaseRoleSubject(reader), readArticles))
    check(repo.includeDirect(alice, reader))

    check(repo.getDirectRoles(alice) == listOf(reader))
    check(readArticles in repo.getAllRoles(alice))
    check(repo.isAccessAllowed(alice, "articles", RightsChecker { it.r }))

    check(repo.excludeDirect(alice, reader))
    check(!repo.isAccessAllowed(alice, "articles", RightsChecker { it.r }))
}
```

Use `getDirectRoles(subject)` for immediate grants and `getAllRoles(subject)` for inherited grants. The key-value implementation's `contains(subject, role)` checks direct grants; use `role in repo.getAllRoles(subject)` when inheritance matters.

`RWMRole` represents `prefix.rights[.identifier]`, for example `articles.rw.42`. The flags are `r` (read), `w` (write), and `m` (manage); an optional identifier scopes the permission to an entity. The example uses explicit `AccessRights` and a custom `RightsChecker`. See the [roles guide](roles/README.md) for scoped permissions, checker composition, and current convenience-overload limitations.

## Integrations

**MicroUtils repositories.** `KeyValueRolesRepo` accepts a `KeyValuesRepo<BaseRoleSubject, BaseRole>`, allowing an application to supply its storage backend. Separate read/write adapters and subject protection policies are available. `CacheRolesRepo` keeps a snapshot of a backing repository's hierarchy and refreshes it after changes. See the [repository guide](repos/README.md) for setup and differences between implementations.

**Ktor server.** Register `configureRolesRepoRoutes(repo)` under your application's routing to expose the repository at `/roles`. Read and write routes can also be registered separately. The application configures serialization, WebSockets, authentication, and authorization. Access-check helpers are available for application routes. See the [server guide](repos/ktor/server/README.md).

**Ktor client.** `KtorRolesRepo(client, rootPath)` implements the same `RolesRepo` interface using a configured `HttpClient`. Queries and mutations use HTTP; `roleIncluded`, `roleExcluded`, `roleCreated`, and `roleRemoved` expose WebSocket event streams. See the [client guide](repos/ktor/client/README.md) for dependencies, client configuration, and current interoperability limitations.

**Kotlin serialization and coroutines.** Role values, subjects, and Ktor request wrappers are serializable. Repository calls use `suspend`, while change notifications use `Flow`. The [shared Ktor protocol](repos/ktor/README.md) documents the route and payload contract for integrating other clients.

## Project links

- [Changelog](CHANGELOG.md)
- [MIT license](LICENSE)
