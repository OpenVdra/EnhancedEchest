# Custom Ender Chests

Each chest can hold **9, 18, 27, 36, 45 or 54 slots**, instead of the fixed 27 of the vanilla ender chest.

<img class="feature-shot" alt="Six ender chests stacked from 54 slots down to 9 slots" src="/screenshots/chest-sizes.webp" />

## Vanilla Feel

Players use the same vanilla ender chest as always. Right-click it or run `/ec` to open it, and the lid still opens and closes.

## Size by Rank

The size of a player's first chest is set with the `default-size` setting in `config.yml`. It defaults to `54`.

- Accepted sizes: `9`, `18`, `27`, `36`, `45`, `54`. Any other value is rounded to the nearest one.
- Set the size per rank with the `enhancedechest.default_size.<size>` permission. See [Permission Chests](/docs/access/permission-chests#default-size-permission).
- Admins can resize any single chest with `/ee resize`.
