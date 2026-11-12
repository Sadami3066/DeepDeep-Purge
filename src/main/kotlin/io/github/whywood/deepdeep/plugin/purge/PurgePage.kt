package io.github.whywood.deepdeep.plugin.purge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdLoadingBlock
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdText
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextArea
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.localizedText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class PurgePageState {
    var injection by mutableStateOf("")
    var boost by mutableStateOf(true)
    var fallback by mutableStateOf(true)
    var defaultInjection by mutableStateOf("")
    var runbooks by mutableStateOf<List<PurgeRunbooks.Entry>>(emptyList())
    var probe by mutableStateOf<PurgeProbe?>(null)
    var audit by mutableStateOf<List<PurgeAuditEntry>>(emptyList())
    var sheet by mutableStateOf<PurgeRunbooks.Entry?>(null)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var ready by mutableStateOf(false)
}

@Composable
internal fun PurgePage(state: PurgeState, host: PluginHost) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val page = remember(state) { PurgePageState() }

    LaunchedEffect(state) {
        val settings = withContext(Dispatchers.IO) { state.settings() }
        page.injection = settings.injection
        page.boost = settings.boost
        page.fallback = settings.fallback
        page.defaultInjection = withContext(Dispatchers.IO) { state.defaultInjection() }.orEmpty()
        page.runbooks = withContext(Dispatchers.IO) { state.runbooks.catalog() }
        page.probe = withContext(Dispatchers.IO) { state.probe() }
        page.audit = withContext(Dispatchers.IO) { state.auditEntries() }
        page.ready = true
    }

    fun persist(next: PurgeSettings, detail: String) {
        if (page.busy) return
        scope.launch {
            page.busy = true
            page.error = null
            try {
                withContext(Dispatchers.IO) { state.save(next, detail) }
                host.requestContributionsRefresh()
                page.audit = withContext(Dispatchers.IO) { state.auditEntries() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                page.error = error.message ?: t(host, "保存失败", "Save failed")
            } finally {
                page.busy = false
            }
        }
    }

    fun createProbe() {
        if (page.busy) return
        scope.launch {
            page.busy = true
            page.error = null
            try {
                page.probe = withContext(Dispatchers.IO) {
                    state.createProbe()
                    state.probe()
                }
                page.audit = withContext(Dispatchers.IO) { state.auditEntries() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                page.error = error.message
            } finally {
                page.busy = false
            }
        }
    }

    if (!page.ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DdLoadingBlock(text = t(host, "正在读取设置", "Loading settings"))
        }
        return
    }

    val effectiveInjection = page.injection.ifBlank { page.defaultInjection }
    val totalChars = PurgeText.rawPreamble(effectiveInjection, page.boost).length + PurgeText.RULES_BODY.length
    val overBudget = totalChars > PurgeText.MAX_CONTRIBUTION_CHARS
    val groups = page.runbooks.groupBy { it.role }.toList()
        .sortedBy { (role, _) -> PurgeRunbooks.roleOrder(role) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        page.error?.let { message ->
            DdText(message, style = DdType.bodySmall, color = deepDeepColors().danger)
        }
        StatusCard(host, totalChars, overBudget, page.runbooks.size, page.boost, page.fallback)
        PromptCard(
            host = host,
            injection = page.injection,
            onInjectionChange = { page.injection = it },
            totalChars = totalChars,
            overBudget = overBudget,
            busy = page.busy,
            onSave = {
                persist(
                    PurgeSettings(injection = page.injection.trim(), boost = page.boost, fallback = page.fallback),
                    "prompt",
                )
            },
            onRestoreDefault = {
                page.injection = ""
                persist(
                    PurgeSettings(injection = "", boost = page.boost, fallback = page.fallback),
                    "prompt-default",
                )
            },
        )
        OptionsCard(
            host = host,
            boost = page.boost,
            fallback = page.fallback,
            busy = page.busy,
            onBoostChange = { checked ->
                page.boost = checked
                persist(
                    PurgeSettings(injection = page.injection.trim(), boost = checked, fallback = page.fallback),
                    if (checked) "boost-on" else "boost-off",
                )
            },
            onFallbackChange = { checked ->
                page.fallback = checked
                persist(
                    PurgeSettings(injection = page.injection.trim(), boost = page.boost, fallback = checked),
                    if (checked) "fallback-on" else "fallback-off",
                )
            },
        )
        RunbooksCard(host, groups) { entry -> page.sheet = entry }
        ProbeCard(
            host = host,
            probe = page.probe,
            busy = page.busy,
            onGenerate = ::createProbe,
            onCopy = { text -> copyToClipboard(context, text) },
        )
        ActivityCard(host, page.audit)
        DdText(
            t(
                host,
                "保存后插件会在安全点重建会话；系统提示词与新会话一致。",
                "Changes rebuild sessions at a safe point; the system prompt matches new sessions.",
            ),
            style = DdType.bodySmall,
            color = deepDeepColors().inkTertiary,
        )
        Spacer(Modifier.height(24.dp))
    }

    page.sheet?.let { entry ->
        DdSheet(onDismiss = { page.sheet = null }) {
            DdSheetHeader(title = entry.name, onClose = { page.sheet = null })
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    DdTextAction(text = t(host, "复制", "Copy"), onClick = { copyToClipboard(context, entry.markdown) })
                }
                DdText(entry.markdown, style = DdType.bodySmall, color = deepDeepColors().ink)
            }
        }
    }
}

@Composable
private fun StatusCard(
    host: PluginHost,
    totalChars: Int,
    overBudget: Boolean,
    runbookCount: Int,
    boost: Boolean,
    fallback: Boolean,
) {
    val colors = deepDeepColors()
    DdGroupCard(header = { DdSectionLabel(t(host, "状态", "STATUS").uppercase()) }) {
        DdListRow(
            title = t(host, "口径段", "Prompt size"),
            value = "$totalChars / ${PurgeText.MAX_CONTRIBUTION_CHARS}",
        )
        DdListRow(
            title = t(host, "runbook", "Runbooks"),
            value = t(host, "$runbookCount 份", "$runbookCount"),
        )
        DdListRow(
            title = t(host, "强化段", "Boost"),
            value = if (boost) t(host, "开", "On") else t(host, "关", "Off"),
        )
        DdListRow(
            title = t(host, "拒答兜底", "Refusal fallback"),
            value = if (fallback) t(host, "开", "On") else t(host, "关", "Off"),
        )
        DdListRow(title = t(host, "版本", "Version"), value = host.manifest.version)
        if (overBudget) {
            Text(
                text = t(host, "口径段超出插件预算，保存后会被截断。", "Prompt exceeds the plugin budget and will be truncated."),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                style = DdType.bodySmall,
                color = colors.danger,
            )
        }
    }
}

@Composable
private fun PromptCard(
    host: PluginHost,
    injection: String,
    onInjectionChange: (String) -> Unit,
    totalChars: Int,
    overBudget: Boolean,
    busy: Boolean,
    onSave: () -> Unit,
    onRestoreDefault: () -> Unit,
) {
    val colors = deepDeepColors()
    DdGroupCard(header = { DdSectionLabel(t(host, "口径段", "PROMPT").uppercase()) }) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DdTextArea(
                value = injection,
                onValueChange = onInjectionChange,
                placeholder = t(host, "留空使用内置口径", "Empty uses the bundled prompt"),
                minHeight = 140.dp,
                maxHeight = 300.dp,
            )
            Text(
                text = t(
                    host,
                    "$totalChars / ${PurgeText.MAX_CONTRIBUTION_CHARS} 字符",
                    "$totalChars / ${PurgeText.MAX_CONTRIBUTION_CHARS} chars",
                ),
                style = DdType.bodySmall,
                color = if (overBudget) colors.danger else colors.inkTertiary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DdPrimaryButton(text = t(host, "保存", "Save"), onClick = onSave, enabled = !busy)
                DdTextAction(text = t(host, "恢复默认", "Reset"), onClick = onRestoreDefault, enabled = !busy)
            }
        }
    }
}

@Composable
private fun OptionsCard(
    host: PluginHost,
    boost: Boolean,
    fallback: Boolean,
    busy: Boolean,
    onBoostChange: (Boolean) -> Unit,
    onFallbackChange: (Boolean) -> Unit,
) {
    DdGroupCard(header = { DdSectionLabel(t(host, "选项", "OPTIONS").uppercase()) }) {
        DdListRow(
            title = t(host, "强化段", "Boost"),
            subtitle = t(host, "在口径段末尾追加交付纪律", "Append delivery rules after the prompt"),
            trailing = {
                DdSwitch(
                    checked = boost,
                    onCheckedChange = onBoostChange,
                    enabled = !busy,
                )
            },
        )
        DdListRow(
            title = t(host, "拒答兜底", "Refusal fallback"),
            subtitle = t(host, "整轮只有拒答时补一条可用内容", "Add usable content when a run only refuses"),
            trailing = {
                DdSwitch(
                    checked = fallback,
                    onCheckedChange = onFallbackChange,
                    enabled = !busy,
                )
            },
        )
    }
}

@Composable
private fun RunbooksCard(
    host: PluginHost,
    groups: List<Pair<String, List<PurgeRunbooks.Entry>>>,
    onOpen: (PurgeRunbooks.Entry) -> Unit,
) {
    val colors = deepDeepColors()
    DdGroupCard(header = { DdSectionLabel(t(host, "RUNBOOK", "RUNBOOKS").uppercase()) }) {
        if (groups.isEmpty()) {
            Text(
                text = t(host, "内置 runbook 不可用。", "Bundled runbooks are unavailable."),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                style = DdType.bodySmall,
                color = colors.danger,
            )
        }
        groups.forEach { (role, entries) ->
            Text(
                text = roleLabel(host, role),
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 2.dp),
                style = DdType.micro,
                color = colors.inkTertiary,
            )
            entries.forEach { entry ->
                DdListRow(
                    title = entry.name,
                    subtitle = entry.description,
                    subtitleMaxLines = 2,
                    showChevron = true,
                    onClick = { onOpen(entry) },
                )
            }
        }
    }
}

@Composable
private fun ProbeCard(
    host: PluginHost,
    probe: PurgeProbe?,
    busy: Boolean,
    onGenerate: () -> Unit,
    onCopy: (String) -> Unit,
) {
    DdGroupCard(header = { DdSectionLabel(t(host, "注入探针", "PROBE").uppercase()) }) {
        DdListRow(
            title = t(host, "生成探针", "Generate probe"),
            subtitle = probe?.let { "purge-${it.nonce}" } ?: t(host, "未生成", "Not generated"),
            trailing = {
                DdTextAction(
                    text = if (probe == null) t(host, "生成", "Generate") else t(host, "重新生成", "Regenerate"),
                    onClick = onGenerate,
                    enabled = !busy,
                )
            },
        )
        if (probe != null) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DdText(
                    PurgeText.probeInstruction(probe.nonce),
                    style = DdType.bodySmall,
                    color = deepDeepColors().inkSecondary,
                )
                DdTextAction(
                    text = t(host, "复制指令", "Copy instruction"),
                    onClick = { onCopy(PurgeText.probeInstruction(probe.nonce)) },
                )
            }
        }
    }
}

@Composable
private fun ActivityCard(host: PluginHost, entries: List<PurgeAuditEntry>) {
    val colors = deepDeepColors()
    DdGroupCard(header = { DdSectionLabel(t(host, "最近动作", "RECENT").uppercase()) }) {
        if (entries.isEmpty()) {
            Text(
                text = t(host, "暂无记录", "No activity yet"),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
        }
        entries.take(6).forEach { entry ->
            DdListRow(
                title = actionLabel(host, entry.action),
                value = timeLabel(entry.at),
            )
        }
    }
}

private fun t(host: PluginHost, zh: String, en: String): String =
    host.text(localizedText(zh, zh = zh, en = en))

private fun roleLabel(host: PluginHost, role: String): String = when (role) {
    "recon" -> t(host, "信息收集", "Recon")
    "vuln-scan" -> t(host, "漏洞面", "Vulnerability")
    "exploit" -> t(host, "入口与执行", "Exploit")
    "internal" -> t(host, "内网与隧道", "Internal")
    "plan" -> t(host, "环境准备", "Setup")
    else -> t(host, "其他", "Other")
}

private fun actionLabel(host: PluginHost, action: String): String = when (action) {
    "settings" -> t(host, "已保存设置", "Settings saved")
    "prompt" -> t(host, "已保存口径段", "Prompt saved")
    "prompt-default" -> t(host, "已恢复默认口径", "Prompt reset")
    "boost-on" -> t(host, "已开启强化段", "Boost enabled")
    "boost-off" -> t(host, "已关闭强化段", "Boost disabled")
    "fallback-on" -> t(host, "已开启拒答兜底", "Fallback enabled")
    "fallback-off" -> t(host, "已关闭拒答兜底", "Fallback disabled")
    "probe" -> t(host, "已生成探针", "Probe generated")
    "fallback" -> t(host, "已补兜底内容", "Fallback posted")
    "enable" -> t(host, "插件已启用", "Plugin enabled")
    "disable" -> t(host, "插件已停用", "Plugin disabled")
    else -> action
}

private fun timeLabel(at: Long): String =
    runCatching { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(at)) }.getOrDefault("")

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("purge", text))
    }
}
