package dev.bento.menu.api.v1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable container layout. The pattern is nine characters per row; each
 * distinct character maps to a {@link RegionType}. Unmapped characters are
 * treated as {@link RegionType#EMPTY}.
 */
public final class Layout {

    public enum RegionType {
        BORDER,
        BACKGROUND,
        CONTENT,
        SEARCH,
        PREVIOUS,
        NEXT,
        PAGE,
        CATEGORY,
        CLOSE,
        EMPTY
    }

    private final int rows;
    private final List<String> pattern;
    private final Map<Character, RegionType> regions;

    private Layout(int rows, List<String> pattern, Map<Character, RegionType> regions) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be between 1 and 6, got " + rows);
        }
        if (pattern.size() != rows) {
            throw new IllegalArgumentException("pattern must contain exactly " + rows + " rows");
        }
        for (int i = 0; i < pattern.size(); i++) {
            String row = pattern.get(i);
            if (row.length() != 9) {
                throw new IllegalArgumentException("pattern row " + i + " must be 9 characters, got " + row.length());
            }
        }
        this.rows = rows;
        this.pattern = List.copyOf(pattern);
        this.regions = Collections.unmodifiableMap(new LinkedHashMap<>(regions));
    }

    public int rows() {
        return rows;
    }

    public int size() {
        return rows * 9;
    }

    public List<String> pattern() {
        return pattern;
    }

    public Map<Character, RegionType> regions() {
        return regions;
    }

    public RegionType regionAt(int slot) {
        if (slot < 0 || slot >= size()) {
            return RegionType.EMPTY;
        }
        char symbol = pattern.get(slot / 9).charAt(slot % 9);
        return regions.getOrDefault(symbol, RegionType.EMPTY);
    }

    public List<Integer> slotsOf(RegionType type) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < size(); slot++) {
            if (regionAt(slot) == type) {
                slots.add(slot);
            }
        }
        return List.copyOf(slots);
    }

    public static Builder builder(int rows) {
        return new Builder(rows);
    }

    public static final class Builder {
        private final int rows;
        private final List<String> pattern = new ArrayList<>();
        private final Map<Character, RegionType> regions = new LinkedHashMap<>();

        private Builder(int rows) {
            this.rows = rows;
        }

        public Builder row(String row) {
            pattern.add(row);
            return this;
        }

        public Builder rows(String... rows) {
            Collections.addAll(pattern, rows);
            return this;
        }

        public Builder region(char symbol, RegionType type) {
            regions.put(symbol, type);
            return this;
        }

        public Builder border(char symbol) {
            return region(symbol, RegionType.BORDER);
        }

        public Builder content(char symbol) {
            return region(symbol, RegionType.CONTENT);
        }

        public Layout build() {
            return new Layout(rows, pattern, regions);
        }
    }
}
