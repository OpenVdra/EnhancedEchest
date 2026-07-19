# Permission Chests

A player's chests come in two kinds: their one **base** chest (`#1`, created automatically) and any **additional** chests beyond it. Two independent permission families size and grant these by rank, with no commands and no config beyond a single toggle. Both sync the next time the player opens their ender chest, no relog needed.

## Base Chest Size by Permission {#default-size-permission}

Every player's base chest is normally sized by the `default-size` setting in `config.yml`. The `enhancedechest.default_size.<size>` permission overrides that size **per player**, so a rank can get a bigger (or smaller) starting chest without any command.

- **`<size>`**: slot count, a multiple of 9 from 9 to 54.
- **Largest applies**: a player holding both `...27` and `...54` gets 54, since there is only one base chest and one size to pick.
- **Grant grows, revoke shrinks**: gaining the permission resizes the base chest to it, keeping every item. Shrinking (a smaller permission, or losing it entirely) moves the overflow into a temporary chest the player can take back. Losing the permission returns the base chest to the `default-size` in `config.yml`.
- **Permission-managed while set**: `/ee resize` refuses to change a base chest while a `default_size` permission owns its size, even for offline players.

::: tip Interaction with additional chests
This node sizes the **base** chest; `additional_amount` grants **extra** chests. They are independent: a player can have a 54-slot base chest from `default_size.54` *and* two more from `additional_amount.2.slot.54`.
:::

## Permission-Granted Chests {#permission-granted-chests}

Use `enhancedechest.additional_amount.<count>.slot.<size>` to grant **extra** chests by rank, on top of the base chest.

- **`<count>`**: number of chests to grant. **`<size>`**: slot count, a multiple of 9 from 9 to 54.
- **Nodes stack**: granting `...1.slot.9` and `...2.slot.9` gives the player three 9-slot chests total.
- **Revocation is clean**: losing a node removes those chests, spilling items to a recoverable temporary chest. The base chest is never touched.

::: warning
Permission grants only apply while `permission-chests.enabled: true` in `config.yml`. Disabling it stops syncing but leaves already-granted chests in place.
:::

## Permission Chests vs. `/ee add` Chests

A player can gain extra chests two ways:

- **Permission chests** (`additional_amount`) come and go with the player's rank: they appear as soon as the permission applies and are removed as soon as it is lost, spilling items to a recoverable temporary chest. Admins cannot resize or delete them, since the permission owns them.
- **Command chests** (`/ee add`) are permanent. They stay until an admin resizes (`/ee resize`) or deletes (`/ee delete`) them; a rank change never touches them.

To the player both work identically in `/eclist`: open, rename, choose an icon, sort, set as main. A removed permission chest loses its custom name and icon with it; a command chest keeps them until an admin removes it.
