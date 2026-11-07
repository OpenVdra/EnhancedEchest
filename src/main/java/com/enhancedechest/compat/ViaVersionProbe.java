package com.enhancedechest.compat;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

import java.util.UUID;

/**
 * The only class that touches the ViaVersion API. Kept separate from {@link ClientVersions} so the JVM
 * resolves the {@code com.viaversion} types only when ViaVersion is actually installed — never construct
 * it without that check.
 */
final class ViaVersionProbe {

    ViaVersionProbe() {
        // Fail fast at construction (inside ClientVersions' try) if the API is missing or incompatible,
        // rather than on the first player lookup.
        Via.getAPI();
    }

    /**
     * The client's protocol number, or -1 when ViaVersion has no record of the player. Snapshot
     * protocols carry a high bit and compare as huge numbers, i.e. as "newer", which is what we want.
     */
    int protocolOf(UUID player) {
        ProtocolVersion version = Via.getAPI().getPlayerProtocolVersion(player);
        return version == null || !version.isKnown() ? -1 : version.getVersion();
    }
}
