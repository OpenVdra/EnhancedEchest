package com.enhancedechest.command.admin;

import com.enhancedechest.service.DbExecutor;
import com.enhancedechest.service.StorageGateway;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves a player name to a UUID for admin commands, working for offline players.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>an <b>online</b> player with the exact name (cheap, main-thread safe);</li>
 *   <li>the plugin's own {@code players} table (its name index is kept up to date the first time a player
 *       opens an ender chest, so it covers anyone who has used the plugin — independent of the server
 *       usercache or network availability);</li>
 *   <li>a Bukkit <b>offline</b> lookup as a last resort (covers players who have never opened an ender
 *       chest, or last did so before this version started indexing names).</li>
 * </ol>
 * The table and offline lookups run on the async storage executor so the calling command thread never
 * blocks — {@code getOfflinePlayer(String)} can hit the network in online mode. Completes with {@code null}
 * when no player matches.
 */
public final class PlayerResolver {

    private PlayerResolver() {}

    public static CompletableFuture<UUID> resolveAsync(StorageGateway gateway, DbExecutor db, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return CompletableFuture.completedFuture(online.getUniqueId());
        }
        return gateway.findUuidByNameAsync(name).thenCompose(uuid -> {
            if (uuid != null) {
                return CompletableFuture.completedFuture(uuid);
            }
            // Run the (potentially blocking) Bukkit lookup on the DB executor, never on the command thread.
            return db.supply(() -> offlineFallback(name));
        });
    }

    @SuppressWarnings("deprecation")
    private static @Nullable UUID offlineFallback(String name) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline.getUniqueId() : null;
    }
}
