package com.enhancedechest.log;

/**
 * The two kinds of chest-log event. Stored as the ordinal so the column stays a cheap integer; the
 * order is load-bearing for that reason — append new actions, never reorder.
 */
public enum LogAction {
    /** A viewer opened the chest; the snapshot is what it held at that moment. */
    OPEN,
    /** A viewer closed the chest; the snapshot is the final state and the diff is what changed. */
    CLOSE;

    private static final LogAction[] VALUES = values();

    /** Maps a stored ordinal back to an action, defaulting to {@link #OPEN} for an unknown value. */
    public static LogAction fromCode(int code) {
        return code >= 0 && code < VALUES.length ? VALUES[code] : OPEN;
    }
}
