# Larger Ender Chests

EnhancedEchest replaces the vanilla 27-slot ender chest with a configurable inventory of up to **54 slots**.

<img class="feature-shot" alt="An enhanced ender chest with 54 slots" src="/screenshots/chest-54.webp" />

## Same Block, More Space

Players open their ender chest the same way they always have, by right-clicking an ender chest, and get the larger inventory instead of the vanilla screen.

- Opens on right-click or via `/ec`
- The ender chest block keeps its open/close lid animation
- Size is configurable in multiples of 9, from 9 up to 54

## Configurable Size

The default size for a player's first chest is set with `default-size` in `config.yml`. Admins can also resize any individual chest with `/ee resize`, and you can override the base size **per rank** with the `enhancedechest.default_size.<size>` permission.

- Valid sizes: `9`, `18`, `27`, `36`, `45`, `54`
- Invalid values are rounded to the nearest valid size
- Defaults to `54` (a full double chest)
- Per-player override by permission, see the [Permission Chests](/docs/access/permission-chests#default-size-permission) page

<div class="size-gallery">
  <figure>
    <img alt="An ender chest with 9 slots" src="/screenshots/chest-9.webp" />
    <figcaption>9 slots</figcaption>
  </figure>
  <figure>
    <img alt="An ender chest with 18 slots" src="/screenshots/chest-18.webp" />
    <figcaption>18 slots</figcaption>
  </figure>
  <figure>
    <img alt="An ender chest with 27 slots" src="/screenshots/chest-27.webp" />
    <figcaption>27 slots</figcaption>
  </figure>
  <figure>
    <img alt="An ender chest with 36 slots" src="/screenshots/chest-36.webp" />
    <figcaption>36 slots</figcaption>
  </figure>
  <figure>
    <img alt="An ender chest with 45 slots" src="/screenshots/chest-45.webp" />
    <figcaption>45 slots</figcaption>
  </figure>
  <figure>
    <img alt="An ender chest with 54 slots" src="/screenshots/chest-54.webp" />
    <figcaption>54 slots</figcaption>
  </figure>
</div>
