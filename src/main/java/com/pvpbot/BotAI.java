package com.pvpbot;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * The fighting brain. Runs once per tick for every bot.
 * The bot is a Mannequin (no vanilla AI), so we move it ourselves with simple physics.
 */
final class BotAI {
    private static final Random RNG = new Random();

    // per-level tuning: {walk speed, base cooldown ticks, reach, miss chance, damage multiplier, reaction ticks}
    private static final double[][] LV = {
            {0.17, 22, 2.8, 0.30, 0.70, 14},   // easy
            {0.22, 14, 3.0, 0.15, 0.90, 8},    // mid
            {0.27, 10, 3.2, 0.06, 1.00, 4},    // hard
            {0.32, 8, 3.4, 0.00, 1.15, 1},     // extreme
    };

    static void tick(ServerLevel w, LivingEntity bot, ServerPlayer target, BotState s) {
        if (target == null || !target.isAlive() || target.isCreative() || target.isSpectator()) {
            idle(bot);
            return;
        }
        if (!s.running) {
            idle(bot);
            return;
        }
        double[] L = LV[s.level];
        double speedBase = L[0];
        int cdBase = (int) L[1];
        double reach = L[2];

        Vec3 bp = bot.position();
        Vec3 tp = target.position();
        double dx = tp.x - bp.x, dz = tp.z - bp.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double fx = dist > 1e-4 ? dx / dist : 0, fz = dist > 1e-4 ? dz / dist : 0;
        face(bot, dx, tp.y - bp.y, dz);

        Vec3 vel = bot.getDeltaMovement();
        double vy = vel.y;
        boolean ground = bot.onGround();
        if (!ground) vy = (vy - 0.08) * 0.98; else if (vy < 0) vy = 0;

        // ---- timers ----
        if (s.attackCd > 0) s.attackCd--;
        if (s.jumpCd > 0) s.jumpCd--;
        if (s.healCd > 0) s.healCd--;
        if (s.pearlCd > 0) s.pearlCd--;
        if (s.specialCd > 0) s.specialCd--;

        // ---- busy actions (eating / drinking): stand still, then apply ----
        if (s.busy > 0) {
            s.busy--;
            if (s.busy == 0) finishBusy(w, bot, s);
            move(bot, 0, vy, 0);
            return;
        }

        // ---- pearl in flight ----
        if (s.pearlFlight >= 0) {
            if (--s.pearlFlight == 0) {
                double ang = RNG.nextDouble() * Math.PI * 2;
                bot.setPos(tp.x + Math.cos(ang) * 3, tp.y, tp.z + Math.sin(ang) * 3);
                w.playSound(null, bot.getX(), bot.getY(), bot.getZ(), SoundEvents.ENDER_PEARL_THROW,
                        SoundSource.PLAYERS, 1f, 1f);
                s.pearlFlight = -1;
            }
            return;
        }

        float hp = bot.getHealth();
        float max = bot.getMaxHealth();

        // ---- healing ----
        if (s.heal && s.healCd == 0 && hp < max * 0.45f && (s.gapples > 0 || s.potions > 0)) {
            if (s.potions > 0 && (s.kit.equals("nethpot") || s.kit.equals("crystal"))) {
                s.potions--;
                bot.heal(8f);
                w.playSound(null, bot.getX(), bot.getY(), bot.getZ(), SoundEvents.SPLASH_POTION_BREAK,
                        SoundSource.PLAYERS, 1f, 1f);
                w.sendParticles(ParticleTypes.HEART, bot.getX(), bot.getY() + 1.5, bot.getZ(), 6, .3, .3, .3, 0);
                s.healCd = 12;
            } else if (s.gapples > 0) {
                s.gapples--;
                s.busy = 32;
                s.busyKind = 1;
                s.healCd = 60;
                return;
            }
        }

        // ---- pearl: close a big gap ----
        if (s.pearl && s.pearls > 0 && s.pearlCd == 0 && dist > 14) {
            s.pearls--;
            s.pearlCd = 100;
            s.pearlFlight = 18;
            w.playSound(null, bot.getX(), bot.getY(), bot.getZ(), SoundEvents.ENDER_PEARL_THROW,
                    SoundSource.PLAYERS, 1f, 1f);
            return;
        }

        // ---- special kits ----
        if (s.mace || s.elytra || s.spear) {
            if (specialKit(w, bot, target, s, L, fx, fz, dist, vy, ground)) return;
        }

        // ---- normal melee ----
        double sprintMul = s.sprint ? 1.0 : 0.77;
        double speed = speedBase * sprintMul;

        double forward;
        if (s.retreat > 0) {
            forward = -0.8;
            s.retreat--;
        } else if (dist > 2.6) {
            forward = 1.0;
        } else if (dist < 1.6) {
            forward = -0.5;
        } else {
            forward = 0.3;
        }

        double side = 0;
        if (s.strafe && dist < 6.0) {
            if (--s.strafeTimer <= 0) {
                s.strafeDir = RNG.nextBoolean() ? 1 : -1;
                s.strafeTimer = 8 + RNG.nextInt(20);
            }
            side = 0.85 * s.strafeDir;
        }
        double sx = -fz, sz = fx;
        double mx = (fx * forward + sx * side) * speed;
        double mz = (fz * forward + sz * side) * speed;

        // crit logic: jump when close, hit while falling
        boolean falling = !ground && vy < -0.05;
        if (s.crit && ground && dist < 3.8 && s.jumpCd == 0 && s.attackCd <= 4) {
            vy = 0.42;
            s.jumpCd = cdBase + 4;
        } else if (!s.crit && ground && bot.horizontalCollision) {
            vy = 0.42;
        } else if (ground && bot.horizontalCollision) {
            vy = 0.42;
        }

        // attack
        boolean inReach = bot.distanceToSqr(target) <= reach * reach;
        boolean canHit = s.attackCd == 0 && inReach && (!s.crit || falling || dist > 3.8 || vy <= 0.0 && !ground);
        if (!s.crit) canHit = s.attackCd == 0 && inReach;
        if (canHit) {
            boolean critHit = s.crit && falling;
            swing(w, bot, target, s, L, baseDamage(s.kit), critHit);
            s.attackCd = cdBase + RNG.nextInt(3) - (s.level >= 2 ? 1 : 0);
            if (s.level >= 1) s.retreat = 2 + RNG.nextInt(3);
        }

        move(bot, mx, vy, mz);
    }

    // ---------------------------------------------------------------- special kits

    /** Returns true if this tick was fully handled. */
    private static boolean specialKit(ServerLevel w, LivingEntity bot, ServerPlayer target, BotState s,
                                      double[] L, double fx, double fz, double dist, double vy, boolean ground) {
        double speed = L[0] * (s.sprint ? 1.0 : 0.77);
        Vec3 tp = target.position();
        Vec3 bp = bot.position();

        // phase 0 = approach, 1 = airborne (launched), 2 = dive / smash, 3 = recover
        switch (s.phase) {
            case 0 -> {
                if (s.specialCd == 0 && dist < (s.elytra ? 18 : 7)) {
                    boolean useElytra = s.elytra;
                    s.phase = 1;
                    s.phaseTicks = useElytra ? 40 : 14;
                    w.playSound(null, bot.getX(), bot.getY(), bot.getZ(),
                            useElytra ? SoundEvents.FIREWORK_ROCKET_LAUNCH : SoundEvents.WIND_CHARGE_THROW,
                            SoundSource.PLAYERS, 1f, 1f);
                    return true;
                }
                double mx = fx * speed, mz = fz * speed;
                if (dist < 3) { mx = -fz * speed * s.strafeDir; mz = fx * speed * s.strafeDir; }
                if (--s.strafeTimer <= 0) {
                    s.strafeDir = RNG.nextBoolean() ? 1 : -1;
                    s.strafeTimer = 10 + RNG.nextInt(15);
                }
                move(bot, mx, vy, mz);
                return true;
            }
            case 1 -> {
                // rise (wind burst / rocket)
                double up = s.elytra ? 0.9 : 0.75;
                double hx = s.elytra ? fx * 0.25 : fx * speed * 0.6;
                double hz = s.elytra ? fz * 0.25 : fz * speed * 0.6;
                w.sendParticles(s.elytra ? ParticleTypes.FIREWORK : ParticleTypes.GUST,
                        bot.getX(), bot.getY(), bot.getZ(), 2, .1, .1, .1, 0);
                move(bot, hx, up, hz);
                if (--s.phaseTicks <= 0) {
                    s.phase = 2;
                    s.phaseTicks = 60;
                    s.cy = bp.y;
                }
                return true;
            }
            case 2 -> {
                // dive at the target
                Vec3 to = tp.add(0, 0.3, 0).subtract(bp);
                double len = Math.max(0.001, to.length());
                double sp = s.elytra ? 1.3 : 1.0;
                Vec3 d = to.multiply(sp / len, sp / len, sp / len);
                move(bot, d.x, Math.min(d.y, -0.5), d.z);
                s.phaseTicks--;
                if (bot.distanceToSqr(target) <= 3.6 * 3.6 || (bot.onGround() && s.phaseTicks < 55)) {
                    double fall = Math.max(0, s.cy - bot.getY());
                    if (bot.distanceToSqr(target) <= 4.0 * 4.0 && fall > 1.5) {
                        double dmg = 6 + Math.min(fall, 25) * 1.6 + (s.kit.equals("spearmace") ? 6 : 0);
                        swing(w, bot, target, s, L, dmg, false);
                        w.playSound(null, bot.getX(), bot.getY(), bot.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY,
                                SoundSource.PLAYERS, 1f, 1f);
                        w.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 0.2, target.getZ(),
                                1, 0, 0, 0, 0);
                    }
                    s.phase = 3;
                    s.phaseTicks = 12 + (3 - s.level) * 6;
                } else if (s.phaseTicks <= 0) {
                    s.phase = 3;
                    s.phaseTicks = 10;
                }
                return true;
            }
            default -> {
                // short recovery: strafe away, then reset
                move(bot, -fx * speed * 0.7, vy, -fz * speed * 0.7);
                if (--s.phaseTicks <= 0) {
                    s.phase = 0;
                    s.specialCd = 30 + (3 - s.level) * 15 + RNG.nextInt(20);
                }
                return true;
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private static double baseDamage(String kit) {
        return switch (kit) {
            case "diamondsword" -> 10.0;
            case "nethpot", "crystal" -> 11.0;
            default -> 8.0;
        };
    }

    private static void swing(ServerLevel w, LivingEntity bot, ServerPlayer target, BotState s,
                              double[] L, double dmg, boolean crit) {
        bot.swing(InteractionHand.MAIN_HAND);
        if (RNG.nextDouble() < L[3]) return; // miss
        double d = dmg * L[4];
        if (crit) d *= 1.5;
        DamageSource src = w.damageSources().mobAttack(bot);
        boolean hit = target.hurtServer(w, src, (float) d);
        if (!hit) return;
        double kx = target.getX() - bot.getX(), kz = target.getZ() - bot.getZ();
        double kb = (s.sprint ? 0.8 : 0.4);
        target.knockback(kb, -kx, -kz);
        target.hurtMarked = true;
        SoundEvent snd = crit ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_STRONG;
        w.playSound(null, target.getX(), target.getY(), target.getZ(), snd, SoundSource.PLAYERS, 1f, 1f);
        if (crit) {
            w.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(),
                    10, .3, .4, .3, 0.1);
        }
    }

    private static void finishBusy(ServerLevel w, LivingEntity bot, BotState s) {
        if (s.busyKind == 1) {
            bot.heal(8f);
            bot.setAbsorptionAmount(Math.max(bot.getAbsorptionAmount(), 4f));
            w.playSound(null, bot.getX(), bot.getY(), bot.getZ(), SoundEvents.PLAYER_BURP,
                    SoundSource.PLAYERS, 1f, 1f);
            w.sendParticles(ParticleTypes.HEART, bot.getX(), bot.getY() + 1.5, bot.getZ(), 5, .3, .3, .3, 0);
        }
        s.busyKind = 0;
    }

    private static void idle(LivingEntity bot) {
        Vec3 v = bot.getDeltaMovement();
        double vy = bot.onGround() ? 0 : (v.y - 0.08) * 0.98;
        move(bot, 0, vy, 0);
    }

    private static void face(LivingEntity bot, double dx, double dy, double dz) {
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        bot.setYRot(yaw);
        bot.setYHeadRot(yaw);
        bot.setYBodyRot(yaw);
        bot.setXRot(pitch);
    }

    private static void move(LivingEntity bot, double vx, double vy, double vz) {
        Vec3 v = new Vec3(vx, vy, vz);
        bot.setDeltaMovement(v);
        bot.move(MoverType.SELF, v);
        bot.resetFallDistance();
    }
        }
