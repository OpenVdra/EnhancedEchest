package com.enhancedechest.log;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One immutable unit of work handed from a Bukkit-owned thread to the log writer thread: a fully
 * captured event ready to insert. Everything here is already primitive/immutable — the snapshot is
 * encoded bytes and the diff is text — so it crosses the thread boundary with no Bukkit object and no
 * further main-thread work.
 *
 * @param owner     the chest owner
 * @param index     1-based chest index
 * @param actor     who performed the action (owner, or an admin viewing)
 * @param actorName the actor's name at the time, or {@code null}
 * @param size      slot count of the chest
 * @param action    OPEN or CLOSE
 * @param ts        epoch millis
 * @param diff      the CLOSE change summary text, or {@code null} for OPEN / no change
 * @param snapshot  the {@code ContainerCodec}-encoded contents at this moment
 */
public record LogWrite(UUID owner, int index, UUID actor, @Nullable String actorName, int size,
                       LogAction action, long ts, @Nullable String diff, byte[] snapshot) {}
