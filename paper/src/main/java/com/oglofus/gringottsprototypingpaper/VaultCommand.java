package com.oglofus.gringottsprototypingpaper;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VaultCommand implements CommandExecutor, TabCompleter {
    public static final List<String> commands = new ArrayList<>();

    static {
        commands.add("add");
        commands.add("create");
        commands.add("list");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (args.length < 1) {
            return false;
        }

        UUID   playerUuid = player.getUniqueId();
        String accountId  = "player_" + playerUuid;

        switch (args[0].toLowerCase()) {
            case "add" -> {
                if (args.length < 2) {
                    return false;
                }

                String targetPlayer     = args[1];
                UUID   targetPlayerUuid = Bukkit.getPlayerUniqueId(targetPlayer);

                if (targetPlayerUuid == null) {
                    player.sendMessage(
                            Component.text("player `" + targetPlayer + "` has never played on this server before!"));

                    return true;
                }

                if (GringottsPrototypingPaper.VAULT_CREATION_PLAYERS.contains(playerUuid)) {
                    GringottsPrototypingPaper.VAULT_CREATION_PLAYERS.remove(playerUuid);

                    return true;
                }

                GringottsPrototypingPaper.VAULT_APPENDING_PLAYERS.put(playerUuid, targetPlayerUuid);

                player.sendMessage(Component.text("Click on a chest to give access to `" + targetPlayer + "`!"));

                return true;
            }
            case "create" -> {
                if (GringottsPrototypingPaper.VAULT_APPENDING_PLAYERS.containsKey(playerUuid)) {
                    GringottsPrototypingPaper.VAULT_APPENDING_PLAYERS.remove(playerUuid);

                    return true;
                }

                GringottsPrototypingPaper.VAULT_CREATION_PLAYERS.add(playerUuid);

                player.sendMessage(Component.text("Click on a chest to create a new vault!"));

                return true;
            }
            case "list" -> {
                List<String> vaults = GringottsPrototypingPaper.VAULT_LINK_MAP.getOrDefault(accountId, null);

                if (vaults == null) {
                    player.sendMessage(Component.text("You have no vaults!"));

                    return true;
                }

                Component component = Component.empty();

                for (String vaultId : vaults) {
                    component = component.append(Component.text(vaultId)).append(Component.newline());
                }

                player.sendMessage(component);

                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return null;
        }

        return commands;
    }
}
