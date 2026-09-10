package com.enhancedechest.log;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One immutable unit of work handed from a Bukkit-owned thread to the log writer thread: a completed
 * visit ready to insert. One row is written per visit (at close), never per open — the snapshot is the
 * chest as it was left, and the diff is what changed while it was open. Everything here is already
 * primitive/immutable, so it crosses the thread boundary with no Bukkit object and no further
 * main-thread work.
 *
 * @param owner     the chest owner
 * @param index     1-based chest index
 * @param actor     who opened it (owner, or an admin viewing)
 * @param actorName the actor's name at the time, or {@code null}
 * @param size      slot count of the chest
 * @param openedAt  epoch millis the chest was opened
 * @param closedAt  epoch millis it was closed
 * @param diff      the change summary text (never empty — a visit that changed nothing is not written)
 * @param snapshot  the {@code ContainerCodec}-encoded contents as the chest was left
 */
public record LogWrite(UUID owner, int index, UUID actor, @Nullable String actorName, int size,
                       long openedAt, long closedAt, String diff, byte[] snapshot) {}
