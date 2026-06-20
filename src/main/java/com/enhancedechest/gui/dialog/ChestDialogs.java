package com.enhancedechest.gui.dialog;

import com.enhancedechest.gui.EnderChestService;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.model.ChestSummary;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Builds the /ec list management dialogs using Paper's (experimental) Dialog API.
 *
 * <p>All Dialog API usage is isolated here so a Paper breaking change requires edits in one place
 * only (mirrors how {@code ContainerCodec} isolates the Data Component API).
 *
 * <p>Three levels: list (one button per chest), per-chest detail (Open / Rename / Set-as-main),
 * and a dedicated rename dialog (text input + Save / Cancel).
 *
 * <p>Navigation strategy — to avoid the cursor recentre that a server-pushed {@code showDialog}
 * causes, <i>forward</i> navigation (list→detail, detail→rename) uses a client-side
 * {@code show_dialog} action ({@link ClickEvent#showDialog}); the client swaps the dialog in place
 * without reopening the screen. Back/Cancel and post-mutation refreshes re-query the DB and are
 * pushed from the server (these legitimately recentre, since the underlying data may have changed).
 */
@SuppressWarnings("UnstableApiUsage")
public final class ChestDialogs {

    private static final int MAX_NAME_LENGTH = 32;
    private static final int BUTTON_WIDTH = 180;

    private final EnderChestService service;
    private final LanguageManager lang;

    public ChestDialogs(EnderChestService service, LanguageManager lang) {
        this.service = service;
        this.lang = lang;
    }

    /** Top-level list: one button per chest, clicking opens that chest's detail dialog in place. */
    public Dialog listDialog(List<ChestSummary> chests) {
        List<ActionButton> buttons = new ArrayList<>(chests.size());
        for (ChestSummary chest : chests) {
            Component label = lang.getChestTitle(chest.index(), chest.customName());
            if (chest.primary()) {
                label = label.append(Component.text(" ")).append(lang.getGui("dialog.main-tag"));
            }
            Component tooltip = lang.getGui("dialog.slots", "size", Integer.toString(chest.size()));
            // Forward, in-place: open this chest's detail dialog client-side (no cursor reset).
            buttons.add(ActionButton.create(label, tooltip, BUTTON_WIDTH,
                    DialogAction.staticAction(ClickEvent.showDialog(detailDialog(chest)))));
        }

        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(lang.getGui("dialog.list-title")).build())
                .type(DialogType.multiAction(buttons, null, 1)));
    }

    /** Per-chest detail: Open / Rename / Set-as-main / Back. */
    public Dialog detailDialog(ChestSummary chest) {
        int index = chest.index();
        List<ActionButton> buttons = new ArrayList<>(4);

        // Open the actual inventory (closes the dialog; cursor position is moot once an inventory opens).
        buttons.add(ActionButton.create(lang.getGui("dialog.open"), null, BUTTON_WIDTH,
                click((view, audience) -> {
                    if (audience instanceof Player p) service.openChest(p, index, null);
                })));

        // Forward, in-place: go to the dedicated rename dialog client-side (no cursor reset).
        buttons.add(ActionButton.create(lang.getGui("dialog.rename"), null, BUTTON_WIDTH,
                DialogAction.staticAction(ClickEvent.showDialog(renameDialog(chest)))));

        // Set as main — mutates data, so it re-queries and is re-pushed from the server.
        if (!chest.primary()) {
            buttons.add(ActionButton.create(lang.getGui("dialog.set-main"), lang.getGui("dialog.main-desc"),
                    BUTTON_WIDTH, click((view, audience) -> {
                        if (!(audience instanceof Player p)) return;
                        service.setPrimaryAsync(p.getUniqueId(), index)
                                .thenRun(() -> service.openDetailDialog(p, index));
                    })));
        }

        buttons.add(ActionButton.create(lang.getGui("dialog.back"), null, BUTTON_WIDTH,
                click((view, audience) -> {
                    if (audience instanceof Player p) service.openListDialog(p);
                })));

        Component title = lang.getChestTitle(index, chest.customName());
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title).build())
                .type(DialogType.multiAction(buttons, null, 1)));
    }

    /** Dedicated rename dialog: a single text input plus Save / Cancel. */
    public Dialog renameDialog(ChestSummary chest) {
        int index = chest.index();
        String current = chest.customName() != null ? chest.customName() : "";

        DialogInput nameInput = DialogInput.text("name", lang.getGui("dialog.name-label"))
                .initial(current)
                .maxLength(MAX_NAME_LENGTH)
                .build();

        ActionButton save = ActionButton.create(lang.getGui("dialog.save-name"), null, BUTTON_WIDTH,
                click((view, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    String typed = view.getText("name");
                    String name = (typed == null || typed.isBlank()) ? null : typed.trim();
                    service.renameAsync(p.getUniqueId(), index, name)
                            .thenRun(() -> service.openDetailDialog(p, index));
                }));

        ActionButton cancel = ActionButton.create(lang.getGui("dialog.cancel"), null, BUTTON_WIDTH,
                click((view, audience) -> {
                    if (audience instanceof Player p) service.openDetailDialog(p, index);
                }));

        Component title = lang.getChestTitle(index, chest.customName());
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .inputs(List.of(nameInput))
                        .build())
                .type(DialogType.multiAction(List.of(save, cancel), null, 1)));
    }

    private static DialogAction click(BiConsumer<io.papermc.paper.dialog.DialogResponseView,
            net.kyori.adventure.audience.Audience> body) {
        return DialogAction.customClick(
                body::accept,
                ClickCallback.Options.builder().build());
    }
}
