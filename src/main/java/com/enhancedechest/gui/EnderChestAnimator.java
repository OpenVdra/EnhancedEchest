package com.enhancedechest.gui;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Triggers the vanilla ender chest open/close block animation and sound via NMS reflection.
 *
 * Delegates to EnderChestBlockEntity.startOpen/stopOpen, which:
 *   - Increments/decrements the internal viewer counter
 *   - Sends a BlockAction packet to nearby clients (animates the chest lid)
 *   - Plays the open/close sound at the block location
 *
 * Silently degrades (no-op) on NMS API changes — animation just won't play.
 *
 * Thread requirement: must be called on the region thread that owns the block's chunk
 * (Folia) or the main thread (Spigot/Paper).
 */
public final class EnderChestAnimator {

    private static final Method  GET_WORLD_HANDLE;
    private static final Method  GET_PLAYER_HANDLE;
    private static final Constructor<?> BLOCK_POS_CTOR;
    private static final Method  GET_BLOCK_ENTITY;
    private static final Class<?> ENDER_CHEST_BE_CLASS;
    private static final Method  START_OPEN;
    private static final Method  STOP_OPEN;
    private static final boolean AVAILABLE;

    static {
        boolean ok = false;
        Method wHandle = null, pHandle = null, gbe = null, start = null, stop = null;
        Constructor<?> posCtor = null;
        Class<?> beCls = null;

        try {
            // Paper 1.20.5+ uses unversioned CraftBukkit package
            Class<?> craftWorld  = Class.forName("org.bukkit.craftbukkit.CraftWorld");
            Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            Class<?> blockPos    = Class.forName("net.minecraft.core.BlockPos");
            Class<?> serverLevel = Class.forName("net.minecraft.server.level.ServerLevel");
            Class<?> nmsPlayer   = Class.forName("net.minecraft.world.entity.player.Player");
            beCls                = Class.forName("net.minecraft.world.level.block.entity.EnderChestBlockEntity");

            wHandle  = craftWorld.getMethod("getHandle");
            pHandle  = craftPlayer.getMethod("getHandle");
            posCtor  = blockPos.getConstructor(int.class, int.class, int.class);
            gbe      = serverLevel.getMethod("getBlockEntity", blockPos);
            start    = beCls.getMethod("startOpen", nmsPlayer);
            stop     = beCls.getMethod("stopOpen", nmsPlayer);

            ok = true;
        } catch (Exception ignored) {}

        AVAILABLE            = ok;
        GET_WORLD_HANDLE     = wHandle;
        GET_PLAYER_HANDLE    = pHandle;
        BLOCK_POS_CTOR       = posCtor;
        GET_BLOCK_ENTITY     = gbe;
        ENDER_CHEST_BE_CLASS = beCls;
        START_OPEN           = start;
        STOP_OPEN            = stop;
    }

    private EnderChestAnimator() {}

    /**
     * Plays the open animation and sound on the ender chest at {@code blockLoc}.
     * Call this when the player opens the custom GUI via block right-click.
     */
    public static void open(Player player, Location blockLoc) {
        animate(player, blockLoc, true);
    }

    /**
     * Plays the close animation and sound on the ender chest at {@code blockLoc}.
     * Call this when the player closes the custom GUI.
     */
    public static void close(Player player, Location blockLoc) {
        animate(player, blockLoc, false);
    }

    private static void animate(Player player, Location blockLoc, boolean open) {
        if (!AVAILABLE || blockLoc.getWorld() == null) return;
        try {
            Object serverLevel = GET_WORLD_HANDLE.invoke(blockLoc.getWorld());
            Object pos         = BLOCK_POS_CTOR.newInstance(
                    blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
            Object be          = GET_BLOCK_ENTITY.invoke(serverLevel, pos);
            if (!ENDER_CHEST_BE_CLASS.isInstance(be)) return;
            Object nmsPlayer   = GET_PLAYER_HANDLE.invoke(player);
            (open ? START_OPEN : STOP_OPEN).invoke(be, nmsPlayer);
        } catch (Throwable ignored) {}
    }
}
