package io.github.whywood.deepdeep.plugin.purge

import io.github.whywood.deepdeep.plugin.api.Contributions
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginExperimentalApi
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginPage
import io.github.whywood.deepdeep.plugin.api.PluginUiContributions
import io.github.whywood.deepdeep.plugin.api.PromptOverride
import io.github.whywood.deepdeep.plugin.api.PromptOverridePlugin
import io.github.whywood.deepdeep.plugin.api.PromptSection
import io.github.whywood.deepdeep.plugin.api.SlotBinding
import io.github.whywood.deepdeep.plugin.api.assetIcon
import io.github.whywood.deepdeep.plugin.api.available
import io.github.whywood.deepdeep.plugin.api.contributions
import io.github.whywood.deepdeep.plugin.api.localizedText
import io.github.whywood.deepdeep.plugin.api.pluginPage
import io.github.whywood.deepdeep.plugin.api.promptOverride as promptOverrideOf

@OptIn(PluginExperimentalApi::class)
public class PurgePlugin : DeepDeepPlugin, PromptOverridePlugin {
    @Volatile
    private var stateCache: PurgeState? = null

    override suspend fun availability(host: PluginHost): PluginAvailability = available()

    override fun promptSections(host: PluginHost): List<PromptSection> = emptyList()

    override fun contributions(host: PluginHost): Contributions {
        val state = state(host)
        return contributions(
            tools = listOf(purgePlaybookTool(state.runbooks)),
            hooks = PurgeHooks(host, state),
        )
    }

    override fun promptOverride(host: PluginHost): PromptOverride {
        val state = state(host)
        val injection = state.effectiveInjection()
        if (injection.isBlank()) {
            return promptOverrideOf(preamble = null, rules = PurgeText.RULES_BODY, priority = PRIORITY)
        }
        return promptOverrideOf(
            preamble = PurgeText.composePreamble(injection, state.settings().boost),
            rules = PurgeText.RULES_BODY,
            priority = PRIORITY,
        )
    }

    override fun ui(host: PluginHost): PluginUiContributions = object : PluginUiContributions {
        override val bindings: List<SlotBinding> = emptyList()

        override val pages: List<PluginPage> = listOf(
            pluginPage(
                id = PAGE_ID,
                title = localizedText("NetSec", zh = "网络攻防", en = "NetSec"),
                icon = assetIcon("asset:icon.png"),
            ) {
                PurgePage(state(host), host)
            },
        )
    }

    override suspend fun onEnable(host: PluginHost) {
        state(host).audit("enable", host.manifest.version)
    }

    override suspend fun onDisable(host: PluginHost) {
        state(host).audit("disable", host.manifest.version)
    }

    private fun state(host: PluginHost): PurgeState =
        stateCache ?: synchronized(this) {
            stateCache ?: PurgeState(host).also { stateCache = it }
        }

    private companion object {
        const val PRIORITY: Int = 1000
        const val PAGE_ID: String = "main"
    }
}
