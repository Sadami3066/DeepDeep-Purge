package io.github.whywood.deepdeep.plugin.purge

internal data class PurgeSettings(
    val injection: String = "",
    val boost: Boolean = true,
    val fallback: Boolean = true,
)

internal data class PurgeProbe(
    val nonce: String,
    val createdAt: Long,
)

internal data class PurgeAuditEntry(
    val at: Long,
    val action: String,
    val detail: String,
)
