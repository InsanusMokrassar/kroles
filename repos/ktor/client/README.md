# Ktor client

`kroles.repos.ktor.client` implements the [roles repository interfaces](../../README.md) over HTTP and WebSockets. It supports JVM, JavaScript (browser and Node.js), and Android.

| Implementation | Repository interface | Operations |
| --- | --- | --- |
| `ReadKtorRolesRepo` | `ReadRolesRepo` | HTTP queries |
| `WriteKtorRolesRepo` | `WriteRolesRepo` | HTTP mutations and WebSocket event flows |
| `KtorRolesRepo` | `RolesRepo` | Both read and write operations |

All three classes are in **`dev.inmo.kroles.repos.repos.ktor.client`**, including the repeated `repos` segment. They use a caller-supplied `HttpClient` and do not own its lifecycle.

## Installation

Add the library to an existing Kotlin Multiplatform project. This JVM example also declares the dependencies used by the client configuration below:

```kotlin
repositories {
    mavenCentral()
}

// Matches Ktor in the MicroUtils 0.32.0 dependency used by this checkout.
val ktorVersion = "3.6.0"

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.inmo:kroles.repos.ktor.client:0.0.4")
            implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
            implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
            implementation("io.ktor:ktor-client-websockets:$ktorVersion")
        }
        jvmMain.dependencies {
            implementation("io.ktor:ktor-client-cio:$ktorVersion")
        }
    }
}
```

Keep all Ktor dependencies on the same version. For JavaScript or Android, supply an engine for that target; event collection requires an engine with WebSocket support. See the [project README](../../../README.md) for the module overview and build setup.

## Configure the HTTP client

Configure HTTP content negotiation, a default JSON request content type, and a WebSocket content converter. `WriteKtorRolesRepo` and `KtorRolesRepo` require the `WebSockets` plugin even when you only intend to call mutation methods; `ReadKtorRolesRepo` does not.

```kotlin
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

val rolesJson = Json {
    useArrayPolymorphism = true
    allowStructuredMapKeys = true
}

fun createRolesHttpClient(): HttpClient = HttpClient(CIO) {
    expectSuccess = true
    install(ContentNegotiation) {
        json(rolesJson)
    }
    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(rolesJson)
    }
    defaultRequest {
        contentType(ContentType.Application.Json)
    }
}
```

Use the same JSON configuration on the [server](../server/README.md). `BaseRoleSubject` has serializable value-class variants: array polymorphism preserves whether a string identifies a direct subject or a role subject. For example, `BaseRoleSubject.Direct("alice")` is encoded as `["CommonSubject", "alice"]`. Default JSON does not round-trip these subjects. Structured map keys allow the server's subject-keyed maps to be serialized, but do not resolve the `getAll()` mismatch described below.

## Query and mutate roles

Pass an absolute HTTP base URL containing the server's mounted route, such as `https://example.com/roles`, without a trailing slash. The constructor default is only `"roles"`; it does not identify a remote server. The [protocol module](../README.md) defines the paths appended to this base.

```kotlin
import dev.inmo.kroles.repos.BaseRoleSubject
import dev.inmo.kroles.repos.repos.ktor.client.KtorRolesRepo
import dev.inmo.kroles.roles.BaseRole
import io.ktor.client.HttpClient

suspend fun grantEditor(client: HttpClient) {
    val repo = KtorRolesRepo(client, "https://example.com/roles")
    val alice = BaseRoleSubject.Direct("alice")
    val editor = BaseRole("editor")
    val reader = BaseRole("reader")

    repo.createRole(editor)
    repo.createRole(reader)
    repo.includeDirect(BaseRoleSubject.OtherRole(editor), reader)
    repo.includeDirect(alice, editor)

    println(repo.getDirectRoles(alice)) // Direct grants.
    println(repo.getAllRoles(alice))    // Includes inherited roles.
    println(repo.contains(alice, reader))

    repo.excludeDirect(alice, editor)
}
```

Every query and mutation is a suspending function. The server's backing repository determines the results; the client forwards mutation `Boolean` values without changing their meaning.

The read API also provides `getDirectSubjects`, `containsAny`, `getAllRolesByPagination`, and `getAllSubjectsByPagination`. The pagination methods take a MicroUtils `Pagination` and an optional `reversed` flag. The write API includes single-role and list overloads of `includeDirect` and `excludeDirect`, `modifyDirect(subject, toExclude, toInclude)`, and `removeRole`. See the [repository documentation](../../README.md) for their contracts.

## Observe changes

| Flow | Event type |
| --- | --- |
| `roleIncluded` | `Pair<BaseRoleSubject, BaseRole>` |
| `roleExcluded` | `Pair<BaseRoleSubject, BaseRole>` |
| `roleCreated` | `BaseRole` |
| `roleRemoved` | `BaseRole` |

The current WebSocket helper preserves the URL scheme. Use a separate `WriteKtorRolesRepo` with a `ws://` or `wss://` root for event collection, and use the HTTP-root repository above for queries and mutations. For an HTTPS API at `https://example.com/roles`, its event root is `wss://example.com/roles`:

```kotlin
import dev.inmo.kroles.repos.repos.ktor.client.WriteKtorRolesRepo
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

fun observeGrants(client: HttpClient, scope: CoroutineScope): Job {
    val events = WriteKtorRolesRepo(client, "wss://example.com/roles")
    return scope.launch {
        events.roleIncluded.collect { (subject, role) ->
            println("${subject.rawValue} received ${role.plain}")
        }
    }
}
```

Each collection opens a WebSocket and the underlying helper reconnects while collection remains active. These are live events; there is no replay or snapshot synchronization protocol. Cancel and join the observing jobs when they are no longer needed, then call `client.close()` when its owning component shuts down. Installing HTTP JSON support alone does not configure WebSocket serialization.

## Current interoperability limitations

- `getAll()` decodes a list of subject/roles pairs, while the bundled server responds with a map. Avoid this call with the bundled server until the wire formats are aligned; query individual subjects or use subject pagination and `getDirectRoles` instead.
- `containsAny()` does not encode individual role query values. For role names containing URL-sensitive characters such as `&` or `#`, call `contains(subject, role)` for each candidate and combine the results.
- An HTTP-root repository cannot collect its event flows with the current helper. Use the separate WebSocket-root instance shown above.
