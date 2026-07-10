package dev.inmo.kroles.repos.ktor.repos.ktor.server

import dev.inmo.kroles.repos.RolesRepo
import dev.inmo.kroles.repos.repos.ktor.RolesKtorConstants
import io.ktor.server.routing.*

/**
 * Registers the full set of roles API routes (both read and write) on this [Route], backed by [repo].
 *
 * When [rootPath] is non-null the routes are nested under a sub-route with that path; when it is null
 * they are registered directly on the current route.
 *
 * @param repo Repository serving the registered endpoints.
 * @param rootPath Optional path part to nest the routes under; defaults to
 * [RolesKtorConstants.DefaultRolesRootPathPart], or `null` to register on the current route.
 */
fun Route.configureRolesRepoRoutes(
    repo: RolesRepo,
    rootPath: String? = RolesKtorConstants.DefaultRolesRootPathPart
) {
    rootPath ?.let {
        route(rootPath) {
            configureReadRolesRepoRoutes(repo)
            configureWriteRolesRepoRoutes(repo)
        }
    } ?: let {
        configureReadRolesRepoRoutes(repo)
        configureWriteRolesRepoRoutes(repo)
    }
}
