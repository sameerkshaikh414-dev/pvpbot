package com.pvpbot;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Spawns bots (as vanilla Mannequin entities, which render with a real player skin),
 * keeps their settings, and hands them to BotAI every tick.
 * Written with Mojang mappings (Minecraft 26.x).
 */
final class BotManager {
    static final List<BotState> BOTS = new ArrayList<>();
    private static int counter = 0;

    // setupTicks <= READY means the mannequin exists and is fully set up
    private static final int READY = -1000;

    // ---------- enchant strings (component syntax) ----------
    private static final String PROT = "[enchantments={\"minecraft:protection\":4,\"minecraft:unbreaking\":3}]";
    private static final String BOOT = "[enchantments={\"minecraft:protection\":4,\"minecraft:unbreaking\":3,\"minecraft:feather_falling\":4}]";
    private static final String SHARP = "[enchantments={\"minecraft:sharpness\":5,\"minecraft:unbreaking\":3}]";
    private static final String MACE = "[enchantments={\"minecraft:density\":5,\"minecraft:wind_burst\":3,\"minecraft:unbreaking\":3}]";

    // ---------- helpers ----------
    static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    static String pos(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.2f %.2f %.2f", x, y, z);
    }

    private static List<BotState> owned(UUID owner) {
        List<BotState> out = new ArrayList<>();
        for (BotState s : BOTS) if (s.owner.equals(owner)) out.add(s);
        return out;
    }

    private static int fail(CommandSourceStack src, String msg) {
        src.sendFailure(Component.literal(msg));
        return 0;
    }

    private static int ok(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private static boolean validName(String n) {
        return n.matches("[A-Za-z0-9_]{1,16}");
    }

    // ---------- /bot ----------
    static int spawn(CommandSourceStack src, String name, String levelStr) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return fail(src, "Run this as a player.");
        if (!validName(name)) return fail(src, "Name must be 1-16 letters/numbers/underscores.");
        int level;
        switch (norm(levelStr)) {
            case "easy" -> level = 0;
            case "mid", "medium" -> level = 1;
            case "hard" -> level = 2;
            case "extreme" -> level = 3;
            default -> {
                return fail(src, "Level must be easy, mid, hard or extreme.");
            }
        }

        ServerLevel w = src.getLevel();
        double yaw = Math.toRadians(p.getYRot());
        double x = p.getX() - Math.sin(yaw) * 4.0;
        double z = p.getZ() + Math.cos(yaw) * 4.0;

        String tag = "pvpbot_" + (++counter) + "_" + (System.currentTimeMillis() % 100000);
        BotState s = new BotState(p.getUUID(), tag, w.dimension(), name);
        s.level = level;
        UUID id = UUID.randomUUID();
        s.entityId = id;
        BOTS.add(s);

        long hi = id.getMostSignificantBits(), lo = id.getLeastSignificantBits();
        String uuidNbt = "[I;" + (int) (hi >> 32) + "," + (int) hi + "," + (int) (lo >> 32) + "," + (int) lo + "]";
        String nbt = "{UUID:" + uuidNbt + ",profile:\"" + name + "\",CustomName:\"" + name
                + "\",CustomNameVisible:1b,immovable:0b,hide_description:1b}";
        // run as the player so it lands in the player's dimension
        src.getServer().getCommands().performPrefixedCommand(
                p.createCommandSourceStack().withSuppressedOutput(),
                "summon minecraft:mannequin " + pos(x, p.getY(), z) + " " + nbt);

        return ok(src, "Spawning " + name + " (" + norm(levelStr) + "). Fight in survival mode - "
                + "the bot only attacks you.");
    }

    static int clear(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return fail(src, "Run this as a player.");
        int n = 0;
        for (BotState s : new ArrayList<>(owned(p.getUUID()))) {
            for (ServerLevel w : src.getServer().getAllLevels()) {
                if (s.entityId != null) {
                    Entity e = w.getEntity(s.entityId);
                    if (e != null) e.discard();
                }
            }
            BOTS.remove(s);
            n++;
        }
        return ok(src, "Removed " + n + " bot(s).");
    }

    static int skin(CommandSourceStack src, String user) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return fail(src, "Run this as a player.");
        if (!validName(user)) return fail(src, "That doesn't look like a valid username.");
        List<BotState> mine = owned(p.getUUID());
        if (mine.isEmpty()) return fail(src, "Spawn a bot first: /bot <name> <level>");
        for (BotState s : mine) {
            s.skin = user;
            if (s.setupTicks <= READY) {
                run(src.getServer(), "data merge entity " + s.entityId + " {profile:\"" + user + "\"}");
            }
        }
        return ok(src, "Skin set to " + user + " (needs internet to download it).");
    }

    // ---------- /botsetting ----------
    static int setFlag(CommandSourceStack src, String key, Boolean val) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return fail(src, "Run this as a player.");
        List<BotState> mine = owned(p.getUUID());
        if (mine.isEmpty()) return fail(src, "Spawn a bot first: /bot <name> <level>");
        boolean last = false;
        for (BotState s : mine) {
            boolean cur = switch (key) {
                case "start" -> s.running;
                case "sprint" -> s.sprint;
                case "crit" -> s.crit;
                case "strafing", "streffing" -> s.strafe;
                case "heal" -> s.heal;
                case "pearl" -> s.pearl;
                case "macepvp" -> s.mace;
                case "elytramace" -> s.elytra;
                case "spearmace" -> s.spear;
                default -> false;
            };
            boolean nv = val == null ? !cur : val;
            switch (key) {
                case "start" -> s.running = nv;
                case "sprint" -> s.sprint = nv;
                case "crit" -> s.crit = nv;
                case "strafing", "streffing" -> s.strafe = nv;
                case "heal" -> {
                    s.heal = nv;
                    if (nv && s.gapples + s.potions == 0) s.gapples = 6;
                }
                case "pearl" -> {
                    s.pearl = nv;
                    if (nv && s.pearls == 0) s.pearls = 8;
                }
                case "macepvp" -> s.mace = nv;
                case "elytramace" -> s.elytra = nv;
                case "spearmace" -> s.spear = nv;
                default -> { }
            }
            last = nv;
        }
        String shown = key.equals("start") ? (last ? "bot started" : "bot paused")
                : key + (last ? " ON" : " OFF");
        return ok(src, shown);
    }

    // ---------- /botkit ----------
    static int kit(CommandSourceStack src, String raw) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return fail(src, "Run this as a player.");
        List<BotState> mine = owned(p.getUUID());
        if (mine.isEmpty()) return fail(src, "Spawn a bot first: /bot <name> <level>");
        String k = switch (norm(raw)) {
            case "diamondsword", "diamond", "sword" -> "diamondsword";
            case "nethpot", "netheritepot", "pot" -> "nethpot";
            case "crystal", "crystalpvp" -> "crystal";
            case "mace" -> "mace";
            case "elytramace" -> "elytramace";
            case "spearmace" -> "spearmace";
            default -> null;
        };
        if (k == null) return fail(src, "Kits: diamond sword, nethpot, crystal, mace, elytra mace, spearmace");
        String note = "";
        for (BotState s : mine) {
            s.kit = k;
            if (s.setupTicks <= READY) note = applyKit(src.getServer(), s);
        }
        return ok(src, "Kit: " + raw + (note.isEmpty() ? "" : " - " + note));
    }

    private static boolean itemExists(String path) {
        return BuiltInRegistries.ITEM.keySet().stream().anyMatch(k -> k.getPath().equals(path));
    }

    /** Sets gear + matching AI flags for s.kit. Returns an optional note for the player. */
    static String applyKit(MinecraftServer server, BotState s) {
        String id = s.entityId.toString();
        String k = s.kit;
        String mat = k.equals("diamondsword") ? "diamond" : "netherite";
        String note = "";

        s.heal = s.pearl = s.mace = s.elytra = s.spear = s.crystal = false;
        s.gapples = 8; s.potions = 0; s.pearls = 0;
        s.phase = 0; s.phaseTicks = 0; s.specialCd = 30;

        String head = "minecraft:" + mat + "_helmet" + PROT;
        String chest = "minecraft:" + mat + "_chestplate" + PROT;
        String legs = "minecraft:" + mat + "_leggings" + PROT;
        String feet = "minecraft:" + mat + "_boots" + BOOT;
        String main;
        String off;

        switch (k) {
            case "nethpot" -> {
                main = "minecraft:netherite_sword" + SHARP;
                off = "minecraft:splash_potion[potion_contents={potion:\"minecraft:strong_healing\"}]";
                s.heal = true; s.pearl = true; s.potions = 16; s.gapples = 6; s.pearls = 8;
            }
            case "crystal" -> {
                main = "minecraft:netherite_sword" + SHARP;
                off = "minecraft:end_crystal";
                s.crystal = true; s.heal = true; s.pearl = true; s.pearls = 8;
            }
            case "mace" -> {
                main = "minecraft:mace" + MACE;
                off = "minecraft:wind_charge";
                s.mace = true; s.heal = true;
            }
            case "elytramace" -> {
                main = "minecraft:mace" + MACE;
                off = "minecraft:firework_rocket";
                chest = "minecraft:elytra[enchantments={\"minecraft:unbreaking\":3}]";
                s.mace = true; s.elytra = true; s.heal = true;
            }
            case "spearmace" -> {
                main = "minecraft:mace" + MACE;
                if (itemExists("netherite_spear")) {
                    off = "minecraft:netherite_spear";
                    s.spear = true;
                } else {
                    off = "minecraft:wind_charge";
                    note = "this version has no spear item, using a plain mace kit";
                }
                s.mace = true; s.heal = true;
            }
            default -> {
                main = "minecraft:diamond_sword" + SHARP;
                off = "minecraft:golden_apple";
                s.heal = true;
            }
        }

        set(server, id, "armor.head", head);
        set(server, id, "armor.chest", chest);
        set(server, id, "armor.legs", legs);
        set(server, id, "armor.feet", feet);
        set(server, id, "weapon.mainhand", main);
        set(server, id, "weapon.offhand", off);
        return note;
    }

    private static void set(MinecraftServer server, String id, String slot, String item) {
        run(server, "item replace entity " + id + " " + slot + " with " + item);
    }

    // ---------- per-tick ----------
    static void tickWorld(ServerLevel w) {
        if (BOTS.isEmpty()) return;
        Iterator<BotState> it = BOTS.iterator();
        while (it.hasNext()) {
            BotState s = it.next();
            if (!s.dim.equals(w.dimension())) continue;

            Player pe = w.getPlayerByUUID(s.owner);
            ServerPlayer owner = pe instanceof ServerPlayer sp ? sp : null;

            // waiting for the summon command to actually create the entity
            if (s.setupTicks > READY) {
                if (--s.setupTicks > 0) continue;
                Entity found = w.getEntity(s.entityId);
                if (found == null) {
                    if (s.setupTicks < -40) {
                        if (owner != null) owner.sendSystemMessage(Component.literal(
                                "Couldn't spawn the bot (mannequin summon failed)."));
                        it.remove();
                    }
                    continue;
                }
                s.setupTicks = READY;
                run(w.getServer(), "attribute " + s.entityId + " minecraft:fall_damage_multiplier base set 0");
                String note = applyKit(w.getServer(), s);
                if (owner != null && !note.isEmpty()) owner.sendSystemMessage(Component.literal(note));
                continue;
            }

            Entity e = w.getEntity(s.entityId);
            if (!(e instanceof LivingEntity bot)) {
                if (++s.missing > 200) it.remove();
                continue;
            }
            s.missing = 0;
            if (!bot.isAlive()) {
                it.remove();
                continue;
            }
            BotAI.tick(w, bot, owner, s);
        }
    }
        }
                            
