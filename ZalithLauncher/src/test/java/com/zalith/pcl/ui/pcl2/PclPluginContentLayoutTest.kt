package com.zalith.pcl.ui.pcl2

import com.zalith.pcl.ui.pcl2.data.PclPluginBlock
import com.zalith.pcl.ui.pcl2.data.parsePclPluginEntries
import com.zalith.pcl.ui.pcl2.data.parsePclPluginPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PclPluginContentLayoutTest {
    @Test
    fun parsesMainPaneEntryAndPinnedPanelActions() {
        val json = """
            {
              "pages": [{
                "id": "panel",
                "title": "Panel",
                "layout": "panel",
                "blocks": [{"type":"icon","resource":"pcl_block_grass","size":64}],
                "footer": [{"type":"button","text":"Start","height":54}]
              }],
              "entries": [{"section":"download","label":"Versions","page":"panel","display":"content"}]
            }
        """.trimIndent()

        val page = parsePclPluginPages(json, "sample-plugin").single()
        val entry = parsePclPluginEntries(json, "sample-plugin").single()

        assertEquals("panel", page.layout)
        assertTrue(page.blocks.single() is PclPluginBlock.Icon)
        assertEquals(64, (page.blocks.single() as PclPluginBlock.Icon).size)
        assertEquals(54, (page.footerBlocks.single() as PclPluginBlock.Button).height)
        assertEquals("content", entry.display)
        assertEquals("sample-plugin", entry.pluginId)
    }
}
