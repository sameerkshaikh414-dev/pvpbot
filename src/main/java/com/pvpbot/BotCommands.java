package com.pvpbot;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** Registers every command twice (lowercase + Capitalised) because phone keyboards auto-capitalise. */
final class BotCommands {
    private static final String[] LEVELS = {"easy", "mid", "hard", "extreme"};
    private static final String[] KITS = {"diamond sword", "nethpot", "crystal", "mace", "elytra mace", "spearmace"};
    private static final String[] FLAGS = {"sprint", "crit", "strafing", "streffing", "heal", "pearl",
            "macepvp", "elytramace", "spearmace"};

    static void register(CommandDispatcher<CommandSourceStack> d) {
        for (String n : new String[]{"bot", "Bot"}) d.register(bot(n));
        for (String n : new String[]{"botsetting", "Botsetting"}) d.register(setting(n));
        for (String n : new String[]{"botkit", "Botkit"}) d.register(kit(n));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> bot(String n) {
        return Commands.literal(n)
                .then(Commands.literal("clear")
                        .executes(c -> BotManager.clear(c.getSource())))
                .then(Commands.literal("skin")
                        .then(Commands.argument("username", StringArgumentType.word())
                                .executes(c -> BotManager.skin(c.getSource(),
                                        StringArgumentType.getString(c, "username")))))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> BotManager.spawn(c.getSource(),
                                StringArgumentType.getString(c, "name"), "mid"))
                        .then(Commands.argument("level", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (String s : LEVELS) b.suggest(s);
                                    return b.buildFuture();
                                })
                                .executes(c -> BotManager.spawn(c.getSource(),
                                        StringArgumentType.getString(c, "name"),
                                        StringArgumentType.getString(c, "level")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> setting(String n) {
        LiteralArgumentBuilder<CommandSourceStack> b = Commands.literal(n);
        b.then(Commands.literal("start").executes(c -> BotManager.setFlag(c.getSource(), "start", true)));
        b.then(Commands.literal("pause").executes(c -> BotManager.setFlag(c.getSource(), "start", false)));
        b.then(Commands.literal("nocrit").executes(c -> BotManager.setFlag(c.getSource(), "crit", false)));
        for (String key : FLAGS) {
            b.then(Commands.literal(key)
                    .executes(c -> BotManager.setFlag(c.getSource(), key, null))
                    .then(Commands.literal("on")
                            .executes(c -> BotManager.setFlag(c.getSource(), key, Boolean.TRUE)))
                    .then(Commands.literal("off")
                            .executes(c -> BotManager.setFlag(c.getSource(), key, Boolean.FALSE))));
        }
        return b;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> kit(String n) {
        return Commands.literal(n)
                .then(Commands.argument("kit", StringArgumentType.greedyString())
                        .suggests((c, b) -> {
                            for (String s : KITS) b.suggest(s);
                            return b.buildFuture();
                        })
                        .executes(c -> BotManager.kit(c.getSource(),
                                StringArgumentType.getString(c, "kit"))));
    }
          }
