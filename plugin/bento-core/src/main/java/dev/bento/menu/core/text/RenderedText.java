package dev.bento.menu.core.text;

/**
 * Result of rendering a text template.
 */
public final class RenderedText {

    public static final RenderedText EMPTY = new RenderedText("", "");

    private final String legacy;
    private final String plain;

    public RenderedText(String legacy, String plain) {
        this.legacy = legacy;
        this.plain = plain;
    }

    public String legacy() {
        return legacy;
    }

    public String plain() {
        return plain;
    }

    public int plainLength() {
        return plain.length();
    }

    @Override
    public String toString() {
        return legacy;
    }
}
