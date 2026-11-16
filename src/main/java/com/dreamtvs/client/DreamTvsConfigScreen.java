package com.dreamtvs.client;

import com.dreamtvs.DreamTvs;
import com.dreamtvs.compat.CompatIndex;
import com.dreamtvs.compat.CompatRecipes;
import com.dreamtvs.compat.CompatResolver;
import com.dreamtvs.compat.CompatSource;
import com.dreamtvs.config.DreamTvsClientConfig;
import com.dreamtvs.config.DreamTvsConfig;
import com.dreamtvs.config.DreamTvsConfig.ExtendPolicy;
import com.dreamtvs.net.RemoteRangePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The settings screen, opened from the mod list: Mod Menu on Fabric, the Config button on NeoForge. Vanilla
 * widgets only, so it needs no config library.
 *
 * <p>The first section is the client config ({@code config/dreamtvs-client.json}), which applies wherever the
 * player plays. The rest is this game's {@code config/dreamtvs.json}, the config for worlds hosted here
 * (singleplayer and LAN). Saving in such a world applies the changes at once; a dedicated server keeps its own
 * file, and the screen says so.
 */
public final class DreamTvsConfigScreen extends OptionsSubScreen {
    // ARGB: since 1.21.6 text drawn with no alpha is invisible.
    private static final int NOTE = 0xFFA0A0A0;
    private static final int ERROR = 0xFFFF5555;
    private static final int VALID = 0xFFE0E0E0;

    /** False when the file isn't valid JSON: the defaults are shown and Save replaces the file. */
    private final boolean fileReadable;
    private final DreamTvsConfig edit;
    private final DreamTvsClientConfig clientEdit = DreamTvsClientConfig.get().copy();
    private final String savedRecipeSource;
    private final boolean savedSequencedAssembly;
    // The number boxes' text, kept here so it survives the screen being rebuilt.
    private String lcdWidthText;
    private String lcdHeightText;
    private String jumbotronWidthText;
    private String jumbotronHeightText;
    private String screensText;
    private String remoteRangeText;

    private final List<NumberBox> numberBoxes = new ArrayList<>();
    private Button saveButton;

    public DreamTvsConfigScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable("dreamtvs.config.title"));
        DreamTvsConfig read = DreamTvsConfig.readForEditing(DreamTvs.configFile());
        fileReadable = read != null;
        edit = read != null ? read : new DreamTvsConfig();
        savedRecipeSource = edit.recipeSource;
        savedSequencedAssembly = edit.createSequencedAssembly;
        lcdWidthText = Integer.toString(edit.lcdMaxWidth);
        lcdHeightText = Integer.toString(edit.lcdMaxHeight);
        jumbotronWidthText = Integer.toString(edit.jumbotronMaxWidth);
        jumbotronHeightText = Integer.toString(edit.jumbotronMaxHeight);
        screensText = Integer.toString(edit.maxScreensPerPlayer);
        remoteRangeText = Integer.toString(edit.remoteRange);
    }

    @Override
    protected void init() {
        numberBoxes.clear();
        super.init();
        updateSaveButton();
    }

    @Override
    protected void addOptions() {
        heading("dreamtvs.config.section.client");
        row("crtOverlay", CycleButton.onOffBuilder(clientEdit.crtOverlay)
                .withTooltip(on -> describe("crtOverlay"))
                .displayOnlyValue()
                .create(label("crtOverlay"), (button, on) -> clientEdit.crtOverlay = on));

        note(Component.translatable(contextKey()), NOTE);
        if (!fileReadable) note(Component.translatable("dreamtvs.config.note.unreadable"), ERROR);

        heading("dreamtvs.config.section.screens");
        numberRow("lcdMaxWidth", lcdWidthText, 1, DreamTvsConfig.MAX_SIDE, 3, text -> lcdWidthText = text);
        numberRow("lcdMaxHeight", lcdHeightText, 1, DreamTvsConfig.MAX_SIDE, 3, text -> lcdHeightText = text);
        numberRow("jumbotronMaxWidth", jumbotronWidthText, 1, DreamTvsConfig.MAX_SIDE, 3,
                text -> jumbotronWidthText = text);
        numberRow("jumbotronMaxHeight", jumbotronHeightText, 1, DreamTvsConfig.MAX_SIDE, 3,
                text -> jumbotronHeightText = text);
        numberRow("maxScreensPerPlayer", screensText, 0, Integer.MAX_VALUE, 10, text -> screensText = text);
        row("extendPolicy", ClientCompat.<ExtendPolicy>cycleButton(
                        policy -> Component.translatable("dreamtvs.config.extendPolicy." + key(policy)), edit.extendPolicy)
                .withValues(ExtendPolicy.values())
                .withTooltip(policy -> Tooltip.create(Component.translatable(
                        "dreamtvs.config.extendPolicy." + key(policy) + ".tooltip")))
                .displayOnlyValue()
                .create(label("extendPolicy"), (button, policy) -> edit.extendPolicy = policy));
        row("showFormMessages", CycleButton.onOffBuilder(edit.showFormMessages)
                .withTooltip(on -> describe("showFormMessages"))
                .displayOnlyValue()
                .create(label("showFormMessages"), (button, on) -> edit.showFormMessages = on));

        heading("dreamtvs.config.section.remote");
        numberRow("remoteRange", remoteRangeText, 0, DreamTvsConfig.MAX_REMOTE_RANGE, 4,
                text -> remoteRangeText = text);

        heading("dreamtvs.config.section.compat");
        List<String> sources = recipeSources();
        row("recipeSource", ClientCompat.cycleButton(this::sourceName, edit.recipeSource)
                .withValues(sources)
                .withTooltip(id -> Tooltip.create(sourceTooltip(id)))
                .displayOnlyValue()
                .create(label("recipeSource"), (button, id) -> edit.recipeSource = id));
        row("createSequencedAssembly", CycleButton.onOffBuilder(edit.createSequencedAssembly)
                .withTooltip(on -> describe("createSequencedAssembly"))
                .displayOnlyValue()
                .create(label("createSequencedAssembly"), (button, on) -> edit.createSequencedAssembly = on));
        note(Component.translatable("dreamtvs.config.note.recipes"), NOTE);
    }

    @Override
    protected void addFooter() {
        LinearLayout buttons = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        buttons.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose()).width(150).build());
        saveButton = buttons.addChild(Button.builder(Component.translatable("dreamtvs.config.save"), button -> save())
                .width(150).build());
    }

    /** Nothing to do: these aren't vanilla options (OptionsSubScreen would save options.txt here). */
    @Override
    public void removed() {}

    private void save() {
        if (!numberBoxes.stream().allMatch(NumberBox::valid)) return;
        edit.lcdMaxWidth = Integer.parseInt(lcdWidthText);
        edit.lcdMaxHeight = Integer.parseInt(lcdHeightText);
        edit.jumbotronMaxWidth = Integer.parseInt(jumbotronWidthText);
        edit.jumbotronMaxHeight = Integer.parseInt(jumbotronHeightText);
        edit.maxScreensPerPlayer = Integer.parseInt(screensText);
        edit.remoteRange = Integer.parseInt(remoteRangeText);
        try {
            DreamTvsClientConfig.save(clientEdit);
            DreamTvsConfig.save(DreamTvs.configFile(), edit);
        } catch (IOException e) {
            DreamTvs.LOGGER.error("Couldn't save the Dream TVs config", e);
            toast(Component.translatable("dreamtvs.config.toast.failed"), Component.literal(String.valueOf(e.getMessage())));
            return;
        }
        DreamTvs.LOGGER.info("Dream TVs config saved from the settings screen");
        // This game's own world uses the new range at once; other players in a LAN world get it when they rejoin.
        if (minecraft.hasSingleplayerServer()) RemoteRangePayload.receive(RemoteRangePayload.fromConfig());
        boolean recipesChanged = !edit.recipeSource.equals(savedRecipeSource)
                || edit.createSequencedAssembly != savedSequencedAssembly;
        if (recipesChanged && minecraft.hasSingleplayerServer()) {
            toast(Component.translatable("dreamtvs.config.toast.saved"),
                    Component.translatable("dreamtvs.config.toast.reload"));
        }
        onClose();
    }

    /** What the file is for, from where the screen was opened. */
    private String contextKey() {
        if (minecraft.hasSingleplayerServer()) return "dreamtvs.config.note.world";
        if (minecraft.level != null) return "dreamtvs.config.note.server";
        return "dreamtvs.config.note.menu";
    }

    // --- recipe sources ----------------------------------------------------------------------------------------

    /** auto, vanilla, then every source in priority order, plus the file's value if it names none of them. */
    private List<String> recipeSources() {
        List<String> ids = new ArrayList<>(List.of(CompatResolver.AUTO, CompatSource.VANILLA));
        for (CompatSource source : sources()) {
            if (!ids.contains(source.id())) ids.add(source.id());
        }
        if (!ids.contains(edit.recipeSource)) ids.add(edit.recipeSource);
        return ids;
    }

    private Component sourceName(String id) {
        if (id.equals(CompatResolver.AUTO) || id.equals(CompatSource.VANILLA)) {
            return Component.translatable("dreamtvs.config.recipeSource." + id);
        }
        CompatSource source = source(id);
        if (source == null) return Component.literal(id).withStyle(ChatFormatting.RED);
        Component name = Component.literal(source.name());
        return CompatRecipes.isInstalled(source) ? name : name.copy().withStyle(ChatFormatting.GRAY);
    }

    private Component sourceTooltip(String id) {
        if (id.equals(CompatResolver.AUTO) || id.equals(CompatSource.VANILLA)) {
            return Component.translatable("dreamtvs.config.recipeSource." + id + ".tooltip");
        }
        CompatSource source = source(id);
        if (source == null) return Component.translatable("dreamtvs.config.recipeSource.unknown", id);
        return Component.translatable(CompatRecipes.isInstalled(source)
                ? "dreamtvs.config.recipeSource.installed" : "dreamtvs.config.recipeSource.missing", source.name());
    }

    private static CompatSource source(String id) {
        for (CompatSource source : sources()) {
            if (source.id().equals(id)) return source;
        }
        return null;
    }

    private static List<CompatSource> sources() {
        try {
            return CompatIndex.sources();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    // --- rows ---------------------------------------------------------------------------------------------------

    private static Component label(String option) {
        return Component.translatable("dreamtvs.config." + option);
    }

    private static Tooltip describe(String option) {
        return Tooltip.create(Component.translatable("dreamtvs.config." + option + ".tooltip"));
    }

    private static String key(ExtendPolicy policy) {
        return policy.name().toLowerCase(Locale.ROOT);
    }

    /**
     * The option's name on the left, which describes the option on hover, and its control on the right. A cycle
     * button sets its own tooltip ({@code withTooltip}), since it replaces it whenever its value changes.
     */
    private void row(String option, AbstractWidget control) {
        StringWidget label = new StringWidget(150, 20, label(option), font);
        //? if <1.21.11
        label.alignLeft();
        label.setTooltip(describe(option));
        list.addSmall(label, control);
    }

    private void numberRow(String option, String text, int min, int max, int maxLength, Consumer<String> store) {
        EditBox box = new EditBox(font, 150, 20, label(option));
        box.setMaxLength(maxLength);
        // 26.x dropped EditBox filters; there a wrong character just turns the box red (see NumberBox).
        //? if <26.1
        box.setFilter(value -> value.chars().allMatch(Character::isDigit));
        box.setValue(text);
        box.setTooltip(describe(option));
        NumberBox number = new NumberBox(box, min, max);
        box.setResponder(value -> {
            store.accept(value);
            number.showValidity();
            updateSaveButton();
        });
        number.showValidity();
        numberBoxes.add(number);
        row(option, box);
    }

    private void heading(String key) {
        StringWidget heading = new StringWidget(310, 20,
                Component.translatable(key).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), font);
        list.addSmall(heading, null);
    }

    private void note(Component text, int color) {
        list.addSmall(new MultiLineTextWidget(text.copy().withColor(color), font).setMaxWidth(310).setMaxRows(2), null);
    }

    private void updateSaveButton() {
        if (saveButton != null) saveButton.active = numberBoxes.stream().allMatch(NumberBox::valid);
    }

    private void toast(Component title, Component message) {
        SystemToast.add(ClientCompat.toasts(minecraft), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, message);
    }

    /** A whole-number box that turns red while its text is outside {@code min..max}. */
    private record NumberBox(EditBox box, int min, int max) {
        boolean valid() {
            String text = box.getValue();
            if (text.isEmpty()) return false;
            try {
                long value = Long.parseLong(text);
                return value >= min && value <= max;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        void showValidity() {
            box.setTextColor(valid() ? VALID : ERROR);
        }
    }
}
