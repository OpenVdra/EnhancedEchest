# Permission Chests

Two permission families hand out ender chest perks by rank, with no commands and no config beyond a single toggle. Both sync the next time the player opens their ender chest, so no relog is needed.

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

## Permission-Granted Chests {#permission-granted-chests}

Use `enhancedechest.additional_amount.<count>.slot.<size>` to tie chest perks to ranks without using commands.

- **`<count>`**: number of chests to grant.
- **`<size>`**: slot count, a multiple of 9 from 9 to 54.
- **Nodes stack**: granting `...1.slot.9` and `...2.slot.9` gives the player three 9-slot chests total.
- **Revocation is clean**: losing a node removes those chests; items spill to a recoverable temporary chest.
- **The base chest is always kept**: grants never delete or override the player's first chest.
- Grants sync on the player's next chest open, no relog needed.

::: warning
Permission grants only apply while `permission-chests.enabled: true` in `config.yml`. Disabling it stops syncing but leaves already-granted chests in place.
:::

## Permission chests vs. `/ee add` chests

A player can gain extra chests two ways, and the two behave differently:

- **Permission chests** (`additional_amount`) come and go with the player's rank. They appear as soon as the permission applies and are removed as soon as it is lost (a rank change, or an edit to the node); a removed one spills its items into a recoverable temporary chest. Admins cannot resize or delete them with `/ee resize` or `/ee delete`, because the permission owns them.
- **Command chests** (`/ee add`) are permanent. An admin hands them out and they stay until an admin resizes them (`/ee resize`) or deletes them (`/ee delete`). A rank change never touches them.

For the player, both look and work exactly the same in the `/eclist` menu: open, rename, choose an icon, sort, and set as main. The only real difference is who controls them, so a permission chest can vanish when a rank changes while a command chest cannot. Because a removed permission chest is deleted, its custom name and icon go with it; a command chest keeps them until an admin removes it.
