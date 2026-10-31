package dev.bento.menu.api.v1;

/**
 * Optional state used when opening a menu programmatically.
 */
public final class MenuOpenOptions {

    private final int page;
    private final String category;
    private final String query;
    private final String themeOverride;
    private final boolean silent;

    private MenuOpenOptions(Builder builder) {
        this.page = Math.max(1, builder.page);
        this.category = builder.category;
        this.query = builder.query;
        this.themeOverride = builder.themeOverride;
        this.silent = builder.silent;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int page() {
        return page;
    }

    public String category() {
        return category;
    }

    public String query() {
        return query;
    }

    /** Theme used for this view only; does not change the player's preference. */
    public String themeOverride() {
        return themeOverride;
    }

    public boolean silent() {
        return silent;
    }

    public static final class Builder {
        private int page = 1;
        private String category;
        private String query;
        private String themeOverride;
        private boolean silent;

        public Builder page(int page) {
            this.page = page;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder themeOverride(String themeId) {
            this.themeOverride = themeId;
            return this;
        }

        public Builder silent(boolean silent) {
            this.silent = silent;
            return this;
        }

        public MenuOpenOptions build() {
            return new MenuOpenOptions(this);
        }
    }
}
