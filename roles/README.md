# Roles

Serializable role values and read/write/manage (RWM) permission checks for Kotlin Multiplatform. This module defines the role model; [repos](../repos/README.md) adds subject assignments and role inheritance.

[Project overview](../README.md)

## Installation

Add the dependency to `commonMain` in your `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.inmo:kroles.roles:0.0.5")
        }
    }
}
```

## Role values

- `BaseRole("editor")` is an arbitrary string role; `.plain` exposes its value. `BaseRole.EMPTY` wraps an empty string.
- `RWMRole` wraps a `BaseRole` in the form `prefix.rights.identifier`, such as `documents.rw.42`. The identifier is optional: `documents.r` applies to all documents.
- `RWMRole.AccessRights` holds independent `r`, `w`, and `m` flags. Manage does not automatically imply read or write.
- `RWMRole.Identifier` wraps the identifier string. Construct roles with a string identifier and obtain the typed value through `.identifier`.

Standard RWM checks (`AccessRights.contains`, `RightsChecker.Default` and `AccessChecker.RWM`) use only the presence of lowercase `r`, `w` and `m` in the rights segment. Character order and repetitions do not matter; other characters such as `x` are ignored. Thus `rw`, `wr`, `rrw` and `wrx` all mean read and write access without manage access.

Kotlin `==` and hash codes for `AccessRights` use the access flags, so the rights parsed from `documents.rw` and `documents.wr` compare equal. The original rights string is preserved and serialized as a plain string without normalization.

`BaseRole`, `RWMRole`, `AccessRights`, and `Identifier` support Kotlin serialization. Build an RWM role with an explicit `AccessRights` value, then use `.role` when an API expects `BaseRole`:

```kotlin
import dev.inmo.kroles.roles.BaseRole
import dev.inmo.kroles.roles.rwm.RWMRole
import dev.inmo.kroles.roles.rwm.rwmRoleOrNull

val editor = BaseRole("editor")
val documentEditor = RWMRole(
    prefix = "documents",
    rights = RWMRole.AccessRights(read = true, write = true),
    identifier = "42"
)
val storedRole: BaseRole = documentEditor.role // documents.rw.42
val parsed: RWMRole? = storedRole.rwmRoleOrNull()
```

Parsing is also available through `RWMRole("documents.rw.42")` and the top-level `rwmRoleOrNull` / `rwmRoleOrThrow` functions. These parsers are permissive and do not validate a strict RWM grammar.

## Checking permissions

Checks are suspending functions. A matching role must have the exact prefix and pass the supplied `RightsChecker`. A supplied identifier requires an exact match; a null identifier accepts both scoped and unscoped roles.

```kotlin
import dev.inmo.kroles.roles.rwm.RWMRole
import dev.inmo.kroles.roles.rwm.RightsChecker
import dev.inmo.kroles.roles.rwm.getAllowedIdentifiers
import dev.inmo.kroles.roles.rwm.isAccessAllowed
import dev.inmo.kroles.roles.rwm.isIdentifierAllowed

suspend fun checkDocumentPermissions() {
    val documentEditor = RWMRole(
        prefix = "documents",
        rights = RWMRole.AccessRights(read = true, write = true),
        identifier = "42"
    )
    val documentReader = RWMRole(
        prefix = "documents",
        rights = RWMRole.AccessRights(read = true)
    )
    val roles = listOf(documentEditor.role, documentReader.role)
    val documentId = requireNotNull(documentEditor.identifier)

    val canRead = RightsChecker { it.r }
    val canWrite = RightsChecker { it.w }
    val canManage = RightsChecker { it.m }
    val canEdit = (canRead * canWrite) + canManage

    check(roles.isIdentifierAllowed("documents", documentId, canEdit))
    check(roles.isAccessAllowed("documents", canRead))
    check(roles.isAccessAllowed("documents", canWrite))
    check(roles.getAllowedIdentifiers("documents", canWrite) == listOf(documentId))
    check(roles.getAllowedIdentifiers("documents", canRead) == null)
}
```

The list helpers have these semantics:

| Helper | Behavior |
| --- | --- |
| `isIdentifierAllowed` | Requires a matching role with exactly the requested identifier; roles without an identifier do not qualify. |
| `isAccessAllowed` | Accepts a matching role with any identifier, including an absent one. |
| `getAllowedIdentifiers` | Returns matching identifiers, an empty list if none qualify, or `null` if a qualifying role has no identifier (unrestricted identifiers). |
| `includesBaseRole` | Uses permission checks for a parsed RWM role and exact list membership for other roles. |

`AccessChecker.check` checks a single role. Pass an `AccessChecker` to `roles.isAccessAllowed(checker)` to check a list, or to `repo.isAccessAllowed(subject, checker)` to check a subject's effective roles. `AccessChecker.RWM` checks the prefix, configured rights and optional identifier; `AccessChecker { role -> ... }` supports custom checks, including non-RWM roles. `RWMRole.checkRights` is deprecated.

List checks succeed if one role passes; they do not combine rights from different roles. For example, separate `documents.r.42` and `documents.w.42` roles do not satisfy `canRead * canWrite`.

## Composition

For `AccessRights`, `+` unions flags, `*` intersects flags, and `-` removes flags. For `RightsChecker`, `+` means OR, `*` means AND, and `!` negates a checker. Checker composition applies to the rights of a single role.

The default `RightsChecker` stores granted rights and accepts a request only when every requested flag is present in those grants. For example, `RightsChecker("r")("rw")` returns `false`, while `RightsChecker("rw")("r")` returns `true`. The list helpers pass each stored role's rights as the request to this checker; their string/flag overloads therefore accept roles whose flags are a subset of the supplied flags.
