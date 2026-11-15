package dev.inmo.kroles.roles

import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Represents a role as its raw string value.
 *
 * BaseRole is a lightweight value type used as the common representation*/
@Serializable(with = BaseRole.Serializer::class)
data class BaseRole(
    val plain: String
) {
    object Serializer : KSerializer<BaseRole> {
        override val descriptor = PrimitiveSerialDescriptor("dev.inmo.kroles.roles.BaseRole", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: BaseRole) = encoder.encodeString(value.plain)

        override fun deserialize(decoder: Decoder): BaseRole = BaseRole(decoder.decodeString())
    }

    companion object {
        /**
         * A [BaseRole] with an empty plain string.
         */
        val EMPTY = BaseRole("")
    }
}
