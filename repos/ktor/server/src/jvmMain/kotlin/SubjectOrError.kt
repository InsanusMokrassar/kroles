package dev.inmo.kroles.repos.ktor.repos.ktor.server

import dev.inmo.kroles.repos.BaseRoleSubject
import dev.inmo.kroles.repos.repos.ktor.RolesKtorConstants
import dev.inmo.kroles.roles.BaseRole
import dev.inmo.micro_utils.ktor.server.getQueryParameter
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Extracts a [BaseRoleSubject] from the current request's query parameters.
 *
 * Reads the subject-role parameter (turned into a [BaseRoleSubject.OtherRole]) or the
 * subject-identifier parameter (turned into a [BaseRoleSubject.Direct]), preferring the role form
 * when both are present. When neither is provided, responds with `BadRequest` and returns `null`.
 *
 * @return the resolved subject, or `null` if none could be determined (after responding `BadRequest`).
 */
internal suspend inline fun RoutingContext.subjectOrRespondError(): BaseRoleSubject? {
    val role = call.getQueryParameter(RolesKtorConstants.SubjectRoleQueryParameterName) ?.let(::BaseRole) ?.let(
        BaseRoleSubject::OtherRole
    )
    val identifier = call.getQueryParameter(RolesKtorConstants.SubjectIdentifierQueryParameterName) ?.let(
        BaseRoleSubject::Direct
    )

    return role ?: identifier ?: let {
        call.respond(HttpStatusCode.BadRequest, "You must specify one of parameters: ${RolesKtorConstants.RoleQueryParameterName} or ${RolesKtorConstants.SubjectIdentifierQueryParameterName}")
        null
    }
}