# kroles.repos.ktor

Shared HTTP protocol definitions for the kroles Ktor adapters: endpoint names, query-parameter names, serializable mutation bodies, and a subject-list serializer. The module exposes `RolesKtorConstants` from the package `dev.inmo.kroles.repos.repos.ktor` and depends on [kroles.repos](../README.md).

Most applications should use the [client](client/README.md) or [server](server/README.md) module, which each expose this dependency transitively. Depend on this module directly when implementing another transport adapter or sharing the protocol types without either adapter.

## Installation

The version below is the version declared in this repository's `gradle.properties`.

```kotlin
repositories {
    mavenCentral()
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.inmo:kroles.repos.ktor:0.0.4")
        }
    }
}
```

Targets: JVM, JavaScript (browser and Node.js), and Android. For a JVM-only project, use the same coordinate in its `dependencies` block.

## Paths and query parameters

`RolesKtorConstants.DefaultRolesRootPathPart` is `"roles"`. The full server configurator uses this as its default route prefix, so `GetDirectSubjectsPathPart` normally becomes `/roles/getSubjectsByRole`. Client and server must agree on the prefix; the constants below are relative path parts.

| Constant | Value | Use |
| --- | --- | --- |
| `RoleQueryParameterName` | `role` | Role identifier; repeated for `containsAny` |
| `SubjectIdentifierQueryParameterName` | `subject_identifier` | A `BaseRoleSubject.Direct` identifier |
| `SubjectRoleQueryParameterName` | `subject_role` | A `BaseRoleSubject.OtherRole` role identifier |

For subject-based reads, supply one of the two subject parameters. The server prefers `subject_role` when both are supplied and returns HTTP 400 when neither is present.

| Constant | Path part | Method / result |
| --- | --- | --- |
| `GetDirectSubjectsPathPart` | `getSubjectsByRole` | GET: direct subjects of `role` |
| `GetDirectRolesPathPart` | `getDirectSubjectRoles` | GET: direct roles of a subject |
| `GetAllPathPart` | `getAll` | GET: all subject-to-roles mappings; see compatibility note below |
| `GetAllRolesPathPart` | `getAllRoles` | GET: direct and inherited roles of a subject |
| `GetAllRolesByPaginationPathPart` | `getRolesPage` | GET: a `PaginationResult<BaseRole>` |
| `GetAllSubjectsRolesByPaginationPathPart` | `getSubjects` | GET: a `PaginationResult<BaseRoleSubject>` |
| `ContainsPathPart` | `contains` | GET: Boolean membership check using subject and `role` |
| `ContainsAnyPathPart` | `containsAny` | GET: Boolean membership check using subject and repeated `role` |
| `IncludeDirectPathPart` | `includeDirect` | POST: `IncludeExcludeWrapper` |
| `IncludeDirectsPathPart` | `includeDirects` | POST: `IncludesExcludesWrapper` |
| `ExcludeDirectPathPart` | `excludeDirect` | POST: `IncludeExcludeWrapper` |
| `ExcludeDirectsPathPart` | `excludeDirects` | POST: `IncludesExcludesWrapper` |
| `ModifyDirectPathPart` | `modifyDirect` | POST: `ModifyWrapper` |
| `CreateRolePathPart` | `createRole` | POST: `BaseRole` |
| `RemoveRolePathPart` | `removeRole` | POST: `BaseRole` |
| `RoleIncludedFlowPathPart` | `roleIncludedFlow` | WebSocket: `Pair<BaseRoleSubject, BaseRole>` events |
| `RoleExcludedFlowPathPart` | `roleExcludedFlow` | WebSocket: `Pair<BaseRoleSubject, BaseRole>` events |
| `RoleCreatedFlowPathPart` | `roleCreatedFlow` | WebSocket: `BaseRole` events |
| `RoleRemovedFlowPathPart` | `roleRemovedFlow` | WebSocket: `BaseRole` events |

All mutation endpoints return the repository's Boolean result. Paginated endpoints use the MicroUtils pagination helpers and its `reversed` query parameter.

## Request types

The wrapper types are nested in `RolesKtorConstants` and annotated with `@Serializable`:

- `IncludeExcludeWrapper(subject, role)` carries one subject and one role.
- `IncludesExcludesWrapper(subject, roles)` carries one subject and several roles.
- `ModifyWrapper(subject, toExclude, toInclude)` carries the roles to remove and add.

For example, a custom adapter can reuse the same path and body type:

```kotlin
import dev.inmo.kroles.repos.BaseRoleSubject
import dev.inmo.kroles.repos.repos.ktor.RolesKtorConstants
import dev.inmo.kroles.roles.BaseRole

val path = "${RolesKtorConstants.DefaultRolesRootPathPart}/${RolesKtorConstants.IncludeDirectPathPart}"
val body = RolesKtorConstants.IncludeExcludeWrapper(
    subject = BaseRoleSubject.Direct("user-42"),
    role = BaseRole("editor"),
)
```

`RolesKtorConstants.RoleSubjectsSerializer` is a `ListSerializer(BaseRoleSubject.serializer())` for subject collections.

## Protocol considerations

This module provides types and names; the adapters use the application's configured Ktor serialization and WebSocket support. Use compatible serializers on both sides, including support for `BaseRoleSubject`'s polymorphic value-class implementations. The wrapper declarations alone do not establish a universal JSON configuration or WebSocket framing format; consult the [client](client/README.md) and [server](server/README.md) setup instructions.

The current `getAll` implementations disagree on the response type: the server sends `Map<BaseRoleSubject, List<BaseRole>>`, while the client expects `List<Pair<BaseRoleSubject, List<BaseRole>>>`. A shared serializer configuration alone does not resolve this difference; agree on a wire representation when implementing an adapter.

Query values need URL encoding. The bundled client explicitly encodes subject values and single-role arguments, but passes the repeated `role` values for `containsAny` to the URL builder without the same explicit encoding. Verify role names containing reserved characters when using that operation.

See the [project overview](../../README.md) and [repository API](../README.md) for the role model and repository behavior.
