package dev.bento.menu.core.text;

import dev.bento.menu.api.v1.theme.Theme;
import org.junit.jupiter.api.Test;

import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextEngineTest {

    private final TextEngine engine = new TextEngine();
    private final Theme theme = Theme.builder("test", "Test").color("primary", "#FF0000")
            .color("secondary", "#0000FF").build();

    @Test
    void rendersLegacyColours() {
        RenderedText text = engine.render("&aHello &lWorld", theme, null);
        assertEquals("Hello World", text.plain());
        assertTrue(text.legacy().contains("\u00a7x"));
    }

    @Test
    void rendersThemeGradient() {
        RenderedText text = engine.render("<gradient:primary:secondary>ABCD</gradient>", theme, null);
        assertEquals("ABCD", text.plain());
        long hexSequences = text.legacy().chars().filter(c -> c == '\u00a7').count();
        assertTrue(hexSequences >= 8, "each character should carry a hex colour");
    }

    @Test
    void rendersNestedFormattingAndBreaks() {
        RenderedText text = engine.render("<bold>a<br>b</bold>", theme, null);
        assertEquals("a\nb", text.plain());
        assertTrue(text.legacy().contains("\u00a7l"));
    }

    @Test
    void leavesUnknownTagsLiteral() {
        assertEquals("<unknown>x", engine.strip("<unknown>x"));
    }

    @Test
    void appliesPlaceholderResolver() {
        RenderedText text = engine.render("{player} &7hi", theme,
                (UnaryOperator<String>) value -> value.replace("{player}", "Steve"));
        assertEquals("Steve hi", text.plain());
    }

    @Test
    void cachesRepeatedRenders() {
        String template = "<gradient>cache-me</gradient>";
        RenderedText first = engine.render(template, theme, null);
        RenderedText second = engine.render(template, theme, null);
        assertEquals(first.legacy(), second.legacy());
    }

    @Test
    void hexLegacyCodeIsParsed() {
        RenderedText text = engine.render("&#12AB34x", theme, null);
        assertEquals("x", text.plain());
        assertTrue(text.legacy().contains("\u00a7x\u00a71\u00a72"));
    }

    @Test
    void resetStopsFormatting() {
        RenderedText text = engine.render("&lbold&rnormal", theme, null);
        assertEquals("boldnormal", text.plain());
        assertTrue(text.legacy().contains("\u00a7r"));
    }
}
