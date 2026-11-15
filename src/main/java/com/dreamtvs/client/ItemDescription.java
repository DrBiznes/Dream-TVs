package com.dreamtvs.client;

import com.dreamtvs.DreamTvs;
import com.dreamtvs.McCompat;
import com.dreamtvs.item.RemoteItem;
import com.google.common.collect.ImmutableList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Client only. Create-style item tooltips: "Hold [Shift] for Summary", which expands into a summary and
 * "When ..." condition/behaviour pairs, plus "Hold [Ctrl] for Controls" if an item has control/action keys.
 *
 * <p>This is a port of Create's {@code ItemDescription} and {@code TooltipHelper.cutTextComponent} (Create 6),
 * so it looks the same as Create and the many addons that copy it: same colours, same 200-pixel wrap, same
 * {@code _highlight_} markup and the same lang key layout, read from {@code <item key>.tooltip.*}:
 * <pre>
 * block.dreamtvs.lcd_panel.tooltip.summary
 * block.dreamtvs.lcd_panel.tooltip.condition1 / behaviour1, condition2 / behaviour2, ...
 * block.dreamtvs.lcd_panel.tooltip.control1 / action1, ...
 * </pre>
 * Items without a {@code .summary} key get no tooltip.
 */
public record ItemDescription(List<Component> lines, List<Component> linesOnShift, List<Component> linesOnCtrl) {
    /** Create's {@code Palette.STANDARD_CREATE}. */
    private static final Style PRIMARY = Style.EMPTY.withColor(0xC9974C);
    private static final Style HIGHLIGHT = Style.EMPTY.withColor(0xF1DD79);
    private static final int MAX_WIDTH_PER_LINE = 200;

    /** Built once per item and language; {@code null} values mean "no tooltip". */
    private static final Map<Item, ItemDescription> CACHE = new IdentityHashMap<>();
    private static String cachedLanguage;

    /**
     * Inserts our lines right under the item name, like Create. Call from each loader's tooltip event.
     * Skipped with no player, as Create does, so the creative search index isn't filled with the hint line.
     */
    public static void appendTo(ItemStack stack, List<Component> tooltip) {
        if (tooltip.isEmpty() || Minecraft.getInstance().player == null) return;
        Item item = stack.getItem();
        if (!BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(DreamTvs.MOD_ID)) return;

        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (!language.equals(cachedLanguage)) {
            cachedLanguage = language;
            CACHE.clear();
        }
        ItemDescription description = CACHE.computeIfAbsent(item, i -> create(i.getDescriptionId() + ".tooltip"));
        if (description == null) return;
        List<Component> lines = description.currentLines();
        tooltip.addAll(1, lines);
        // What this one stack is set to goes at the end of the summary, so it's hidden until Shift is held.
        if (lines == description.linesOnShift && item instanceof RemoteItem) {
            tooltip.add(1 + lines.size(), CommonComponents.EMPTY);
            tooltip.add(2 + lines.size(), remoteBinding(stack));
        }
    }

    /** "Bound to the TV at 12, 64, -30", with the dimension when it isn't the one the player is in. */
    private static Component remoteBinding(ItemStack stack) {
        RemoteItem.Binding binding = RemoteItem.binding(stack);
        if (binding == null) return Component.translatable("item.dreamtvs.remote.tooltip.unbound").withStyle(PRIMARY);
        Component where = Component.literal(binding.pos().getX() + ", " + binding.pos().getY() + ", " + binding.pos().getZ())
                .withStyle(HIGHLIGHT);
        var level = Minecraft.getInstance().level;
        if (level != null && binding.dimension().equals(McCompat.dimensionId(level))) {
            return Component.translatable("item.dreamtvs.remote.tooltip.bound", where).withStyle(PRIMARY);
        }
        return Component.translatable("item.dreamtvs.remote.tooltip.bound_in", where,
                Component.literal(binding.dimension()).withStyle(HIGHLIGHT)).withStyle(PRIMARY);
    }

    private List<Component> currentLines() {
        if (ClientCompat.hasShiftDown()) return linesOnShift;
        if (ClientCompat.hasControlDown()) return linesOnCtrl;
        return lines;
    }

    private static ItemDescription create(String key) {
        if (!Language.getInstance().has(key + ".summary")) return null;

        List<Component> lines = new ArrayList<>();
        List<Component> linesOnShift = new ArrayList<>(wrap(I18n.get(key + ".summary"), 0));
        List<Component> linesOnCtrl = new ArrayList<>();

        for (int i = 1; Language.getInstance().has(key + ".condition" + i); i++) {
            if (i == 1) linesOnShift.add(CommonComponents.EMPTY);
            linesOnShift.add(Component.literal(I18n.get(key + ".condition" + i)).withStyle(ChatFormatting.GRAY));
            linesOnShift.addAll(wrap(I18n.get(key + ".behaviour" + i), 1));
        }
        for (int i = 1; Language.getInstance().has(key + ".control" + i); i++) {
            linesOnCtrl.add(Component.literal(I18n.get(key + ".control" + i)).withStyle(ChatFormatting.GRAY));
            linesOnCtrl.addAll(wrap(I18n.get(key + ".action" + i), 1));
        }

        boolean hasControls = !linesOnCtrl.isEmpty();
        Component keyShift = Component.translatable("dreamtvs.tooltip.keyShift");
        Component keyCtrl = Component.translatable("dreamtvs.tooltip.keyCtrl");
        for (List<Component> list : List.of(lines, linesOnShift, linesOnCtrl)) {
            boolean shift = list == linesOnShift;
            boolean ctrl = list == linesOnCtrl;
            if (hasControls) list.add(0, holdLine("dreamtvs.tooltip.holdForControls", keyCtrl, ctrl));
            list.add(0, holdLine("dreamtvs.tooltip.holdForDescription", keyShift, shift));
            if (shift || ctrl) list.add(hasControls ? 2 : 1, CommonComponents.EMPTY);
        }
        if (!hasControls) {
            linesOnCtrl.clear();
            linesOnCtrl.addAll(lines);
        }
        return new ItemDescription(ImmutableList.copyOf(lines), ImmutableList.copyOf(linesOnShift),
                ImmutableList.copyOf(linesOnCtrl));
    }

    /** "Hold [Shift] for Summary": dark grey, with the key grey, or white while it's held. */
    private static Component holdLine(String key, Component keyName, boolean held) {
        String[] parts = I18n.get(key, "$").split("\\$", -1);
        if (parts.length != 2) return Component.literal("Invalid lang formatting!");
        return Component.empty()
                .append(Component.literal(parts[0]).withStyle(ChatFormatting.DARK_GRAY))
                .append(keyName.plainCopy().withStyle(held ? ChatFormatting.WHITE : ChatFormatting.GRAY))
                .append(Component.literal(parts[1]).withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * Wraps {@code text} at {@link #MAX_WIDTH_PER_LINE} pixels and colours it: text between underscores is
     * highlighted, the rest is the primary colour. A highlight can carry on across a line break.
     */
    private static List<Component> wrap(String text, int indent) {
        // Create uses NeoForge's Minecraft.getLocale(); this is the same thing, also available on Fabric.
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        BreakIterator iterator = BreakIterator.getLineInstance(Locale.forLanguageTag(language.replace('_', '-')));
        iterator.setText(text);
        Font font = Minecraft.getInstance().font;
        List<String> rows = new ArrayList<>();
        StringBuilder row = new StringBuilder();
        int width = 0;
        for (int start = iterator.first(), end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String word = text.substring(start, end);
            int wordWidth = font.width(word.replace("_", ""));
            if (width + wordWidth > MAX_WIDTH_PER_LINE) {
                if (width == 0) {
                    rows.add(word);
                    continue;
                }
                rows.add(row.toString());
                row.setLength(0);
                width = 0;
            }
            row.append(word);
            width += wordWidth;
        }
        if (width > 0) rows.add(row.toString());

        List<Component> formatted = new ArrayList<>(rows.size());
        boolean highlighted = false;
        for (String r : rows) {
            MutableComponent line = Component.literal(" ".repeat(indent)).withStyle(PRIMARY);
            for (String part : r.split("_", -1)) {
                line.append(Component.literal(part).withStyle(highlighted ? HIGHLIGHT : PRIMARY));
                highlighted = !highlighted;
            }
            formatted.add(line);
            highlighted = !highlighted;
        }
        return formatted;
    }
}
