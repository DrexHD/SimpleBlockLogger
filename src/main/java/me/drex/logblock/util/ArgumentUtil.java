package me.drex.logblock.util;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.drex.logblock.BlockLog;
import me.drex.logblock.database.DBCache;
import net.minecraft.block.Block;
import net.minecraft.server.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.minecraft.server.command.CommandManager.argument;

public class ArgumentUtil {

    public static final SuggestionProvider<ServerCommandSource> USER = (source, builder) -> {
        List<String> strings = new ArrayList<>();
        strings.add("-everyone");
        for (ServerPlayerEntity player : BlockLog.server.getPlayerManager().getPlayerList()) {
            strings.add(player.getEntityName());
        }
        return CommandSource.suggestMatching(strings, builder);
    };

    public static final SuggestionProvider<ServerCommandSource> PLAYER = (source, builder) -> {
        List<String> strings = new ArrayList<>();
        for (ServerPlayerEntity player : BlockLog.server.getPlayerManager().getPlayerList()) {
            strings.add(player.getEntityName());
        }
        return CommandSource.suggestMatching(strings, builder);
    };
    public static final SuggestionProvider<ServerCommandSource> RADIUS = (source, builder) -> {
        List<String> strings = new ArrayList<>();
        strings.add("-global");
        if (builder.getRemaining().isEmpty()) {
            for (int i = 1; i < 10; i++) {
                strings.add(String.valueOf(i));
            }
        } else if (builder.getRemaining().matches("[\\d]{1,3}")) {
            for (int i = 0; i < 10; i++) {
                strings.add(builder.getRemaining() + i);
            }
        }
        return CommandSource.suggestMatching(strings, builder);
    };
    public static final SuggestionProvider<ServerCommandSource> BLOCK = (source, builder) -> {
        List<String> strings = new ArrayList<>();
        strings.add("-all");
        for (Block block : Registry.BLOCK) {
            strings.add(BlockUtil.toNameNoNameSpace(block));
        }
        return CommandSource.suggestMatching(strings, builder);
    };
    public static final SuggestionProvider<ServerCommandSource> TIME = (source, builder) -> {
        List<String> strings = new ArrayList<>();
        strings.add("-always");
        if (builder.getRemaining().matches("[\\d]")) {
            strings.add(builder.getRemaining() + "s");
            strings.add(builder.getRemaining() + "m");
            strings.add(builder.getRemaining() + "h");
            strings.add(builder.getRemaining() + "d");
            strings.add(builder.getRemaining() + "w");
        }
        return CommandSource.suggestMatching(strings, builder);
    };

    public static RequiredArgumentBuilder<ServerCommandSource, String> getUser() {
        return argument("user", word()).suggests(USER);
    }

    public static RequiredArgumentBuilder<ServerCommandSource, String> getPlayer() {
        return argument("player", word()).suggests(PLAYER);
    }

    public static RequiredArgumentBuilder<ServerCommandSource, String> getRadius() {
        return argument("radius", word()).suggests(RADIUS);
    }

    public static RequiredArgumentBuilder<ServerCommandSource, String> getBlock() {
        return argument("block", word()).suggests(BLOCK);
    }

    public static RequiredArgumentBuilder<ServerCommandSource, String> getTime() {
        return argument("time", word()).suggests(TIME);
    }

    public static String parseUser(CommandContext<ServerCommandSource> context) throws SQLException, CommandSyntaxException {
        String input = StringArgumentType.getString(context, "user");
        if (input.equals("-everyone")) {
            return "";
        } else {
            DBCache cache = BlockLog.getCache();
            int userID = cache.getEntity(input);
            //if userID is 0, that means the value didnt exist in the databse
            if (userID == 0) {
                GameProfile profile = BlockLog.server.getUserCache().findByName(input);
                if (profile == null || !profile.isComplete()) {
                    throw new SimpleCommandExceptionType(new LiteralText("Couldn't find player!")).create();
                }
                userID = cache.getEntity(profile.getId().toString());
                if (userID == 0) {
                    throw new SimpleCommandExceptionType(new LiteralText("Couldn't find user!")).create();
                }
            }
            return "entityid = " + userID;
        }
    }

    public static GameProfile parsePlayer(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String input = StringArgumentType.getString(context, "user");
        GameProfile profile = BlockLog.server.getUserCache().findByName(input);
        if (profile == null || !profile.isComplete()) {
            throw new SimpleCommandExceptionType(new LiteralText("Couldn't find player!")).create();
        }
        return profile;
    }


    public static String parseRadius(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String input = StringArgumentType.getString(context, "radius");
        if (input.equals("-global")) {
            return "";
        }
        BlockPos pos = context.getSource().getPlayer().getBlockPos();

        int radius;
        try {
            radius = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new SimpleCommandExceptionType(new LiteralText("Radius must be an integer!")).create();
        }

        if (radius < 1) {
            throw new SimpleCommandExceptionType(new LiteralText("Radius must be larger than 0!")).create();
        }
        return "x BETWEEN " + (pos.getX() - radius) + " AND " + (pos.getX() + radius) + " AND " + " y BETWEEN " + 0 + " AND " + 256 + " AND " + " z BETWEEN " + (pos.getZ() - radius) + " AND " + (pos.getZ() + radius);
    }

    public static String parseBlock(CommandContext<ServerCommandSource> context) throws SQLException, CommandSyntaxException {
        String input = StringArgumentType.getString(context, "block");
        if (input.equals("-all")) {
            return "";
        } else {
            DBCache cache = BlockLog.getCache();
            int blockID = cache.getBlock("minecraft:" + input);
            if (blockID == 0) {
                throw new SimpleCommandExceptionType(new LiteralText("Couldn't find block!")).create();
            } else {
                return "pblockid = " + blockID + " OR blockid = " + blockID;
            }
        }
    }

    public static String parseTime(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String input = StringArgumentType.getString(context, "time");
        if (input.equals("-always")) {
            return "";
        } else {
            if (input.matches("[\\d]+(s|m|h|d|w)")) {
                String numbers = input.substring(0, input.length() - 1);
                char c = input.substring(input.length() - 1).charAt(0);
                long time = Long.parseLong(numbers);
                switch (c) {
                    case 's':
                        time = time;
                        break;
                    case 'm':
                        time = time * 60;
                        break;
                    case 'h':
                        time = time * 60 * 60;
                        break;
                    case 'd':
                        time = time * 60 * 60 * 24;
                        break;
                    case 'w':
                        time = time * 60 * 60 * 24 * 4;
                        break;
                }
                time = (System.currentTimeMillis() - time);
                return "time >= " + time;
            } else {
                throw new SimpleCommandExceptionType(new LiteralText("Invalid time format!")).create();
            }
        }
    }


}
