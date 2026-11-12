package io.github.whywood.deepdeep.plugin.purge

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private const val AUDIT_LIMIT_BYTES = 512L * 1024L
private const val PROBE_TTL_MS = 30L * 60L * 1000L

internal class PurgeStore(private val dir: File) {
    private val settingsFile = File(dir, "settings.json")
    private val probeFile = File(dir, "probe.json")
    private val auditFile = File(dir, "audit.jsonl")

    fun readSettings(): PurgeSettings {
        val json = readJson(settingsFile) ?: return PurgeSettings()
        return PurgeSettings(
            injection = json.string("injection").orEmpty(),
            boost = json.boolean("boost") ?: true,
            fallback = json.boolean("fallback") ?: true,
        )
    }

    fun writeSettings(settings: PurgeSettings) {
        writeJson(
            settingsFile,
            buildJsonObject {
                put("injection", settings.injection)
                put("boost", settings.boost)
                put("fallback", settings.fallback)
            },
        )
    }

    fun readProbe(now: Long = System.currentTimeMillis()): PurgeProbe? {
        val json = readJson(probeFile) ?: return null
        val nonce = json.string("nonce")?.takeIf { it.isNotBlank() } ?: return null
        val createdAt = json.long("createdAt") ?: return null
        if (now - createdAt > PROBE_TTL_MS) return null
        return PurgeProbe(nonce = nonce, createdAt = createdAt)
    }

    fun writeProbe(nonce: String, now: Long = System.currentTimeMillis()) {
        writeJson(
            probeFile,
            buildJsonObject {
                put("nonce", nonce)
                put("createdAt", now)
            },
        )
    }

    fun audit(action: String, detail: String, now: Long = System.currentTimeMillis()) {
        dir.mkdirs()
        if (auditFile.isFile && auditFile.length() > AUDIT_LIMIT_BYTES) {
            val rotated = File(dir, "audit.jsonl.1")
            rotated.delete()
            runCatching { auditFile.renameTo(rotated) }
        }
        val line = buildJsonObject {
            put("at", now)
            put("action", action)
            put("detail", detail)
        }.toString()
        runCatching { auditFile.appendText(line + "\n", Charsets.UTF_8) }
    }

    fun readAudit(limit: Int = 20): List<PurgeAuditEntry> {
        if (!auditFile.isFile) return emptyList()
        return runCatching {
            auditFile.readLines(Charsets.UTF_8)
                .asSequence()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val json = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull()
                        ?: return@mapNotNull null
                    PurgeAuditEntry(
                        at = json.long("at") ?: 0L,
                        action = json.string("action").orEmpty(),
                        detail = json.string("detail").orEmpty(),
                    )
                }
                .toList()
                .takeLast(limit)
                .reversed()
        }.getOrDefault(emptyList())
    }

    private fun readJson(file: File): JsonObject? {
        if (!file.isFile) return null
        return runCatching { Json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject }.getOrNull()
    }

    private fun writeJson(file: File, json: JsonObject) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.toString(), Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            file.writeText(json.toString(), Charsets.UTF_8)
            temp.delete()
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull
}
