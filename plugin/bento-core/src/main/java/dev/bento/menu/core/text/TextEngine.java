package dev.bento.menu.core.text;

import dev.bento.menu.api.v1.theme.Theme;
import dev.bento.menu.core.util.HexColors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Small, dependency-free text pipeline.
 *
 * <p>Supported syntax:</p>
 * <ul>
 *   <li>Legacy {@code &} / {@code §} colours, formatting and hex
 *       ({@code &#RRGGBB}, {@code &x&R&R&G&G&B&B})</li>
 *   <li>{@code <gradient:#aabbcc:#ddeeff>text</gradient>},
 *       {@code <gradient:primary:secondary>text</gradient>}</li>
 *   <li>{@code <rainbow>text</rainbow>}, {@code <color:#aabbcc>} /
 *       {@code <color:primary>}</li>
 *   <li>{@code <bold>}, {@code <italic>}, {@code <underlined>},
 *       {@code <strikethrough>}, {@code <obfuscated>}, {@code <reset>},
 *       {@code <br>} and short aliases</li>
 * </ul>
 *
 * <p>The output is a legacy section-sign string, which is accepted by every
 * supported server version (1.18–26.x) and by vanilla clients without mods.</p>
 */
public final class TextEngine {

    private static final int MAX_CACHE = 4096;
    private static final String[] LEGACY_COLORS = {
            "000000", "0000AA", "00AA00", "00AAAA", "AA0000", "AA00AA", "FFAA00", "AAAAAA",
            "555555", "5555FF", "55FF55", "55FFFF", "FF5555", "FF55FF", "FFFF55", "FFFFFF"
    };
    private static final Theme PLAIN_THEME = Theme.builder("__plain", "plain").build();

    private final ConcurrentHashMap<String, List<StyledChar>> cache = new ConcurrentHashMap<>();

    public RenderedText render(String template, Theme theme, UnaryOperator<String> placeholderResolver) {
        if (template == null) {
            return RenderedText.EMPTY;
        }
        String resolved = placeholderResolver == null ? template : placeholderResolver.apply(template);
        if (resolved == null) {
            resolved = "";
        }
        Theme effectiveTheme = theme == null ? PLAIN_THEME : theme;
        String key = System.identityHashCode(effectiveTheme) + "\u0000" + resolved;
        List<StyledChar> chars = cache.get(key);
        if (chars == null) {
            List<StyledChar> parsed = List.copyOf(parse(resolved, effectiveTheme));
            if (cache.size() >= MAX_CACHE) {
                cache.clear();
            }
            List<StyledChar> previous = cache.putIfAbsent(key, parsed);
            chars = previous == null ? parsed : previous;
        }
        return serialize(chars);
    }

    public String strip(String template) {
        if (template == null) {
            return "";
        }
        return render(template, PLAIN_THEME, UnaryOperator.identity()).plain();
    }

    private List<StyledChar> parse(String input, Theme theme) {
        return parseRange(input, 0, input.length(), Style.DEFAULT, theme);
    }

    private List<StyledChar> parseRange(String input, int start, int end, Style base, Theme theme) {
        List<StyledChar> out = new ArrayList<>(Math.max(8, end - start));
        Style current = base;
        int i = start;
        while (i < end) {
            char c = input.charAt(i);
            if (c == '&' || c == '\u00a7') {
                if (i + 1 < end && input.charAt(i + 1) == c) {
                    out.add(new StyledChar(c, current));
                    i += 2;
                    continue;
                }
                LegacyResult legacy = parseLegacy(input, i, end, current);
                if (legacy != null) {
                    current = legacy.style;
                    i = legacy.next;
                    continue;
                }
            }
            if (c == '<') {
                int close = input.indexOf('>', i + 1);
                if (close > i && close < end) {
                    String rawTag = input.substring(i + 1, close).trim();
                    Tag tag = Tag.parse(rawTag);
                    if (tag != null && tag.closing) {
                        // An unmatched closing tag is treated literally.
                        out.add(new StyledChar('<', current));
                        i++;
                        continue;
                    }
                    if (tag != null) {
                        if (tag.name.equals("reset")) {
                            current = Style.DEFAULT;
                            i = close + 1;
                            continue;
                        }
                        if (tag.name.equals("br") || tag.name.equals("newline")) {
                            out.add(new StyledChar('\n', current));
                            i = close + 1;
                            continue;
                        }
                        if (tag.container) {
                            int[] match = findClosing(input, close + 1, end, tag.name);
                            if (match != null) {
                                Style innerBase = styleFor(tag, current, theme);
                                List<StyledChar> inner = parseRange(input, close + 1, match[0], innerBase, theme);
                                if (tag.name.equals("gradient")) {
                                    applyGradient(inner, tag.args, theme);
                                } else if (tag.name.equals("rainbow")) {
                                    applyRainbow(inner);
                                }
                                out.addAll(inner);
                                i = match[1];
                                continue;
                            }
                        }
                    }
                }
            }
            out.add(new StyledChar(c, current));
            i++;
        }
        return out;
    }

    private Style styleFor(Tag tag, Style current, Theme theme) {
        return switch (tag.name) {
            case "gradient", "rainbow" -> current.withColor(null);
            case "color" -> {
                Integer color = resolveColor(tag.args.isEmpty() ? "text" : tag.args.get(0), theme);
                yield current.withColor(color);
            }
            case "bold", "b" -> current.withBold(true);
            case "italic", "i" -> current.withItalic(true);
            case "underlined", "u" -> current.withUnderline(true);
            case "strikethrough", "st" -> current.withStrike(true);
            case "obfuscated", "obf" -> current.withObfuscated(true);
            default -> current.withColor(resolveColor(tag.name, theme));
        };
    }

    private void applyGradient(List<StyledChar> chars, List<String> args, Theme theme) {
        int fromRgb;
        int toRgb;
        if (args.isEmpty()) {
            List<String> gradient = theme.titleGradient();
            fromRgb = resolveColor(gradient.isEmpty() ? "primary" : gradient.get(0), theme, 0x7AD7FF);
            toRgb = resolveColor(gradient.size() < 2 ? "secondary" : gradient.get(1), theme, 0xB39DFF);
        } else {
            fromRgb = resolveColor(args.get(0), theme, 0x7AD7FF);
            toRgb = resolveColor(args.size() < 2 ? args.get(0) : args.get(1), theme, 0xB39DFF);
        }
        int total = 0;
        for (StyledChar sc : chars) {
            if (sc.style.color == null) {
                total++;
            }
        }
        if (total == 0) {
            return;
        }
        int index = 0;
        for (int i = 0; i < chars.size(); i++) {
            StyledChar sc = chars.get(i);
            if (sc.style.color == null) {
                double ratio = total == 1 ? 0.0 : (double) index / (double) (total - 1);
                chars.set(i, new StyledChar(sc.value, sc.style.withColor(HexColors.interpolate(fromRgb, toRgb, ratio))));
                index++;
            }
        }
    }

    private void applyRainbow(List<StyledChar> chars) {
        int total = 0;
        for (StyledChar sc : chars) {
            if (sc.style.color == null) {
                total++;
            }
        }
        if (total == 0) {
            return;
        }
        int index = 0;
        for (int i = 0; i < chars.size(); i++) {
            StyledChar sc = chars.get(i);
            if (sc.style.color == null) {
                float hue = total == 1 ? 0.0f : (float) index / (float) total;
                int rgb = java.awt.Color.HSBtoRGB(hue, 0.72f, 1.0f);
                chars.set(i, new StyledChar(sc.value, sc.style.withColor(rgb)));
                index++;
            }
        }
    }

    private int resolveColor(String token, Theme theme, int fallback) {
        if (token == null || token.isBlank()) {
            return fallback;
        }
        String value = token.trim();
        Integer direct = HexColors.parse(value);
        if (direct != null) {
            return direct;
        }
        String themed = theme.color(value, null);
        if (themed != null) {
            Integer parsed = HexColors.parse(themed);
            if (parsed != null) {
                return parsed;
            }
        }
        return fallback;
    }

    private Integer resolveColor(String token, Theme theme) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String value = token.trim();
        Integer direct = HexColors.parse(value);
        if (direct != null) {
            return direct;
        }
        String themed = theme.color(value, null);
        return themed == null ? null : HexColors.parse(themed);
    }

    private int[] findClosing(String input, int from, int end, String name) {
        int depth = 1;
        int i = from;
        while (i < end) {
            int open = input.indexOf('<', i);
            if (open < 0 || open >= end) {
                return null;
            }
            int close = input.indexOf('>', open + 1);
            if (close < 0 || close >= end) {
                return null;
            }
            Tag tag = Tag.parse(input.substring(open + 1, close).trim());
            if (tag != null && tag.name.equals(name)) {
                if (tag.closing) {
                    depth--;
                    if (depth == 0) {
                        return new int[]{open, close + 1};
                    }
                } else if (tag.container) {
                    depth++;
                }
            }
            i = close + 1;
        }
        return null;
    }

    private LegacyResult parseLegacy(String input, int start, int end, Style style) {
        if (start + 1 >= end) {
            return null;
        }
        char code = Character.toLowerCase(input.charAt(start + 1));
        if (code == '#') {
            if (start + 8 <= end) {
                Integer rgb = HexColors.parse(input.substring(start + 2, start + 8));
                if (rgb != null) {
                    return new LegacyResult(style.withColor(rgb), start + 8);
                }
            }
            return null;
        }
        if (code == 'x') {
            int cursor = start + 2;
            StringBuilder hex = new StringBuilder(6);
            for (int group = 0; group < 6; group++) {
                if (cursor + 1 >= end || (input.charAt(cursor) != '&' && input.charAt(cursor) != '\u00a7')) {
                    return null;
                }
                char hexChar = input.charAt(cursor + 1);
                if (Character.digit(hexChar, 16) < 0) {
                    return null;
                }
                hex.append(hexChar);
                cursor += 2;
            }
            Integer rgb = HexColors.parse(hex.toString());
            return rgb == null ? null : new LegacyResult(style.withColor(rgb), cursor);
        }
        if (code >= '0' && code <= '9' || code >= 'a' && code <= 'f') {
            int index = code <= '9' ? code - '0' : code - 'a' + 10;
            return new LegacyResult(style.withColor(Integer.parseInt(LEGACY_COLORS[index], 16)), start + 2);
        }
        return switch (code) {
            case 'k' -> new LegacyResult(style.withObfuscated(true), start + 2);
            case 'l' -> new LegacyResult(style.withBold(true), start + 2);
            case 'm' -> new LegacyResult(style.withStrike(true), start + 2);
            case 'n' -> new LegacyResult(style.withUnderline(true), start + 2);
            case 'o' -> new LegacyResult(style.withItalic(true), start + 2);
            case 'r' -> new LegacyResult(Style.DEFAULT, start + 2);
            default -> null;
        };
    }

    private RenderedText serialize(List<StyledChar> chars) {
        StringBuilder legacy = new StringBuilder(chars.size() + 16);
        StringBuilder plain = new StringBuilder(chars.size());
        Style emitted = Style.DEFAULT;
        for (StyledChar sc : chars) {
            Style style = sc.style;
            boolean needsReset = (emitted.color != null && style.color == null)
                    || (emitted.bold && !style.bold)
                    || (emitted.italic && !style.italic)
                    || (emitted.underline && !style.underline)
                    || (emitted.strike && !style.strike)
                    || (emitted.obfuscated && !style.obfuscated);
            if (needsReset) {
                legacy.append("\u00a7r");
                emitted = Style.DEFAULT;
            }
            if (style.color != null && !Objects.equals(style.color, emitted.color)) {
                legacy.append(HexColors.legacyHex(style.color));
                emitted = new Style(style.color, false, false, false, false, false);
            }
            if (style.bold && !emitted.bold) {
                legacy.append("\u00a7l");
                emitted = emitted.withBold(true);
            }
            if (style.italic && !emitted.italic) {
                legacy.append("\u00a7o");
                emitted = emitted.withItalic(true);
            }
            if (style.underline && !emitted.underline) {
                legacy.append("\u00a7n");
                emitted = emitted.withUnderline(true);
            }
            if (style.strike && !emitted.strike) {
                legacy.append("\u00a7m");
                emitted = emitted.withStrike(true);
            }
            if (style.obfuscated && !emitted.obfuscated) {
                legacy.append("\u00a7k");
                emitted = emitted.withObfuscated(true);
            }
            legacy.append(sc.value);
            plain.append(sc.value);
        }
        return new RenderedText(legacy.toString(), plain.toString());
    }

    private record LegacyResult(Style style, int next) {
    }

    private record StyledChar(char value, Style style) {
    }

    private static final class Style {
        static final Style DEFAULT = new Style(null, false, false, false, false, false);

        final Integer color;
        final boolean bold;
        final boolean italic;
        final boolean underline;
        final boolean strike;
        final boolean obfuscated;

        Style(Integer color, boolean bold, boolean italic, boolean underline, boolean strike, boolean obfuscated) {
            this.color = color;
            this.bold = bold;
            this.italic = italic;
            this.underline = underline;
            this.strike = strike;
            this.obfuscated = obfuscated;
        }

        Style withColor(Integer value) {
            return new Style(value, bold, italic, underline, strike, obfuscated);
        }

        Style withBold(boolean value) {
            return new Style(color, value, italic, underline, strike, obfuscated);
        }

        Style withItalic(boolean value) {
            return new Style(color, bold, value, underline, strike, obfuscated);
        }

        Style withUnderline(boolean value) {
            return new Style(color, bold, italic, value, strike, obfuscated);
        }

        Style withStrike(boolean value) {
            return new Style(color, bold, italic, underline, value, obfuscated);
        }

        Style withObfuscated(boolean value) {
            return new Style(color, bold, italic, underline, strike, value);
        }
    }

    private record Tag(String name, List<String> args, boolean closing, boolean container) {

        static Tag parse(String raw) {
            if (raw == null || raw.isEmpty()) {
                return null;
            }
            String value = raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
            boolean closing = value.startsWith("/");
            if (closing) {
                value = value.substring(1);
            }
            String[] split = value.split(":", 3);
            String name = split[0].trim().toLowerCase(Locale.ROOT);
            if (!isKnown(name)) {
                return null;
            }
            List<String> args;
            if (split.length == 1) {
                args = List.of();
            } else if (split.length == 2) {
                args = List.of(split[1]);
            } else {
                args = List.of(split[1], split[2]);
            }
            boolean container = switch (name) {
                case "gradient", "rainbow", "color", "bold", "b", "italic", "i",
                        "underlined", "u", "strikethrough", "st", "obfuscated", "obf",
                        "primary", "secondary", "accent", "text", "muted", "success",
                        "warning", "error" -> true;
                default -> false;
            };
            return new Tag(name, args, closing, container);
        }

        private static boolean isKnown(String name) {
            return switch (name) {
                case "gradient", "rainbow", "color", "bold", "b", "italic", "i",
                        "underlined", "u", "strikethrough", "st", "obfuscated", "obf",
                        "primary", "secondary", "accent", "text", "muted", "success",
                        "warning", "error", "reset", "br", "newline" -> true;
                default -> false;
            };
        }
    }
}
