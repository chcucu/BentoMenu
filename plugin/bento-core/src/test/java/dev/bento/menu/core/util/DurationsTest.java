package dev.bento.menu.core.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurationsTest {

    @Test
    void parsesHumanDurations() {
        assertEquals(Duration.ofMillis(500), Durations.parse("500ms", null));
        assertEquals(Duration.ofSeconds(3), Durations.parse("3s", null));
        assertEquals(Duration.ofSeconds(90), Durations.parse("1m30s", null));
        assertEquals(Duration.ofHours(2), Durations.parse("2h", null));
        assertEquals(Duration.ofDays(1), Durations.parse("1d", null));
        assertEquals(Duration.ofSeconds(5), Durations.parse("5", null));
    }

    @Test
    void parsesIsoAndDisabledValues() {
        assertEquals(Duration.ofSeconds(4), Durations.parse("PT4S", null));
        assertEquals(Duration.ZERO, Durations.parse("off", null));
        assertEquals(Duration.ZERO, Durations.parse("0", null));
    }

    @Test
    void fallsBackOnGarbage() {
        assertEquals(Duration.ofSeconds(2), Durations.parse("nonsense", Duration.ofSeconds(2)));
        assertNull(Durations.parse("nonsense", null));
    }

    @Test
    void convertsToTicks() {
        assertEquals(20, Durations.toTicks(Duration.ofSeconds(1)));
        assertEquals(1, Durations.toTicks(Duration.ofMillis(1)));
        assertEquals(0, Durations.toTicks(Duration.ZERO));
        assertEquals(0, Durations.toTicks(null));
    }

    @Test
    void comparesNumbers() {
        assertTrue(Comparison.parse(">=", Comparison.EQUAL).test(5, 5));
        assertTrue(Comparison.parse("<", Comparison.EQUAL).test(4, 5));
        assertTrue(Comparison.parse("!=", Comparison.EQUAL).test(4, 5));
    }

    @Test
    void picksNearestDyeColour() {
        assertEquals("WHITE_STAINED_GLASS_PANE", HexColors.nearestStainedGlassPane(0xF0F0F0));
        assertEquals("BLACK_STAINED_GLASS_PANE", HexColors.nearestStainedGlassPane(0x101010));
        assertEquals("RED_STAINED_GLASS_PANE", HexColors.nearestStainedGlassPane(0xB1341C));
    }

    @Test
    void buildsLegacyHex() {
        assertEquals("\u00a7x\u00a71\u00a72\u00a7a\u00a7b\u00a73\u00a74", HexColors.legacyHex(0x12AB34));
    }
}
