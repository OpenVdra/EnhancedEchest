# Permissions

All nodes default to `op`. Grant them through your permission plugin (LuckPerms, etc.) to open access to other ranks.

## Player

**`enhancedechest.command.open`**
Allows using `/ec` and `/eclist` by command, and setting a main chest. Right-clicking an ender chest block never requires this.

**`enhancedechest.additional_amount.<count>.slot.<size>`**
Grants extra chests by rank. For example, `enhancedechest.additional_amount.2.slot.54` gives the player two 54-slot chests. Multiple nodes stack. Removing a node removes those chests; any items spill to a temporary chest recoverable from `/eclist`.

See [Permission-Granted Chests](/docs/permission-chests#permission-granted-chests) for full details.

**`enhancedechest.default_size.<size>`**
Overrides the size of the player's **base** (first) ender chest by rank, independent of the global `enderchest.default-size`. For example, `enhancedechest.default_size.54` makes that player's base chest 54 slots. If a player holds several, the **largest** wins.

See [Base Chest Size by Permission](/docs/permission-chests#default-size-permission) for full details.

## Admin

Each `/ee` command requires only its own node. There is no separate base permission:

**`enhancedechest.admin.add`** - `/ee add`: give a player a new chest.

**`enhancedechest.admin.resize`** - `/ee resize`: change a chest's slot count. Refuses on a permission-granted chest, and on a base chest whose size is set by a [`default_size`](/docs/permission-chests#default-size-permission) permission.

**`enhancedechest.admin.delete`** - `/ee delete`: delete a player's newest chests.

**`enhancedechest.admin.view`** - `/ee view`: open another player's chest (read-only).

**`enhancedechest.admin.edit`** - combined with `admin.view`, allows moving items.

**`enhancedechest.admin.clear`** - shows the red **(Admin) Clear chest** button in the `/ee view` menu and allows emptying a chest with it.

**`enhancedechest.admin.transfer`** - `/ee transfer`: move a player's chests onto another account.

**`enhancedechest.admin.reload`** - `/ee reload`: reload config and language files.

**`enhancedechest.admin.migrate`** - `/ee migrate vanilla`, `/ee migrate axvaults`, and `/ee migrate playervaultsx`: import data from vanilla ender chests, the AxVaults plugin, or the PlayerVaultsX plugin.

**`enhancedechest.admin.import`** - `/ee import`: copy all data from an old database backend into the active one.

::: tip
To grant full admin access in one go, give `enhancedechest.admin.*` (if your permission plugin supports wildcards).
:::

The two chest-by-rank permissions (`default_size` and `additional_amount`) have their own page: see [Permission Chests](/docs/permission-chests).
