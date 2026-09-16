package it.faccioio.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPickerPolicyTest {

    @Test
    fun usesTheExactOpenStreetMapTileEndpoint() {
        assertEquals(
            "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
            OSM_TILE_URL
        )
    }

    @Test
    fun userAgentIdentifiesTheApplication() {
        assertTrue(MAP_USER_AGENT.startsWith("FaccioIo/0.3.31"))
        assertTrue(MAP_USER_AGENT.contains("github.com/yurigrelu78-lab/Faccio-io0.3"))
    }
}
