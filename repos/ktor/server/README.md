# kroles.repos.ktor.server

JVM Ktor routes for a coroutine-based `RolesRepo`, with HTTP queries and mutations plus WebSocket change events. The application supplies the repository, server engine, serialization, and caller authorization.

See the [project overview](../../../README.md), [repository API](../../README.md), [shared protocol](../README.md), and matching [Ktor client](../client/README.md).

## Installation

Add the module to a Kotlin/JVM application. `0.0.4` is the version configured in this repository; the server module targets JVM with Java 17.

```kotlin
repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("dev.inmo:kroles.repos.ktor.server:0.0.4")

    val ktorVersion = "3.6.0"
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
}
```

Ktor `3.6.0` matches the module's MicroUtils `0.32.0` dependency. Keep Ktor artifacts aligned when integrating into an existing application. The module exposes `kroles.repos.ktor` and MicroUtils server helpers transitively, but does not select or start a server engine for you.

## Start a server

The route functions live in `dev.inmo.kroles.repos.ktor.repos.ktor.server`, including the repeated `repos.ktor` segments.

```kotlin
import dev.inmo.kroles.repos.RolesRepo
import dev.inmo.kroles.repos.kv.roles.KeyValueRolesRepo
import dev.inmo.kroles.repos.ktor.repos.ktor.server.configureRolesRepoRoutes
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import kotlinx.serialization.json.Json

val rolesJson = Json {
    useArrayPolymorphism = true
    allowStructuredMapKeys = true
}

fun Application.rolesModule(repo: RolesRepo) {
    install(ContentNegotiation) {
        json(rolesJson)
    }
    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(rolesJson)
    }
    routing {
        configureRolesRepoRoutes(repo)
    }
}

fun main() {
    val repo = KeyValueRolesRepo()
    embeddedServer(Netty, host = "127.0.0.1", port = 8080) {
        rolesModule(repo)
    }.start(wait = true)
}
```

`KeyValueRolesRepo()` provides in-memory storage and permits direct grant/revoke operations by default. Inject your application's `RolesRepo` instead to choose persistence and repository policies. Repository methods suspend; each HTTP handler calls the corresponding method and returns its result.

Use the same JSON configuration on the client. `BaseRoleSubject` is a sealed interface with value-class implementations; array polymorphism preserves their subtype names, for example `["CommonSubject","user-42"]`. Default `Json` does not round-trip these subjects. Structured map keys allow the server's `getAll` map to be encoded, but do not resolve the client/server response mismatch described below. HTTP content negotiation and the WebSocket converter must both be configured.

## Route placement

`configureRolesRepoRoutes(repo)` registers reads, mutations, and WebSocket endpoints under `"roles"`. The example therefore exposes URLs such as `http://127.0.0.1:8080/roles/getDirectSubjectRoles` and `ws://127.0.0.1:8080/roles/roleIncludedFlow`.

Pass a different `rootPath` to change that prefix. Pass `null` to register directly on the current route:

```kotlin
import dev.inmo.kroles.repos.RolesRepo
import dev.inmo.kroles.repos.ktor.repos.ktor.server.configureRolesRepoRoutes
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

fun Route.customRolesRoutes(repo: RolesRepo) {
    route("api/access") {
        configureRolesRepoRoutes(repo, rootPath = null)
    }
}
```

This puts the endpoints under `/api/access`, without adding `/roles`. The client's `rootPath` must include whichever prefix you choose.

For separate route groups, use the read and write configurators. Neither adds a prefix itself:

```kotlin
import dev.inmo.kroles.repos.ReadRolesRepo
import dev.inmo.kroles.repos.WriteRolesRepo
import dev.inmo.kroles.repos.ktor.repos.ktor.server.configureReadRolesRepoRoutes
import dev.inmo.kroles.repos.ktor.repos.ktor.server.configureWriteRolesRepoRoutes
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

fun Route.splitRolesRoutes(readRepo: ReadRolesRepo, writeRepo: WriteRolesRepo) {
    route("roles/read") {
        configureReadRolesRepoRoutes(readRepo)
    }
    route("roles/write") {
        configureWriteRolesRepoRoutes(writeRepo)
    }
}
```

Use a read client rooted at `/roles/read` and a write client rooted at `/roles/write` for that layout. A single combined client expects a shared prefix.

The write configurator registers all four event WebSockets. `roleCreated` and `roleRemoved` emit `BaseRole`; `roleIncluded` and `roleExcluded` emit `Pair<BaseRoleSubject, BaseRole>`. These are the supplied repository's coroutine `Flow` streams, forwarded using the configured WebSocket serializer. The adapter adds no snapshot or event history; use read endpoints to obtain current state. A read-only setup can omit the WebSockets plugin.

Endpoint names, request wrappers, query parameters, and pagination are listed in the [shared protocol documentation](../README.md).

## Caller authorization and permission helpers

The configurators install no authentication or caller permission checks. Apply your application's authentication and authorization policy around the roles routes, including their WebSockets. A subject in a query or request body identifies whose roles are being queried or changed; it does not establish the caller's identity. Repository protection policies also do not authenticate callers.

The `ApplicationCall` helpers can check resource permissions in your own handlers. This example takes an application-provided function that resolves an already authenticated caller to its subject:

```kotlin
import dev.inmo.kroles.repos.BaseRoleSubject
import dev.inmo.kroles.repos.ReadRolesRepo
import dev.inmo.kroles.repos.ktor.repos.ktor.server.isAccessAllowedOrStatus
import dev.inmo.kroles.roles.rwm.RightsChecker
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.documentRoute(
    repo: ReadRolesRepo,
    authenticatedSubject: suspend (ApplicationCall) -> BaseRoleSubject,
) {
    get("documents") {
        val subject = authenticatedSubject(call)
        if (!call.isAccessAllowedOrStatus(
                repo = repo,
                prefix = "documents",
                subject = subject,
                rightsChecker = RightsChecker { it.r },
                statusCode = HttpStatusCode.Forbidden,
            )) {
            return@get
        }
        call.respondText("Read access granted")
    }
}
```

These helpers use the subject's effective roles through `getAllRoles(subject)` and return `true` when allowed. On rejection, they send a response and return `false`; return from your handler immediately to avoid sending another response. `isIdentifierAllowedOrStatus` also checks a supplied `RWMRole.Identifier` for resource-specific access.

The example uses an explicit `RightsChecker` predicate because the current string/flag-based checker constructors rely on the rights-containment behavior documented in the [roles module](../../../roles/README.md). The flag overload of `isAccessAllowedOrRespondNotAllowed` also forwards `write` and `manage` in swapped positions. Prefer an explicit predicate such as `RightsChecker { it.r && it.w }` when those flags matter.

Default rejection statuses vary by overload:

| Helper | Default rejection status |
| --- | --- |
| `isIdentifierAllowedOrStatus` | `204 No Content` |
| `isIdentifierAllowedOrNoContent` | `204 No Content` |
| `isAccessAllowedOrStatus` with `RightsChecker` | `204 No Content` |
| `isAccessAllowedOrStatus` with `requiredRights: String` | `405 Method Not Allowed` |
| `isAccessAllowedOrRespondNotAllowed` | `405 Method Not Allowed` |

Pass an explicit status, as above, when your application needs a different response. These defaults are unrelated to the HTTP mutation endpoints' Boolean repository results.

## Current compatibility limits

The server's `getAll` endpoint responds with `Map<BaseRoleSubject, List<BaseRole>>`, while the bundled client decodes `List<Pair<BaseRoleSubject, List<BaseRole>>>`. Those representations are incompatible with the JSON setup above. Other read operations can be used individually; a shared JSON configuration alone cannot repair `getAll`.

Query and mutation semantics come from the supplied repository. For example, `contains` and `containsAny` check only direct grants in `KeyValueRolesRepo`, whereas `getAllRoles(subject)` includes inherited roles. See the [repository documentation](../../README.md) for implementation differences and write-result semantics.
