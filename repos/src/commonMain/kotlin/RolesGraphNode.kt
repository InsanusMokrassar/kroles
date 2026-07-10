package dev.inmo.kroles.repos

import dev.inmo.kroles.roles.BaseRole

/**
 * Node of the subject/role hierarchy graph. Each node holds a [subject] and links to its child nodes.
 *
 * The type comes in [Mutable] (builder) and [Immutable] (snapshot) flavours, and in [DirectNode] and
 * [RoleNode] variants depending on the wrapped subject.
 */
sealed interface RoleSubjectGraphNode {
    /**
     * The subject this node represents.
     */
    val subject: BaseRoleSubject
    /**
     * Nodes that are children of this node in the hierarchy.
     */
    val childNodes: Set<RoleSubjectGraphNode>

    /**
     * Mutable graph node used while building the hierarchy; also tracks its [parentNodes].
     */
    sealed interface Mutable : RoleSubjectGraphNode {
        /**
         * Nodes that are parents of this node in the hierarchy.
         */
        val parentNodes: MutableSet<Mutable>
        override val childNodes: MutableSet<Mutable>

        /**
         * Produces the immutable snapshot of this node, reusing already converted nodes from [immutableMap]
         * to handle shared and cyclic references.
         */
        fun immutable(immutableMap: MutableMap<Mutable, Immutable> = mutableMapOf()): Immutable

        companion object {
            /**
             * Creates the matching mutable node for the given [roleSubject]: a [RoleNode.MutableRoleNode] for a
             * role subject or a [DirectNode.MutableDirectNode] for a direct subject.
             */
            operator fun invoke(roleSubject: BaseRoleSubject) = when (roleSubject) {
                is BaseRoleSubject.OtherRole -> RoleNode.MutableRoleNode(roleSubject.role)
                is BaseRoleSubject.Direct -> DirectNode.MutableDirectNode(roleSubject.identifier)
            }
        }
    }
    /**
     * Immutable snapshot of a graph node.
     */
    sealed interface Immutable : RoleSubjectGraphNode {
        override val childNodes: Set<Immutable>

        /**
         * Returns all descendant nodes reachable from this node.
         */
        fun allChildren(): Set<RoleSubjectGraphNode> = allChildren(emptySet())
    }
    /**
     * Graph node whose subject is a [BaseRoleSubject.Direct] identifier.
     */
    sealed interface DirectNode : RoleSubjectGraphNode {
        /**
         * The raw identifier of the direct subject.
         */
        val identifier: BaseRolSubjectDirectIdentifier
        override val subject: BaseRoleSubject.Direct
        /**
         * Immutable snapshot variant of a [DirectNode].
         */
        data class ImmutableDirectNode(override val identifier: BaseRolSubjectDirectIdentifier, override val childNodes: Set<Immutable>) : DirectNode, Immutable {
            override val subject: BaseRoleSubject.Direct = BaseRoleSubject.Direct(identifier)

            override fun hashCode(): Int {
                return identifier.hashCode()
            }
        }
        /**
         * Mutable builder variant of a [DirectNode]. A direct subject is always a leaf as a subject, so it exposes
         * no real parents.
         */
        data class MutableDirectNode(
            override val identifier: BaseRolSubjectDirectIdentifier,
            override val childNodes: MutableSet<Mutable> = mutableSetOf()
        ) : DirectNode, Mutable {
            override val subject: BaseRoleSubject.Direct = BaseRoleSubject.Direct(identifier)
            override val parentNodes: MutableSet<Mutable>
                get() = mutableSetOf()

            override fun immutable(
                immutableMap: MutableMap<Mutable, Immutable>
            ): Immutable {
                immutableMap[this] ?.let { return it }

                val fakeImmutableSet = mutableSetOf<Immutable>()
                val immutable = ImmutableDirectNode(
                    identifier,
                    fakeImmutableSet
                )
                immutableMap[this] = immutable
                childNodes.forEach {
                    fakeImmutableSet.add(it.immutable(immutableMap))
                }
                return immutable
            }

            override fun hashCode(): Int {
                return identifier.hashCode()
            }
        }
    }
    /**
     * Graph node whose subject is a [BaseRoleSubject.OtherRole], i.e. a role acting as a subject.
     */
    sealed interface RoleNode : RoleSubjectGraphNode {
        /**
         * The role this node represents.
         */
        val role: BaseRole
        override val subject: BaseRoleSubject.OtherRole
        /**
         * Immutable snapshot variant of a [RoleNode].
         */
        data class ImmutableRoleNode(
            override val role: BaseRole,
            override val childNodes: Set<Immutable>
        ) : RoleNode, Immutable {
            override val subject: BaseRoleSubject.OtherRole = BaseRoleSubject.OtherRole(role)

            override fun hashCode(): Int {
                return role.hashCode()
            }
        }
        /**
         * Mutable builder variant of a [RoleNode], tracking both parent and child links.
         */
        data class MutableRoleNode(
            override val role: BaseRole,
            override val parentNodes: MutableSet<Mutable> = mutableSetOf(),
            override val childNodes: MutableSet<Mutable> = mutableSetOf()
        ) : RoleNode, Mutable {
            override val subject: BaseRoleSubject.OtherRole = BaseRoleSubject.OtherRole(role)

            override fun immutable(
                immutableMap: MutableMap<Mutable, Immutable>
            ): Immutable {
                immutableMap[this] ?.let { return it }

                val fakeParentNodesImmutableSet = mutableSetOf<Immutable>()
                val fakeChildrenNodesImmutableSet = mutableSetOf<Immutable>()
                val immutable = ImmutableRoleNode(
                    role,
                    fakeChildrenNodesImmutableSet,
                )
                immutableMap[this] = immutable
                parentNodes.forEach {
                    fakeParentNodesImmutableSet.add(it.immutable(immutableMap))
                }
                childNodes.forEach {
                    fakeChildrenNodesImmutableSet.add(it.immutable(immutableMap))
                }
                return immutable
            }

            override fun hashCode(): Int {
                return role.hashCode()
            }
        }
    }
}
/**
 * Returns all descendant nodes reachable from this node, skipping any node present in [exclude] to guard
 * against revisiting already seen nodes (and cycles).
 */
fun RoleSubjectGraphNode.Immutable.allChildren(exclude: Set<RoleSubjectGraphNode.Immutable>): Set<RoleSubjectGraphNode.Immutable> {
    return childNodes.fold(childNodes) { acc, roleSubjectGraphNode ->
        if (roleSubjectGraphNode !in exclude) {
            (acc + roleSubjectGraphNode.allChildren(exclude + acc))
        } else {
            acc
        }
    }
}

private fun createTempNode(
    subject: BaseRoleSubject,
    parent: RoleSubjectGraphNode.Mutable?,
    directSubNodes: Map<BaseRoleSubject, Set<BaseRole>>,
    rolesNodesMap: MutableMap<BaseRoleSubject, RoleSubjectGraphNode.Mutable>
): RoleSubjectGraphNode.Mutable {
    val node = RoleSubjectGraphNode.Mutable(
        subject,
    )
    rolesNodesMap[subject] = node
    parent ?.let { node.parentNodes.add(it) }
    node.childNodes.addAll(
        getTempNodes(node, directSubNodes, rolesNodesMap)
    )
    return node
}
private fun RoleSubjectGraphNode.Mutable.collectMutableTempNodes(
    target: MutableSet<RoleSubjectGraphNode.Mutable> = mutableSetOf()
): MutableSet<RoleSubjectGraphNode.Mutable> {
    if (target.add(this)) {
        childNodes.filter {
            target.add(it)
        }.forEach {
            it.collectMutableTempNodes(target)
        }
    }
    return target
}
private fun getTempNodes(
    parent: RoleSubjectGraphNode.Mutable,
    directSubNodes: Map<BaseRoleSubject, Set<BaseRole>>,
    rolesNodesMap: MutableMap<BaseRoleSubject, RoleSubjectGraphNode.Mutable>
): Set<RoleSubjectGraphNode.Mutable> {
    return directSubNodes[parent.subject] ?.map {
        val subject = BaseRoleSubject.OtherRole(it)
        rolesNodesMap[subject] ?.also {
            it.parentNodes.add(parent)
        } ?: createTempNode(subject, parent, directSubNodes, rolesNodesMap)
    } ?.toSet() ?.fold(mutableSetOf()) { acc, mutableTempNode ->
        mutableTempNode.collectMutableTempNodes(acc)
    } ?: emptySet()
}

/**
 * Generic directed-graph node holding a [value] together with its direct [parents] and [children].
 */
sealed interface GraphNode<T> {
    /**
     * The value stored in this node.
     */
    val value: T
    /**
     * Direct parent nodes.
     */
    val parents: Set<GraphNode<T>>
    /**
     * Direct child nodes.
     */
    val children: Set<GraphNode<T>>

    /**
     * All nodes reachable by following [children] transitively.
     */
    val allChildren: Set<GraphNode<T>>
        get() = children + children.flatMap { it.allChildren }.toSet()
    /**
     * All nodes reachable by following [parents] transitively.
     */
    val allParents: Set<GraphNode<T>>
        get() = parents + parents.flatMap { it.allParents }.toSet()

    private class ImmutableGraphNode<T>(
        override val value: T,
        override val parents: MutableSet<ImmutableGraphNode<T>>,
        override val children: MutableSet<ImmutableGraphNode<T>>
    ) : GraphNode<T>

    private class MutableGraphNode<T>(
        override val value: T,
        override val parents: MutableSet<MutableGraphNode<T>>,
        override val children: MutableSet<MutableGraphNode<T>>
    ) : GraphNode<T>

    companion object {
        /**
         * Builds a graph from [dataMap], where each key maps to the values that become its children, and returns
         * a map from every value to its corresponding node.
         */
        fun <T> buildGraph(dataMap: Map<T, Iterable<T>>): Map<T, GraphNode<T>> {
            val nodesMap = mutableMapOf<T, MutableGraphNode<T>>()

            dataMap.forEach { (k, vs) ->
                val kNode = nodesMap.getOrPut(k) { MutableGraphNode(k, mutableSetOf(), mutableSetOf()) }
                vs.forEach { v ->
                    val vNode = nodesMap.getOrPut(v) { MutableGraphNode(v, mutableSetOf(), mutableSetOf()) }
                    vNode.parents.add(kNode)
                    kNode.children.add(vNode)
                }
            }

            return nodesMap.toMap()
        }
    }
}


/**
 * Builds the subject hierarchy graph from [directSubNodes], which maps each subject to the roles directly granted
 * to it. Each role is turned into a [BaseRoleSubject] so it can act as a child node, and the result maps every
 * subject to its [GraphNode].
 */
fun buildRolesNodesGraph(directSubNodes: Map<BaseRoleSubject, Set<BaseRole>>): Map<BaseRoleSubject, GraphNode<BaseRoleSubject>> {
    return GraphNode.buildGraph(directSubNodes.mapValues { it.value.map { BaseRoleSubject(it) } })
}