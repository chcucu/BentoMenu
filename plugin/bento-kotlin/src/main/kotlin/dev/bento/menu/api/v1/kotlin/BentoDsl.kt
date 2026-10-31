package dev.bento.menu.api.v1.kotlin

import dev.bento.menu.api.v1.BentoApi
import dev.bento.menu.api.v1.BentoApiProvider
import dev.bento.menu.api.v1.Layout
import dev.bento.menu.api.v1.MenuBuilder
import dev.bento.menu.api.v1.MenuEntry
import dev.bento.menu.api.v1.MenuItem
import dev.bento.menu.api.v1.action.Action
import dev.bento.menu.api.v1.action.ActionContext
import dev.bento.menu.api.v1.action.Condition
import dev.bento.menu.api.v1.action.ConditionContext
import org.bukkit.Material

/**
 * Kotlin DSL facade for the stable v1 API.
 *
 * Example:
 * ```
 * val menu = bentoMenu("example") {
 *     title("<gradient:primary:secondary>Demo</gradient>")
 *     rows(3)
 *     layout(bentoLayout(3) {
 *         row("#########")
 *         row("#CCCCCCC#")
 *         row("#########")
 *         border('#')
 *         content('C')
 *     })
 *     item(4, bentoItem(Material.DIAMOND) {
 *         name("<color:accent>Hello</color>")
 *         lore("<muted>Kotlin DSL</muted>")
 *         action(action {
 *             player.sendMessage("clicked")
 *         })
 *         cooldown(java.time.Duration.ofSeconds(1))
 *     })
 *     build()
 * }
 * BentoApiProvider.get().registerMenu(menu)
 * ```
 */

fun bentoMenu(id: String, block: MenuBuilder.() -> Unit): dev.bento.menu.api.v1.MenuDefinition =
    bentoMenu(BentoApiProvider.get(), id, block)

fun bentoMenu(api: BentoApi, id: String, block: MenuBuilder.() -> Unit): dev.bento.menu.api.v1.MenuDefinition =
    api.menuBuilder(id).apply(block).build()

fun bentoLayout(rows: Int, block: LayoutDsl.() -> Unit): Layout =
    LayoutDsl(rows).apply(block).build()

fun bentoItem(material: Material, block: MenuItem.Builder.() -> Unit): MenuItem =
    MenuItem.builder(material).apply(block).build()

fun bentoItem(item: org.bukkit.inventory.ItemStack, block: MenuItem.Builder.() -> Unit): MenuItem =
    MenuItem.from(item).apply(block).build()

fun bentoEntry(id: String, item: MenuItem, block: MenuEntry.Builder.() -> Unit = {}): MenuEntry =
    MenuEntry.builder(id, item).apply(block).build()

fun action(block: (ActionContext) -> Unit): Action = Action { block(it) }

fun condition(block: (ConditionContext) -> Boolean): Condition = Condition { block(it) }

class LayoutDsl internal constructor(private val rows: Int) {
    private val builder = Layout.builder(rows)

    fun row(pattern: String) {
        builder.row(pattern)
    }

    fun rows(vararg patterns: String) {
        builder.rows(*patterns)
    }

    fun border(symbol: Char) {
        builder.region(symbol, Layout.RegionType.BORDER)
    }

    fun background(symbol: Char) {
        builder.region(symbol, Layout.RegionType.BACKGROUND)
    }

    fun content(symbol: Char) {
        builder.region(symbol, Layout.RegionType.CONTENT)
    }

    fun region(symbol: Char, type: Layout.RegionType) {
        builder.region(symbol, type)
    }

    fun build(): Layout = builder.build()
}
