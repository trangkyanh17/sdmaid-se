package eu.darken.sdmse.appcontrol.core.access.history

import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import java.time.Instant

data class AppAccessOperation(
    val id: String,
    val packageName: String,
    val userId: Int,
    val kind: Kind,
    val subjectId: String,
    val before: Value,
    val after: Value,
    val createdAt: Instant,
    val revertOf: String? = null,
) {
    enum class Kind {
        RUNTIME_PERMISSION,
        APP_OP,
    }

    sealed interface Value {
        data class Permission(val granted: Boolean) : Value
        data class AppOp(val value: PkgOps.AppOpsValue) : Value
    }

    companion object {
        internal fun fromEntity(entity: AppAccessOperationEntity): AppAccessOperation {
            val kind = try {
                Kind.valueOf(entity.kind)
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException("Unknown access operation kind '${entity.kind}'", e)
            }

            return AppAccessOperation(
                id = entity.id,
                packageName = entity.packageName,
                userId = entity.userId,
                kind = kind,
                subjectId = entity.subjectId,
                before = decodeValue(kind, entity.beforeValue),
                after = decodeValue(kind, entity.afterValue),
                createdAt = Instant.ofEpochMilli(entity.createdAtEpochMs),
                revertOf = entity.revertOf,
            )
        }

        internal fun encodeValue(kind: Kind, value: Value): String = when (kind) {
            Kind.RUNTIME_PERMISSION -> {
                val permission = value as? Value.Permission
                    ?: throw IllegalArgumentException("Runtime permission operation requires Permission values")
                if (permission.granted) "granted" else "denied"
            }

            Kind.APP_OP -> {
                val appOp = value as? Value.AppOp
                    ?: throw IllegalArgumentException("AppOps operation requires AppOp values")
                appOp.value.raw
            }
        }

        internal fun decodeValue(kind: Kind, raw: String): Value = when (kind) {
            Kind.RUNTIME_PERMISSION -> when (raw) {
                "granted" -> Value.Permission(granted = true)
                "denied" -> Value.Permission(granted = false)
                else -> throw IllegalStateException("Unknown runtime-permission history value '$raw'")
            }

            Kind.APP_OP -> {
                val value = PkgOps.AppOpsValue.entries.singleOrNull { it.raw == raw }
                    ?: throw IllegalStateException("Unknown AppOps history value '$raw'")
                Value.AppOp(value)
            }
        }
    }
}
