# Larger Ender Chests

EnhancedEchest replaces the vanilla 27-slot ender chest with a configurable inventory of up to **54 slots**.

<img class="feature-shot" alt="An enhanced ender chest with 54 slots" src="https://github.com/user-attachments/assets/a1f8a60e-5f31-4a30-b91b-07c5ba9243bf" />

## Same Block, More Space

Players open their ender chest the same way they always have, by right-clicking an ender chest block, and get the larger inventory instead of the vanilla screen.

- Opens on right-click or via `/ec`
- The ender chest block keeps its open/close lid animation
- Size is configurable in multiples of 9, from 9 up to 54

## Configurable Size

The default size for a player's first chest is set with `enderchest.default-size` in `config.yml`. Admins can also resize any individual chest with `/ee resize`, and you can override the base size **per rank** with the `enhancedechest.default_size.<size>` permission.

- Valid sizes: `9`, `18`, `27`, `36`, `45`, `54`
- Invalid values are rounded to the nearest valid size
- Defaults to `54` (a full double chest)
- Per-player override by permission, see the [Permission Chests](/docs/access/permission-chests#default-size-permission) page
