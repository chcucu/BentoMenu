package dev.bento.menu.core.util;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * RGB helpers and a 16-colour palette used for no-resource-pack glass
 * gradients. The palette is vanilla dye RGB; no Bukkit enum lookups are needed
 * at parse time, which keeps older and future servers compatible.
 */
public final class HexColors {

    private static final Map<String, Integer> DYE_COLORS = new LinkedHashMap<>();

    static {
        DYE_COLORS.put("WHITE", 0xFFFFFF);
        DYE_COLORS.put("ORANGE", 0xF9801D);
        DYE_COLORS.put("MAGENTA", 0xC74EBD);
        DYE_COLORS.put("LIGHT_BLUE", 0x3AB3DA);
        DYE_COLORS.put("YELLOW", 0xFED83D);
        DYE_COLORS.put("LIME", 0x80C71F);
        DYE_COLORS.put("PINK", 0xF38BAA);
        DYE_COLORS.put("GRAY", 0x474F52);
        DYE_COLORS.put("LIGHT_GRAY", 0x9D9D97);
        DYE_COLORS.put("CYAN", 0x169C9C);
        DYE_COLORS.put("PURPLE", 0x8932B8);
        DYE_COLORS.put("BLUE", 0x3C44AA);
        DYE_COLORS.put("BROWN", 0x835432);
        DYE_COLORS.put("GREEN", 0x5E7C16);
        DYE_COLORS.put("RED", 0xB1341C);
        DYE_COLORS.put("BLACK", 0x1D1D21);
    }

    private HexColors() {
    }

    public static Integer parse(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() != 6 || !value.matches("[0-9a-fA-F]{6}")) {
            return null;
        }
        return Integer.parseInt(value, 16);
    }

    public static String format(int rgb) {
        return String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF);
    }

    public static int interpolate(int from, int to, double ratio) {
        double clamped = Math.max(0.0, Math.min(1.0, ratio));
        int r = (int) Math.round(((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * clamped));
        int g = (int) Math.round(((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * clamped));
        int b = (int) Math.round((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * clamped));
        return (r << 16) | (g << 8) | b;
    }

    public static String nearestDye(int rgb) {
        String best = "GRAY";
        long bestDistance = Long.MAX_VALUE;
        for (Map.Entry<String, Integer> entry : DYE_COLORS.entrySet()) {
            long distance = distanceSquared(rgb, entry.getValue());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry.getKey();
            }
        }
        return best;
    }

    public static String nearestStainedGlassPane(int rgb) {
        return nearestDye(rgb) + "_STAINED_GLASS_PANE";
    }

    private static long distanceSquared(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        return (long) dr * dr + (long) dg * dg + (long) db * db;
    }

    /** Minecraft section-sign hex sequence understood by 1.16+ clients. */
    public static String legacyHex(int rgb) {
        String hex = format(rgb).toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(14);
        builder.append('\u00a7').append('x');
        for (int i = 0; i < 6; i++) {
            builder.append('\u00a7').append(hex.charAt(i));
        }
        return builder.toString();
    }

    public static int[] hsToRgb(float hue, float saturation, float brightness) {
        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness);
        return new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
    }
}
