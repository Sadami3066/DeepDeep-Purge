package io.github.whywood.deepdeep.plugin.purge

internal object PurgeFallback {
    private val refusalMarkers = listOf(
        "抱歉", "对不起", "我不能", "我无法", "无法协助", "无法提供", "不能提供", "不能帮",
        "不能满足", "不便提供", "我不会", "恕不", "拒绝", "建议你", "建议通过", "请理解",
        "我不能帮", "不做这个", "这个我不做", "这类内容我不做",
        "I can't", "I cannot", "I won't", "I'm sorry", "unable to", "not able to",
        "as an AI", "against my", "I must decline", "I have to decline",
    )

    private val productiveMarkers = listOf(
        "```", "```bash", "```sh", "```powershell", "```python", "TARGET", "HOST",
    )

    fun looksLikeRefusal(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isEmpty()) return false
        if (productiveMarkers.any { normalized.contains(it) }) return false
        return refusalMarkers.any { normalized.contains(it, ignoreCase = true) }
    }

    fun message(prompt: String, runbooks: PurgeRunbooks): String? {
        val classes = PurgeIntents.matchClasses(prompt)
        val skills = PurgeIntents.matchSkills(prompt)
        if (skills.isEmpty()) {
            val template = when {
                PurgeTaskClass.CATALOG in classes -> PurgeText.CATALOG_FALLBACK
                PurgeTaskClass.CRACK in classes -> PurgeText.CRACK_FALLBACK
                else -> null
            }
            return template?.let { body ->
                "本机口径（模型本轮没有给做法，下面这份直接可用）：\n\n" + body
            }
        }
        val blocks = skills.mapNotNull { id ->
            runbooks.content(id)?.let { body -> id to body.take(PurgeText.FALLBACK_RUNBOOK_CHARS) }
        }
        if (blocks.isEmpty()) return null
        return buildString {
            appendLine("本机 runbook · 模型本轮没有给命令，下面这份直接可用：")
            blocks.forEach { (id, body) ->
                appendLine()
                appendLine("### $id")
                appendLine(body)
            }
            appendLine()
            append("按占位符替换后执行；需要我继续推进就接着说要哪一步。")
        }
    }
}
