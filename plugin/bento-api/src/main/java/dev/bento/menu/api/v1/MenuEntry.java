package dev.bento.menu.api.v1;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable content entry rendered inside a content region.
 */
public final class MenuEntry {

    private final String id;
    private final MenuItem item;
    private final String category;
    private final Set<String> tags;
    private final Map<String, Object> metadata;
    private final String searchText;

    private MenuEntry(Builder builder) {
        this.id = builder.id;
        this.item = builder.item;
        this.category = builder.category;
        this.tags = Collections.unmodifiableSet(new LinkedHashSet<>(builder.tags));
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
        this.searchText = builder.searchText;
    }

    public static Builder builder(String id, MenuItem item) {
        return new Builder(id, item);
    }

    public String id() {
        return id;
    }

    public MenuItem item() {
        return item;
    }

    public String category() {
        return category;
    }

    public Set<String> tags() {
        return tags;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    /** Explicit search text, or {@code null} to derive it from id/name/tags/metadata. */
    public String searchText() {
        return searchText;
    }

    public static final class Builder {
        private final String id;
        private final MenuItem item;
        private final Set<String> tags = new LinkedHashSet<>();
        private final Map<String, Object> metadata = new LinkedHashMap<>();
        private String category;
        private String searchText;

        private Builder(String id, MenuItem item) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("entry id must not be blank");
            }
            if (item == null) {
                throw new IllegalArgumentException("entry item must not be null");
            }
            this.id = id;
            this.item = item;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder tag(String tag) {
            if (tag != null && !tag.isBlank()) {
                this.tags.add(tag);
            }
            return this;
        }

        public Builder tags(Collection<String> tags) {
            this.tags.clear();
            if (tags != null) {
                tags.forEach(this::tag);
            }
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder searchText(String text) {
            this.searchText = text;
            return this;
        }

        public MenuEntry build() {
            return new MenuEntry(this);
        }
    }
}
