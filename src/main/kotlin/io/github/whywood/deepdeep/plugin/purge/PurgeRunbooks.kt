package io.github.whywood.deepdeep.plugin.purge

import java.io.File

internal class PurgeRunbooks(private val root: File) {
    data class Entry(
        val id: String,
        val name: String,
        val description: String,
        val role: String,
        val markdown: String,
    )

    private val lock = Any()

    @Volatile
    private var cache: List<Entry>? = null

    fun catalog(): List<Entry> = cache ?: synchronized(lock) {
        cache ?: scan().also { cache = it }
    }

    fun content(id: String): String? = catalog().firstOrNull { it.id == id }?.markdown

    fun ids(): List<String> = catalog().map { it.id }

    fun invalidate() {
        synchronized(lock) { cache = null }
    }

    private fun scan(): List<Entry> {
        val directories = File(root, "skills").listFiles()?.filter { it.isDirectory }.orEmpty()
        return directories.mapNotNull { directory ->
            val id = directory.name
            if (!isSafeId(id)) return@mapNotNull null
            val file = File(directory, SKILL_FILE)
            if (!file.isFile) return@mapNotNull null
            val markdown = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return@mapNotNull null
            val meta = parseFrontMatter(markdown)
            Entry(
                id = id,
                name = meta["name"]?.takeIf { it.isNotBlank() } ?: id,
                description = meta["description"].orEmpty(),
                role = meta["role"].orEmpty(),
                markdown = markdown,
            )
        }.sortedWith(compareBy({ roleOrder(it.role) }, { it.id }))
    }

    companion object {
        private const val SKILL_FILE = "SKILL.md"

        fun roleOrder(role: String): Int = when (role.lowercase()) {
            "recon" -> 0
            "vuln-scan" -> 1
            "exploit" -> 2
            "internal" -> 3
            "plan" -> 4
            else -> 5
        }

        fun isSafeId(id: String): Boolean {
            if (id.isEmpty() || id.length > 64 || id == "." || id == "..") return false
            return id.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
        }

        fun parseFrontMatter(text: String): Map<String, String> {
            val lines = text.lineSequence().toList()
            if (lines.firstOrNull()?.trim() != "---") return emptyMap()
            val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
            if (end < 0) return emptyMap()
            val result = LinkedHashMap<String, String>()
            lines.subList(1, end + 1).forEach { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) return@forEach
                val key = line.substring(0, separator).trim()
                val value = line.substring(separator + 1).trim().trim('"', '\'')
                if (key.isNotEmpty()) result[key] = value
            }
            return result
        }
    }
}
