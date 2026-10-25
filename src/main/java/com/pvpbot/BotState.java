package com.pvpbot;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** Everything we remember about one bot. */
final class BotState {
    final UUID owner;
    final String tag;
    final ResourceKey<Level> dim;
    String skin;

    UUID entityId;
    int setupTicks = 3;
    int missing = 0;

    int level = 1;               // 0 easy, 1 mid, 2 hard, 3 extreme
    String kit = "diamondsword";

    // settings
    boolean running = true;
    boolean sprint = true;
    boolean crit = true;
    boolean strafe = true;
    boolean heal, pearl, mace, elytra, spear, crystal;

    // consumables
    int gapples, potions, pearls;

    // runtime
    int strafeDir = 1, strafeTimer, attackCd = 10, jumpCd, retreat;
    int busy, busyKind, healCd, pearlCd, pearlFlight = -1;
    int phase, phaseTicks, specialCd = 30;
    double cy;

    BotState(UUID owner, String tag, ResourceKey<Level> dim, String skin) {
        this.owner = owner;
        this.tag = tag;
        this.dim = dim;
        this.skin = skin;
    }
}
