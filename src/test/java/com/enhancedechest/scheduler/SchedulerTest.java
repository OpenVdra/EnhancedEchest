package com.enhancedechest.scheduler;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.MockBukkitInject;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link Scheduler} against a mocked Bukkit server (MockBukkit), proving that
 * Bukkit/Paper-dependent code can be driven under plain JUnit instead of a live server. MockBukkit's
 * Paper scheduler mocks delegate onto its classic {@code BukkitSchedulerMock}, so sync work needs an
 * explicit {@code performOneTick()} and async work needs {@code waitAsyncTasksFinished()} — nothing
 * runs on its own the way it would on a real server tick loop.
 */
@ExtendWith(MockBukkitExtension.class)
class SchedulerTest {

    @MockBukkitInject
    private ServerMock server;

    @MockBukkitInject(name = "testPlayer")
    private PlayerMock player;

    @MockBukkitInject(name = "EnhancedEchestTest")
    private Plugin plugin;

    private Scheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new Scheduler(plugin);
    }

    @Test
    void isFolia_falseUnderPlainMockServer() {
        assertFalse(scheduler.isFolia());
    }

    @Test
    void runAtEntity_runsAgainstTheEntity() {
        AtomicBoolean ran = new AtomicBoolean(false);
        scheduler.runAtEntity(player, task -> ran.set(true));
        server.getScheduler().performOneTick();
        assertTrue(ran.get());
    }

    @Test
    void runAsync_runsOffThread() {
        AtomicBoolean ran = new AtomicBoolean(false);
        scheduler.runAsync(task -> ran.set(true));
        server.getScheduler().waitAsyncTasksFinished();
        assertTrue(ran.get());
    }

    @Test
    void runNextTick_isDeferredNotImmediate() {
        AtomicBoolean ran = new AtomicBoolean(false);
        scheduler.runNextTick(task -> ran.set(true));
        assertFalse(ran.get());
        server.getScheduler().performOneTick();
        assertTrue(ran.get());
    }

    @Test
    void runAtLocation_runsAgainstTheRegion() {
        Location location = player.getLocation();
        AtomicBoolean ran = new AtomicBoolean(false);
        scheduler.runAtLocation(location, task -> ran.set(true));
        server.getScheduler().performOneTick();
        assertTrue(ran.get());
    }
}
