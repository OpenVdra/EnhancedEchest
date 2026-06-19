package com.enhancedechest.model;

import java.util.UUID;

public record EnderChestData(
        UUID owner,
        byte[] containerData,
        boolean migrated,
        long lastUpdated
) {}
