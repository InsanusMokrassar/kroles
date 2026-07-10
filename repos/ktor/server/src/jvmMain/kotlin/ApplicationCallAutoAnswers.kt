package dev.inmo.kroles.repos.ktor.repos.ktor.server

import dev.inmo.kroles.repos.*
import dev.inmo.kroles.roles.rwm.RWMRole
import dev.inmo.kroles.roles.rwm.RightsChecker
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

/**
 * Checks whether [subject] is allowed on [identifier] under [prefix] according to [accessChecker],
 * and responds with [statusCode] when it is not.
 *
 * @return `true` when access is allowed; otherwise `false` (after responding with [statusCode]).
 */
suspend fun ApplicationCall.isIdentifierAllowedOrStatus(
    repo: ReadRolesRepo,
    prefix: String,
    subject: BaseRoleSubject,
    identifier: RWMRole.Identifier,
    statusCode: HttpStatusCode = HttpStatusCode.NoContent,
    accessChecker: RightsChecker
): Boolean {
    val result = repo.isIdentifierAllowed(
        subject = subject,
        prefix = prefix,
        identifier = identifier,
        accessCheck = accessChecker
    )

    if (!result) {
        respond(statusCode)
    }

    return result
}

/**
 * Overload of [isIdentifierAllowedOrStatus] taking the [rightsChecker] before the [statusCode].
 *
 * @return `true` when access is allowed; otherwise `false` (after responding with [statusCode]).
 */
suspend fun ApplicationCall.isIdentifierAllowedOrStatus(
    repo: ReadRolesRepo,
    prefix: String,
    subject: BaseRoleSubject,
    identifier: RWMRole.Identifier,
    rightsChecker: RightsChecker,
    statusCode: HttpStatusCode = HttpStatusCode.NoContent
): Boolean = isIdentifierAllowedOrStatus(
    repo = repo,
    prefix = prefix,
    subject = subject,
    identifier = identifier,
    statusCode = statusCode,
    accessChecker = rightsChecker
)

/**
 * Overload of [isIdentifierAllowedOrStatus] that builds the rights check from a [requiredRights]
 * string.
 *
 * @return `true` when access is allowed; otherwise `false` (after responding with [statusCode]).
 */
suspend fun ApplicationCall.isIdentifierAllowedOrStatus(
    repo: ReadRolesRepo,
    prefix: String,
    subject: BaseRoleSubject,
    identifier: RWMRole.Identifier,
    requiredRights: String,
    statusCode: HttpStatusCode = HttpStatusCode.NoContent
): Boolean = isIdentifierAllowedOrStatus(
    repo = repo,
    prefix = prefix,
    subject = subject,
    identifier = identifier,
    rightsChecker = RightsChecker(requiredRights),
    statusCode = statusCode
)

/**
 * Convenience form of [isIdentifierAllowedOrStatus] that responds with `NoContent` when [subject] is
 * not allowed on [identifier] under [prefix] for the given [rightsChecker].
 *
 * @return `true` when access is allowed; otherwise `false` (after responding with `NoContent`).
 */
suspend fun ApplicationCall.isIdentifierAllowedOrNoContent(
    repo: ReadRolesRepo,
    prefix: String,
    subject: BaseRoleSubject,
    identifier: RWMRole.Identifier,
    rightsChecker: RightsChecker
): Boolean = isIdentifierAllowedOrStatus(
    repo = repo,
    prefix = prefix,
    subject = subject,
    identifier = identifier,
    rightsChecker = rightsChecker
)

/**
 * Overload of [isIdentifierAllowedOrNoContent] that builds the rights check from a [requiredRights]
 * string and responds with `NoContent` when access is denied.
 *
 * @return `true` when access is allowed; otherwise `false` (after responding with `NoContent`).
 */
suspend fun ApplicationCall.isIdentifierAllowedOrNoContent(
    repo: ReadRolesRepo,
    prefix: String,
    subject: BaseRoleSubject,
    identifier: RWMRole.Identifier,
    requiredRights: String
): Boolean = isIdentifierAllowedOrStatus(
    repo = repo,
    prefix = prefix,
    subject = subject,
    identifier = identifier,
    requiredRights = requiredRights
)

/**
 * Overload of [isIdentifierAllowedOrNoContent] that assembles the rights check from the individual
 * [read], [manage] and [write] flags and responds with `NoContent` when access is denied.
 *
 * @return `true` when access is allowed; otherwise `false` (after responding with `NoContent`).
 */
suspend fun ApplicationCall.isIdentifierAllowedOrNoContent(
    repo: ReadRolesRepo,
    prefix: String,
    subject: BaseRoleSubject,
    identifier: RWMRole.Identifier,
    read: Boolean = false,
    manage: Boolean = false,
    write: Boolean = false
): Boolean = isIdentifierAllowedOrNoContent(
    repo = repo,
    prefix = prefix,
    subject = subject,
    identifier = identifier,
    rightsChecker = RightsChecker(
        read = read,
        write = write,
        manage = manage
    ),
)


/**
 * Checks whether [subject] has access under [prefix] according to [rightsChecker], and responds with
 * [statusCode] when it does not.
 *
 * @return `true` when access is allowed; otherwise `false` (after responding with [statusCode]).
 */
suspend fun ApplicationCall.isAccessAllowedOrStatus(
    repo: ReadRolesRepo,
    prefix: String,
    subject: BaseRoleSubject,
    statusCode: HttpStatusCode = HttpStatusCode.NoContent,
    rightsChecker: RightsChecker
): Boolean {
    val result = repo.isAccessAllowed(subject, prefix, rightsChecker)

    if (!result) {
        respond(statusCode)
    }

    return result
}

/**
 * Overload of [isAccessAllowedOrStatus] that builds the rights check from a [requiredRights] string
 * and defaults to responding with `MethodNotAllowed` when access is denied.
 *
 * @return `true` when access is allowed; otherwise `false` (after responding with [statusCode]).
 */
suspend fun ApplicationCall.isAccessAllowedOrStatus(
    repo: ReadRolesRepo,
    prefix: String,
    subject: BaseRoleSubject,
    requiredRights: String,
    statusCode: HttpStatusCode = HttpStatusCode.MethodNotAllowed
): Boolean = isAccessAllowedOrStatus(
    repo = repo,
    prefix = prefix,
    subject = subject,
    statusCode = statusCode,
    rightsChecker = RightsChecker(requiredRights)
)

/**
 * Convenience form of [isAccessAllowedOrStatus] that responds with `MethodNotAllowed` when [subject]
 * has no access under [prefix] for the given [rightsChecker].
 *
 * @return `true` when access is allowed; otherwise `false` (after responding with `MethodNotAllowed`).
 */
suspend fun ApplicationCall.isAccessAllowedOrRespondNotAllowed(
    repo: ReadRolesRepo,
    prefix: String,
    subject: BaseRoleSubject,
    rightsChecker: RightsChecker
): Boolean = isAccessAllowedOrStatus(
    repo = repo,
    prefix = prefix,
    subject = subject,
    statusCode = HttpStatusCode.MethodNotAllowed,
    rightsChecker = rightsChecker
)

/**
 * Overload of [isAccessAllowedOrRespondNotAllowed] that builds the rights check from a
 * [requiredRights] string and responds with `MethodNotAllowed` when access is denied.
 *
 * @return `true` when access is allowed; otherwise `false` (after responding with `MethodNotAllowed`).
 */
suspend fun ApplicationCall.isAccessAllowedOrRespondNotAllowed(
    repo: ReadRolesRepo,
    prefix: String,
    subject: BaseRoleSubject,
    requiredRights: String,
): Boolean = isAccessAllowedOrRespondNotAllowed(
    repo = repo,
    prefix = prefix,
    subject = subject,
    rightsChecker = RightsChecker(requiredRights)
)

/**
 * Overload of [isAccessAllowedOrRespondNotAllowed] that assembles the rights check from the
 * individual [read], [manage] and [write] flags and responds with `MethodNotAllowed` when access is
 * denied.
 *
 * @return `true` when access is allowed; otherwise `false` (after responding with `MethodNotAllowed`).
 */
suspend fun ApplicationCall.isAccessAllowedOrRespondNotAllowed(
    repo: ReadRolesRepo,
    prefix: String,
    subject: BaseRoleSubject,
    read: Boolean = false,
    manage: Boolean = false,
    write: Boolean = false
): Boolean = isAccessAllowedOrRespondNotAllowed(
    repo = repo,
    prefix = prefix,
    subject = subject,
    rightsChecker = RightsChecker(
        read = read,
        manage = write,
        write = manage
    )
)

