package com.enhancedechest.log;

/**
 * One line of a CLOSE event's change summary: how the amount of a single item kind changed over the
 * visit. {@code delta > 0} means that many were put into the chest, {@code delta < 0} that many taken
 * out. {@code itemKey} is a namespaced material key (e.g. {@code minecraft:diamond}).
 *
 * <p><b>Material-level on purpose.</b> The summary totals by material, not by full item identity, so a
 * swap of two differently-enchanted swords of the same material nets to zero here. That is a readable
 * headline, not the source of truth: the exact before/after contents are the OPEN and CLOSE snapshots,
 * viewable by clicking the entry. Keeping the summary at material level is what lets it render as a
 * localized item name with no stored text and no per-viewer work at write time.
 */
public record DiffLine(String itemKey, int delta) {

    /** Serializes to one text line, {@code "<delta> <key>"}; keys never contain a space so it round-trips. */
    String encode() {
        return delta + " " + itemKey;
    }

    /** Parses a line produced by {@link #encode()}, or {@code null} if it is malformed. */
    static DiffLine decode(String line) {
        int space = line.indexOf(' ');
        if (space <= 0 || space == line.length() - 1) return null;
        try {
            return new DiffLine(line.substring(space + 1), Integer.parseInt(line.substring(0, space)));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
