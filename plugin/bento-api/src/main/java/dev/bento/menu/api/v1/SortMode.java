package dev.bento.menu.api.v1;

/**
 * Built-in entry ordering. Price sorting reads {@code metadata.price}.
 */
public enum SortMode {
    NONE,
    ID,
    NAME,
    PRICE_ASC,
    PRICE_DESC
}
