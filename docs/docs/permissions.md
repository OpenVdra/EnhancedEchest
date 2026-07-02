# Permissions

All nodes default to `op`. Grant them through your permission plugin (LuckPerms, etc.) to open access to other ranks.

## Player

**`enhancedechest.command.open`**
Allows using `/ec` and `/eclist` by command, and setting a main chest. Right-clicking an ender chest block never requires this.

**`enhancedechest.additional_amount.<count>.slot.<size>`**
Grants extra chests by rank. For example, `enhancedechest.additional_amount.2.slot.54` gives the player two 54-slot chests. Multiple nodes stack. Removing a node removes those chests; any items spill to a temporary chest recoverable from `/eclist`.

See [Permission-Granted Chests](#permission-granted-chests) below for full details.

**`enhancedechest.default_size.<size>`**
Overrides the size of the player's **base** (first) ender chest by rank, independent of the global `enderchest.default-size`. For example, `enhancedechest.default_size.54` makes that player's base chest 54 slots. If a player holds several, the **largest** wins.

See [Base Chest Size by Permission](#default-size-permission) below for full details.

## Admin

Each `/ee` command requires only its own node. There is no separate base permission:

**`enhancedechest.admin.add`** - `/ee add`: give a player a new chest.

**`enhancedechest.admin.resize`** - `/ee resize`: change a chest's slot count. Refuses on a permission-granted chest, and on a base chest whose size is set by a [`default_size`](#default-size-permission) permission.

**`enhancedechest.admin.delete`** - `/ee delete`: delete a player's newest chests.

**`enhancedechest.admin.view`** - `/ee view`: open another player's chest (read-only).

**`enhancedechest.admin.edit`** - combined with `admin.view`, allows moving items.

**`enhancedechest.admin.clear`** - shows the red **(Admin) Clear chest** button in the `/ee view` menu and allows emptying a chest with it.

**`enhancedechest.admin.transfer`** - `/ee transfer`: move a player's chests onto another account.

**`enhancedechest.admin.reload`** - `/ee reload`: reload config and language files.

**`enhancedechest.admin.migrate`** - `/ee migrate vanilla`, `/ee migrate axvaults`, and `/ee migrate playervaultsx`: import data from vanilla ender chests, the AxVaults plugin, or the PlayerVaultsX plugin.

::: tip
To grant full admin access in one go, give `enhancedechest.admin.*` (if your permission plugin supports wildcards).
:::

## Permission-Granted Chests {#permission-granted-chests}

Use `enhancedechest.additional_amount.<count>.slot.<size>` to tie chest perks to ranks without using commands.

- **`<count>`**: number of chests to grant.
- **`<size>`**: slot count, a multiple of 9 from 9 to 54.
- **Nodes stack**: granting `...1.slot.9` and `...2.slot.9` gives the player three 9-slot chests total.
- **Revocation is clean**: losing a node removes those chests; items spill to a recoverable temporary chest.
- **The base chest is always kept**: grants never delete or override the player's first chest.
- Grants sync on the player's next chest open - no relog needed.

::: warning
Permission grants only apply while `permission-chests.enabled: true` in `config.yml`. Disabling it stops syncing but leaves already-granted chests in place.
:::

## Base Chest Size by Permission {#default-size-permission}

Every player has one **base** ender chest (their first chest, number `#1`). Its size is normally the global `enderchest.default-size`. The `enhancedechest.default_size.<size>` permission overrides that size **per player**, so a rank can get a bigger (or smaller) starting chest without any command.

- **`<size>`**: slot count, a multiple of 9 from 9 to 54.
- **Largest wins**: if a player somehow holds `...27` and `...54`, they get 54. (This differs from the *additional* chests, which stack, since a player only has one base chest, so there is one size to pick.)
- **Grant grows, revoke shrinks**: gaining the permission resizes the base chest to it. Growing keeps every item. Shrinking (a smaller permission, or losing it entirely) moves the overflow into a recoverable temporary chest, exactly like the additional-chest permission. Losing the permission shrinks the base chest back to `enderchest.default-size`.
- **Permission-managed while set**: while a `default_size` permission applies to a player, their base chest behaves like a permission-granted chest for admins. `/ee resize` refuses to change it, since its size is owned by the permission. This holds even for offline players.
- **Always available**: this is a pure permission feature with no config switch, simply grant (or don't grant) the node.
- Syncs on the player's next chest open, no relog needed.

::: tip Interaction with additional chests
This node sizes the **base** chest; `additional_amount` grants **extra** chests. They are independent: a player can have a 54-slot base chest from `default_size.54` *and* two more from `additional_amount.2.slot.54`.
:::
