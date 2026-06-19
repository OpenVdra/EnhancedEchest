package com.enhancedechest.serialization;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Encodes and decodes a 54-slot inventory using Paper's Data Component API.
 *
 * Storage format: [1-byte version tag] + [ItemStack#serializeAsBytes of a SHULKER_BOX vehicle
 * carrying a CONTAINER data component].
 *
 * WARNING: DataComponentTypes.CONTAINER and ItemContainerContents are @Experimental.
 * Isolating all component usage here means a Paper breaking change requires edits only
 * in this class. The version tag in the stored bytes allows future migration paths.
 *
 * Slot semantics: ItemContainerContents.containerContents(List) is positional —
 * interior empty slots are preserved; only trailing empties are trimmed on encode.
 * Decode pads the tail back to CHEST_SIZE with empty stacks.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ContainerCodec {

    private static final byte FORMAT_VERSION = 0x01;
    public static final int CHEST_SIZE = 54;

    // Arbitrary carrier — never shown to players, just a vehicle for the CONTAINER component.
    // SHULKER_BOX is the same vehicle Mojang uses for shulker box item contents.
    private static final Material VEHICLE_MATERIAL = Material.SHULKER_BOX;

    /**
     * Encodes the 54-slot inventory contents to bytes for DB storage.
     * Null entries in the array are treated as empty (AIR) slots.
     */
    public byte[] encode(ItemStack[] contents) {
        List<ItemStack> slots = new ArrayList<>(CHEST_SIZE);
        for (int i = 0; i < CHEST_SIZE; i++) {
            ItemStack item = (contents != null && i < contents.length) ? contents[i] : null;
            slots.add(isEmpty(item) ? ItemStack.empty() : item);
        }

        ItemContainerContents containerContents = ItemContainerContents.containerContents(slots);
        ItemStack vehicle = ItemStack.of(VEHICLE_MATERIAL);
        vehicle.setData(DataComponentTypes.CONTAINER, containerContents);

        byte[] vehicleBytes = vehicle.serializeAsBytes();
        byte[] result = new byte[1 + vehicleBytes.length];
        result[0] = FORMAT_VERSION;
        System.arraycopy(vehicleBytes, 0, result, 1, vehicleBytes.length);
        return result;
    }

    /**
     * Decodes stored bytes back to a 54-slot array. Always returns exactly CHEST_SIZE entries;
     * empty slots are represented as ItemStack.empty() (never null).
     *
     * @throws CodecException if the data is malformed or uses an unknown format version.
     *                        Callers must NOT open an empty chest on failure — abort and preserve the DB row.
     */
    public ItemStack[] decode(byte[] data) throws CodecException {
        if (data == null || data.length < 2) {
            throw new CodecException("Stored data is too short (length=" + (data == null ? 0 : data.length) + ")");
        }

        byte version = data[0];
        if (version != FORMAT_VERSION) {
            throw new CodecException("Unknown format version 0x" + String.format("%02X", version)
                    + " — plugin may need updating before this data can be read");
        }

        byte[] vehicleBytes = Arrays.copyOfRange(data, 1, data.length);

        try {
            ItemStack vehicle = ItemStack.deserializeBytes(vehicleBytes);
            ItemContainerContents containerContents = vehicle.getData(DataComponentTypes.CONTAINER);

            ItemStack[] result = new ItemStack[CHEST_SIZE];
            Arrays.fill(result, ItemStack.empty());

            if (containerContents != null) {
                List<ItemStack> decoded = containerContents.contents();
                int limit = Math.min(decoded.size(), CHEST_SIZE);
                for (int i = 0; i < limit; i++) {
                    ItemStack item = decoded.get(i);
                    result[i] = isEmpty(item) ? ItemStack.empty() : item;
                }
            }

            return result;
        } catch (Exception e) {
            throw new CodecException("Failed to deserialize container vehicle", e);
        }
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getType() == Material.AIR;
    }
}
