package com.enhancedechest.log;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One row of the chest log as shown in the {@code /ee log} GUI — everything a pane needs <b>except</b>
 * the snapshot blob, which is left in the database and fetched only when the pane is actually clicked.
 * Keeping the (potentially multi-KB) blob out of the list query is what lets a page of 45 entries load
 * cheaply.
 *
 * @param id      row id, used to fetch the snapshot on click
 * @param action  OPEN or CLOSE (drives the pane colour)
 * @param ts      epoch millis of the event
 * @param actorName last-known name of who performed it, or {@code null}
 * @param index   1-based chest index
 * @param size    slot count of the chest at that moment
 * @param diff    the CLOSE change summary (empty for OPEN, or a CLOSE that changed nothing)
 */
public record LogEntry(long id, LogAction action, long ts, @Nullable String actorName,
                       int index, int size, List<DiffLine> diff) {}
