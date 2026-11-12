package io.github.whywood.deepdeep.plugin.purge

import io.github.whywood.deepdeep.plugin.api.PluginHost
import java.io.File
import java.util.UUID

internal class PurgeState(private val host: PluginHost) {
    private val store = PurgeStore(File(host.dataDir, "purge"))

    val runbooks: PurgeRunbooks = PurgeRunbooks(File(host.assetsDir, "purge"))

    @Volatile
    private var settingsCache: PurgeSettings? = null

    @Volatile
    private var defaultInjectionCache: String? = null

    @Volatile
    private var defaultInjectionLoaded: Boolean = false

    fun settings(): PurgeSettings = settingsCache ?: store.readSettings().also { settingsCache = it }

    fun save(settings: PurgeSettings, detail: String) {
        store.writeSettings(settings)
        settingsCache = settings
        store.audit("settings", detail)
    }

    fun effectiveInjection(): String = settings().injection.ifBlank { defaultInjection().orEmpty() }

    fun defaultInjection(): String? {
        if (!defaultInjectionLoaded) {
            defaultInjectionCache = runCatching {
                File(host.assetsDir, "purge/prompt-inject.md").readText(Charsets.UTF_8)
            }.getOrNull()
            defaultInjectionLoaded = true
        }
        return defaultInjectionCache
    }

    fun probe(): PurgeProbe? = store.readProbe()

    fun createProbe(): String {
        val nonce = UUID.randomUUID().toString().replace("-", "").take(8)
        store.writeProbe(nonce)
        store.audit("probe", nonce)
        return nonce
    }

    fun audit(action: String, detail: String) = store.audit(action, detail)

    fun auditEntries(limit: Int = 20): List<PurgeAuditEntry> = store.readAudit(limit)

    fun contributionChars(injection: String, boost: Boolean): Int =
        PurgeText.rawPreamble(injection, boost).length + PurgeText.RULES_BODY.length
}
