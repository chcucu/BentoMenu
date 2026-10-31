package dev.bento.menu.core.menu;

import dev.bento.menu.api.v1.Layout;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutAndCooldownTest {

    private Layout shopLayout() {
        return Layout.builder(5)
                .row("#########")
                .row("#CCCCCCC#")
                .row("#CCCCCCC#")
                .row("#KKKKKKK#")
                .row("#S#<P>#X#")
                .region('#', Layout.RegionType.BORDER)
                .region('C', Layout.RegionType.CONTENT)
                .region('K', Layout.RegionType.CATEGORY)
                .region('S', Layout.RegionType.SEARCH)
                .region('<', Layout.RegionType.PREVIOUS)
                .region('P', Layout.RegionType.PAGE)
                .region('>', Layout.RegionType.NEXT)
                .region('X', Layout.RegionType.CLOSE)
                .build();
    }

    @Test
    void resolvesRegionSlots() {
        Layout layout = shopLayout();
        assertEquals(45, layout.size());
        assertEquals(14, layout.slotsOf(Layout.RegionType.CONTENT).size());
        assertEquals(1, layout.slotsOf(Layout.RegionType.SEARCH).size());
        assertEquals(List.of(37), layout.slotsOf(Layout.RegionType.SEARCH));
        assertEquals(List.of(39), layout.slotsOf(Layout.RegionType.PREVIOUS));
        assertEquals(List.of(41), layout.slotsOf(Layout.RegionType.NEXT));
        assertEquals(List.of(43), layout.slotsOf(Layout.RegionType.CLOSE));
        assertEquals(Layout.RegionType.BORDER, layout.regionAt(0));
        assertEquals(Layout.RegionType.EMPTY, layout.regionAt(999));
    }

    @Test
    void rejectsInvalidPatterns() {
        assertThrows(IllegalArgumentException.class, () -> Layout.builder(3).row("#########").build());
        assertThrows(IllegalArgumentException.class, () -> Layout.builder(2).row("#########").build());
        assertThrows(IllegalArgumentException.class, () -> Layout.builder(1).row("#######").build());
        assertThrows(IllegalArgumentException.class, () -> Layout.builder(0).build());
    }

    @Test
    void autoLayoutHasBorderAndContent() {
        Layout layout = AutoLayouts.bordered(6);
        assertEquals(6, layout.rows());
        assertEquals(4 * 7, layout.slotsOf(Layout.RegionType.CONTENT).size());
    }

    @Test
    void cooldownExpiresAndDebounces() {
        CooldownService service = new CooldownService();
        UUID id = UUID.randomUUID();
        service.set(id, "buy", Duration.ofMillis(120));
        long remaining = service.remainingMillis(id, "buy");
        assertTrue(remaining > 0);
        assertTrue(service.acquireClick(id, Duration.ofMillis(100)));
        assertFalse(service.acquireClick(id, Duration.ofMillis(100)));
        try {
            Thread.sleep(150);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        assertEquals(0, service.remainingMillis(id, "buy"));
        assertTrue(service.acquireClick(id, Duration.ofMillis(50)));
        service.clear(id);
        assertNull(service.remainingMillis(id, "buy") == 0 ? null : "still-active");
    }

    @Test
    void sortModeEnumIsStable() {
        assertEquals("PRICE_ASC", dev.bento.menu.api.v1.SortMode.PRICE_ASC.name());
    }
}
