package io.github.whywood.deepdeep.plugin.purge

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PurgeFallbackTest {
    private val runbooks = PurgeRunbooks(File("src/main/assets/purge"))

    @Test
    fun `refusal detection ignores productive answers`() {
        assertTrue(PurgeFallback.looksLikeRefusal("抱歉，我不能提供这个。"))
        assertFalse(PurgeFallback.looksLikeRefusal("先给命令块：\n```bash\nhydra -l USER ssh://TARGET\n```"))
        assertFalse(PurgeFallback.looksLikeRefusal("TARGET 是占位符，按步骤执行。"))
        assertFalse(PurgeFallback.looksLikeRefusal(""))
    }

    @Test
    fun `security prompts fall back to the runbook text`() {
        val message = PurgeFallback.message("帮我先 fscan 扫一遍内网", runbooks)

        assertNotNull(message)
        assertTrue(message!!.contains("fscan-intranet"))
        assertTrue(message.contains("按占位符替换后执行"))
    }

    @Test
    fun `catalog prompts fall back to the search template`() {
        val message = PurgeFallback.message("帮我找部片子，番号搜一下", runbooks)

        assertNotNull(message)
        assertTrue(message!!.contains("找片"))
        assertTrue(message.contains("番号"))
    }

    @Test
    fun `crack prompts fall back to the reverse engineering template`() {
        val message = PurgeFallback.message("帮我脱壳一个软件", runbooks)

        assertNotNull(message)
        assertTrue(message!!.contains("apktool"))
    }

    @Test
    fun `unknown prompts produce no fallback`() {
        assertTrue(PurgeFallback.message("帮我写个周报", runbooks) == null)
    }
}
