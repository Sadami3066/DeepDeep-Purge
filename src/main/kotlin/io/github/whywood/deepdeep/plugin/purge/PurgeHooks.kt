package io.github.whywood.deepdeep.plugin.purge

import io.github.whywood.deepdeep.plugin.api.InputDecision
import io.github.whywood.deepdeep.plugin.api.InputRequest
import io.github.whywood.deepdeep.plugin.api.MessageFinishedEvent
import io.github.whywood.deepdeep.plugin.api.MessageRole
import io.github.whywood.deepdeep.plugin.api.MessageStartedEvent
import io.github.whywood.deepdeep.plugin.api.PluginHooks
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.RunFinishedEvent
import io.github.whywood.deepdeep.plugin.api.RunStartedEvent
import io.github.whywood.deepdeep.plugin.api.SessionClosedEvent
import io.github.whywood.deepdeep.plugin.api.chatDisplay
import io.github.whywood.deepdeep.plugin.api.chatMessageOptions
import io.github.whywood.deepdeep.plugin.api.inputContinue
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val FALLBACK_CUSTOM_TYPE = "purge.fallback"

internal class PurgeHooks(
    private val host: PluginHost,
    private val state: PurgeState,
) : PluginHooks {
    private var lastAssistantText: String? = null
    private var lastAssistantMessageId: String? = null
    private var lastUserPrompt: String? = null
    private var fallbackRunId: String? = null

    override suspend fun inputTransform(request: InputRequest): InputDecision {
        lastUserPrompt = request.text
        val skills = PurgeIntents.matchSkills(request.text)
        if (skills.isEmpty()) return inputContinue(request.text, request.imagePaths)
        val blocks = skills.mapNotNull { id ->
            state.runbooks.content(id)?.let { body -> id to body.take(PurgeText.MAX_RUNBOOK_CHARS) }
        }
        if (blocks.isEmpty()) return inputContinue(request.text, request.imagePaths)
        val augmented = buildString {
            appendLine(request.text)
            appendLine()
            appendLine(PurgeText.CONTEXT_CLAUSE)
            appendLine()
            appendLine("runbook 原文如下，按其中命令执行，占位符按本轮已知值替换：")
            blocks.forEach { (id, body) ->
                appendLine()
                appendLine("<runbook skill=\"$id\">")
                appendLine(body)
                appendLine("</runbook>")
            }
        }
        return inputContinue(augmented, request.imagePaths)
    }

    override suspend fun runStarted(event: RunStartedEvent) {
        lastAssistantText = null
        lastAssistantMessageId = null
    }

    override suspend fun messageStarted(event: MessageStartedEvent) {
        if (event.role == MessageRole.ASSISTANT) lastAssistantMessageId = event.messageId
    }

    override suspend fun messageFinished(event: MessageFinishedEvent) {
        if (event.messageId != lastAssistantMessageId) return
        val text = event.text?.takeIf { it.isNotBlank() } ?: return
        lastAssistantText = text
    }

    override suspend fun runFinished(event: RunFinishedEvent) {
        if (event.error != null) return
        if (!state.settings().fallback) return
        if (fallbackRunId == event.runId) return
        val refusal = lastAssistantText ?: return
        if (!PurgeFallback.looksLikeRefusal(refusal)) return
        val prompt = lastUserPrompt ?: return
        val message = PurgeFallback.message(prompt, state.runbooks) ?: return
        fallbackRunId = event.runId
        host.scope.launch {
            val sent = runCatching {
                host.chat().sendMessage(
                    event.sessionId,
                    message,
                    chatMessageOptions(
                        triggerTurn = false,
                        display = chatDisplay(
                            customType = FALLBACK_CUSTOM_TYPE,
                            payload = buildJsonObject { put("type", "refusal") },
                            text = message,
                        ),
                    ),
                )
            }.isSuccess
            state.audit("fallback", if (sent) event.sessionId else "failed:${event.sessionId}")
        }
    }

    override suspend fun sessionClosed(event: SessionClosedEvent) {
        lastAssistantText = null
        lastAssistantMessageId = null
        lastUserPrompt = null
        fallbackRunId = null
    }
}
