# Permissions

All nodes live under the `enhancedechest.` namespace, default to `op`, and hide their command when missing. Click any node to copy it.

::: info Right-clicking the block needs no permission
`enhancedechest.command.open` only gates the commands — right-clicking an ender chest block always works for everyone. To let all players use `/enderchest` and `/eclist`, grant it to the default group (set it to `true`).
:::

## Default Values

| Value | Meaning |
|-------|---------|
| `op` | Server operators only (the default for every node) |
| `true` | All players by default |
| `false` | Nobody by default; must be granted explicitly |

## Player Permissions

<BaseTable :columns="['Permission', 'Description', 'Default']" grid="2fr 3fr 0.6fr">

<PermRow permission="enhancedechest.command.open" defaultVal="op">
Open the GUI by command: <code>/enderchest</code> (<code>/ec</code>), <code>/eclist</code>, and <code>/enderchest #&lt;index&gt;</code> / <code>&lt;name&gt;</code>.
</PermRow>

</BaseTable>

## Admin Permissions

Admin commands need the base `enhancedechest.admin` **plus** the specific node below — the base alone grants nothing.

<BaseTable :columns="['Permission', 'Description', 'Default']" grid="2fr 3fr 0.6fr">

<PermRow permission="enhancedechest.admin" defaultVal="op">
Base node for every <code>/enhancedechest</code> (<code>/ee</code>) command — combine with a node below.
</PermRow>

<PermRow permission="enhancedechest.admin.reload" defaultVal="op">
Use <code>/ee reload</code>.
</PermRow>

<PermRow permission="enhancedechest.admin.migrate.run" defaultVal="op">
Use <code>/ee migrate run</code>.
</PermRow>

<PermRow permission="enhancedechest.admin.add" defaultVal="op">
Use <code>/ee add</code>.
</PermRow>

<PermRow permission="enhancedechest.admin.resize" defaultVal="op">
Use <code>/ee resize</code>.
</PermRow>

<PermRow permission="enhancedechest.admin.delete" defaultVal="op">
Use <code>/ee delete</code>.
</PermRow>

</BaseTable>
