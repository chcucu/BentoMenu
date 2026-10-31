package dev.bento.menu.core.menu;

import dev.bento.menu.api.v1.Layout;

/**
 * Convenience layouts for programmatic menus that do not supply one.
 */
public final class AutoLayouts {

    private AutoLayouts() {
    }

    public static Layout bordered(int rows) {
        Layout.Builder builder = Layout.builder(rows);
        if (rows == 1) {
            builder.row("CCCCCCCCC").content('C');
            return builder.build();
        }
        builder.row("#########").border('#');
        for (int row = 1; row < rows - 1; row++) {
            builder.row("#CCCCCCC#");
        }
        if (rows == 2) {
            builder.row("#CCCCCCC#");
        } else {
            builder.row("#########");
        }
        return builder.content('C').build();
    }
}
