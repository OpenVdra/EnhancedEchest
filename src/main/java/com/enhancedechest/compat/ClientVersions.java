package com.enhancedechest.compat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

/**
 * Answers "can this player's client show a Dialog?". The Dialog API arrived in Minecraft 1.21.6; a
 * server on 1.21.6+ can still be joined by older clients through ViaBackwards, and those clients either
 * get ViaBackwards' chest emulation of the dialog or — with {@code dialogs-via-chests: false} — nothing
 * at all. Knowing the real client version lets the chest list route them to the inventory chooser
 * instead.
 *
 * <p>Only ViaVersion knows the client's version (Paper sees the server's own protocol on every
 * connection). It is an <b>optional</b> dependency, so every ViaVersion type lives in
 * {@link ViaVersionProbe}, which this class loads only after checking the plugin is enabled — referencing
 * it unconditionally would throw {@code NoClassDefFoundError} on servers without ViaVersion.
 *
 * <p>The fallback is always on (not a config key): a pre-1.21.6 player shown a dialog either gets a
 * clumsy emulation or nothing at all, and neither is a state worth letting an admin choose.
 *
 * <p>When ViaVersion is absent, or runs only on the proxy (so this server never sees an old protocol),
 * every player counts as dialog-capable, which is exactly the behaviour before this class existed.
 */
public final class ClientVersions {

    /** Protocol number of Minecraft 1.21.6, the first release with the Dialog API. */
    static final int DIALOG_PROTOCOL = 771;

    /** Null when ViaVersion is not installed or its API failed to link. */
    private final ViaVersionProbe probe;

    public ClientVersions(Logger logger) {
        ViaVersionProbe found = null;
        if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
            try {
                found = new ViaVersionProbe();
                logger.info("ViaVersion found — players on clients older than 1.21.6 get the inventory chest list");
            } catch (LinkageError | RuntimeException e) {
                // An incompatible ViaVersion API must not take the plugin down: degrade to "everyone
                // supports dialogs", which is the pre-detection behaviour.
                logger.warn("ViaVersion is installed but its API could not be used ({}); "
                        + "old-client detection is off", e.toString());
            }
        }
        this.probe = found;
    }

    /**
     * Whether {@code player}'s client understands the Dialog API (1.21.6+). Always true without
     * ViaVersion, and on any lookup failure — a wrong "true" only shows the dialog, which ViaBackwards
     * usually emulates, while a wrong "false" would needlessly take features away.
     */
    public boolean supportsDialogs(Player player) {
        if (probe == null) return true;
        try {
            int protocol = probe.protocolOf(player.getUniqueId());
            return protocol < 0 || protocol >= DIALOG_PROTOCOL;
        } catch (LinkageError | RuntimeException e) {
            return true;
        }
    }
}
