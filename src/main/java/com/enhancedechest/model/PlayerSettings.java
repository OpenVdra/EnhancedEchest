package com.enhancedechest.model;

import org.jetbrains.annotations.Nullable;

/**
 * Per-player state, persisted one row per player in {@code players}.
 *
 * <p>This is the general settings container: it is loaded whole and saved whole, so callers never
 * deal with individual columns. <b>To add a new setting:</b>
 * <ol>
 *   <li>add a component to this record (and to {@link #defaults()} / a {@code withX} helper);</li>
 *   <li>add the matching column to the {@code players} DDL in all three storage dialects
 *       (SqliteStorage / MysqlStorage / PostgresStorage) — for an existing install, ship a guarded
 *       {@code ALTER TABLE ... ADD COLUMN ... DEFAULT ...} step in {@code SchemaMigrator} (portable
 *       across all four engines);</li>
 *   <li>map the column in {@code AbstractSqlStorage.loadSettings}/{@code saveSettings}.</li>
 * </ol>
 * Defaults live in {@link #defaults()} so a player with no row behaves identically to a fresh one.
 *
 * @param editMode          whether the management list opens in edit mode (clicking a chest opens its
 *                          detail dialog rather than the chest itself); remembered across sessions
 * @param appliedDefaultSize the base-chest size currently dictated by the player's
 *                          {@code enhancedechest.default_size.<size>} permission, or {@code 0} when the
 *                          base chest is <b>not</b> permission-managed (its size is the config default or
 *                          an admin resize). This is the persisted baseline the reconcile compares against
 *                          to detect a permission being granted, changed or revoked, and that
 *                          {@code /ee resize} reads to know the base chest is permission-managed (so it is
 *                          left alone even for an offline owner). See
 *                          {@code com.enhancedechest.service.PermissionChestService}.
 * @param username          the player's in-game name as last recorded on join, or {@code null} if never
 *                          recorded. Backs offline name→UUID resolution ({@code /ee view} and friends);
 *                          written lazily — only when it differs from the stored value — by
 *                          {@code PlayerSettingsCache.preloadSettings}, not on every join.
 */
public record PlayerSettings(boolean editMode, int appliedDefaultSize, @Nullable String username) {

    /** Settings for a player who has never saved any — every field at its default. */
    public static PlayerSettings defaults() {
        return new PlayerSettings(false, 0, null);
    }

    /** Returns a copy with {@code editMode} changed, leaving every other setting untouched. */
    public PlayerSettings withEditMode(boolean editMode) {
        return new PlayerSettings(editMode, appliedDefaultSize, username);
    }

    /** Returns a copy with {@code appliedDefaultSize} changed, leaving every other setting untouched. */
    public PlayerSettings withAppliedDefaultSize(int appliedDefaultSize) {
        return new PlayerSettings(editMode, appliedDefaultSize, username);
    }

    /** Returns a copy with {@code username} changed, leaving every other setting untouched. */
    public PlayerSettings withUsername(String username) {
        return new PlayerSettings(editMode, appliedDefaultSize, username);
    }
}
