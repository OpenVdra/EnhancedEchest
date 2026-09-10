package com.enhancedechest.log;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One row of the chest log as shown in the {@code /ee log} GUI — everything a pane needs <b>except</b>
 * the snapshot blob, which is left in the database and fetched only when the pane is actually clicked.
 * Keeping the (potentially multi-KB) blob out of the list query is what lets a page of 45 entries load
 * cheaply.
 *
 * <p>One entry is one visit: opened, changed, closed. There is no separate open/close event any more.
 *
 * @param id        row id, used to fetch the snapshot on click
 * @param openedAt  epoch millis the chest was opened
 * @param closedAt  epoch millis it was closed
 * @param actorName last-known name of who did it, or {@code null}
 * @param index     1-based chest index
 * @param size      slot count of the chest
 * @param diff      the change summary (always non-empty for a stored visit)
 */
public record LogEntry(long id, long openedAt, long closedAt, @Nullable String actorName,
                       int index, int size, List<DiffLine> diff) {}
