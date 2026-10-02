# Multi-Chest System

Players are no longer limited to one ender chest. Each player can own several, managed through an in-game menu.

<figure class="feature-figure">
  <img alt="The chest list menu showing several owned ender chests" src="/screenshots/eclist.webp" />
  <figcaption>With two or more chests, opening your ender chest brings up this menu of every chest you own, each with its slot count.</figcaption>
</figure>

## Chest List Menu

Run `/eclist`, or hold shift while right-clicking an ender chest, to open a menu listing every chest the player owns, each showing its slot count. An **Edit mode** checkbox switches what clicking a chest does: off by default, a chest opens straight away; tick it and clicking a chest opens its management screen where players can rename it, give it a custom icon, or set it as their main. The checkbox toggles in place without reopening the menu.

## Main Chest

With several chests, a player can pick one as their **main**, the one opened directly by `/ec` and by right-clicking an ender chest. Until a main is chosen, those open the management menu instead. A new chest is never made main automatically; players set it from the menu (and can always reach the menu with `/eclist`).

One exception: while a player has a **temporary chest** holding spilled items (for example after a resize or a revoked rank chest), opening the ender chest always shows the menu, even with a main set. This makes sure the spilled items are seen. Once the player empties the temporary chest, it disappears and the main chest opens directly again.

## Customize Each Chest

Personalize a chest from the in-game menu, no commands needed:

- **Rename**: give it a display name, with optional colours
- **Choose an icon**: pick any item to show in the list
- **Sort**: merge stacks and reorder by type in one click

Each one can be switched off server-wide in the `features` section under `enderchest` in `config.yml`. See [Configuration](/docs/configuration/).

<div class="placeholder-row">
  <figure>
    <img alt="A chest's management menu with rename, icon, and set-as-main options" src="/screenshots/chest-detail.webp" />
    <figcaption>A chest's management screen: rename it, choose an icon, or set it as your main.</figcaption>
  </figure>
  <figure>
    <img alt="The rename prompt for an ender chest" src="/screenshots/rename.webp" />
    <figcaption>Renaming a chest; the name you enter becomes its inventory title.</figcaption>
  </figure>
</div>

<figure class="feature-figure">
  <img alt="The searchable item picker for choosing a chest icon" src="/screenshots/icon-picker.webp" />
  <figcaption>Pick any item as the chest's icon with the searchable item picker.</figcaption>
</figure>

## Admin Management

Admins can add, resize, and delete chests for any player with `/ee add`, `/ee resize`, and `/ee delete`. Deleting a player's main chest leaves them with no main until they pick a new one from the menu.

## Permission-Granted Chests

Hand out chests by rank instead of by command. The permission `enhancedechest.additional_amount.<count>.slot.<size>` grants that many chests at that size. Multiple nodes stack, grants sync on open, and removing a node removes those chests, moving any items to a temporary chest the player can take them back from. The player's base chest is always kept. See the [Permission Chests](/docs/access/permission-chests#permission-granted-chests) page.

## View Other Players' Chests

With `/ee view <player>` an admin opens a player's chest, online or offline, in the same management menu the owner sees. One chest opens its menu directly; with several, a picker lets you choose. Grant `admin.view` for a read-only look, add `admin.edit` to take/add items and to rename, re-icon, or sort the chest, and add `admin.clear` for a **Clear chest** button (with a confirmation) that empties it.

<figure class="feature-figure">
  <img alt="An admin viewing another player's chest, with the Clear chest button" src="/screenshots/admin-view-detail.webp" />
  <figcaption>An admin's view of another player's chest, with the admin-only Clear chest button.</figcaption>
</figure>
