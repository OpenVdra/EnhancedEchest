# Welcome to EnhancedEchest

**EnhancedEchest** is a free, open-source Minecraft plugin that upgrades the vanilla ender chest into a larger, persistent, multi-chest storage system. Instead of three cramped rows that live inside a single player file, every player gets ender chests of up to **54 slots**, saved to a real database so the contents survive restarts, world resets, and server migrations.

## Quick Navigation

<CardGrid>

<DocCard icon="📥" title="Installation" link="/docs/installation" desc="Install EnhancedEchest on your server in minutes." />

<DocCard icon="✨" title="Features" link="/docs/features" desc="Larger chests, multi-chest management, database storage, and more." />

<DocCard icon="⌨️" title="Commands" link="/docs/commands" desc="Full command reference with syntax and descriptions." />

<DocCard icon="🔐" title="Permissions" link="/docs/permissions" desc="Permission nodes for player and admin features." />

<DocCard icon="⚙️" title="Configuration" link="/docs/configuration" desc="Configure chest size, database, migration, and language." />

<DocCard icon="💾" title="Database" link="/docs/database" desc="Set up SQLite, MySQL, MariaDB, or PostgreSQL storage." />

</CardGrid>

## Why EnhancedEchest?

<CardGrid>

<FeatureCard icon="📦" title="More Space">
The vanilla ender chest holds 27 items. EnhancedEchest gives players a configurable chest of up to 54 slots (a full double chest) opened from the same ender chest block or the <code>/ec</code> command.
</FeatureCard>

<FeatureCard icon="🗂️" title="Multiple Chests Per Player">
Players are no longer limited to a single ender chest. Admins can grant extra chests, each with its own size and custom name, switchable from an in-game management menu.
</FeatureCard>

<FeatureCard icon="💾" title="Real Persistence">
All contents are stored in a database: SQLite out of the box, or MySQL / MariaDB / PostgreSQL for networks. Data is shared cleanly across restarts and, with a shared database, across servers.
</FeatureCard>

<FeatureCard icon="🛡️" title="Dupe-Proof by Design">
Contents are loaded fresh when a chest is opened and written immediately when it closes. A pending-save chain guarantees the next open never reads stale data, closing the door on reload-based duplication.
</FeatureCard>

</CardGrid>
