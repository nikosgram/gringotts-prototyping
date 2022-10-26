package com.oglofus.gringottsprototypingpaper;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.*;

// id = type + "_" + owner
record Account(String id, String owner, String type) {}

record Vec3d(int x, int y, int z) {
    @Override
    public String toString() {
        return x + "_" + y + "_" + z;
    }
}

// id = world + "_" + vec
record Vault(String id, Vec3d vec, UUID world, String[] accounts) {
    public static Vault create(Vec3d vec, UUID world, String[] accounts) {
        return new Vault(world + "_" + vec.toString(), vec, world, accounts);
    }

    public static Vault create(Location location, String[] accounts) {
        Vec3d vec3d = new Vec3d(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        UUID  world = location.getWorld().getUID();

        return create(vec3d, world, accounts);
    }

    public static String getId(Location location) {
        return location.getWorld().getUID() + "_" + location.getBlockX() + "_" + location.getBlockY() + "_" +
                location.getBlockZ();
    }
}

public final class GringottsPrototypingPaper extends JavaPlugin implements Listener {
    public static final List<UUID>                VAULT_CREATION_PLAYERS  = new ArrayList<>();
    public static final Map<UUID, UUID>           VAULT_APPENDING_PLAYERS = new HashMap<>();
    // account_id -> account
    public static final Map<String, Account>      ACCOUNT_MAP             = new HashMap<>();
    // vault_id -> vault
    public static final Map<String, Vault>        VAULT_MAP               = new HashMap<>();
    // account_id -> []vault_id
    public static final Map<String, List<String>> VAULT_LINK_MAP          = new HashMap<>();

    public static @Nullable Vault getVault(Block block) {
        Location vaultLocation = calculateVaultLocation(block);

        if (vaultLocation == null) {
            return null;
        }

        String vaultId = Vault.getId(vaultLocation);

        return VAULT_MAP.getOrDefault(vaultId, null);
    }

    public static @Nullable Location calculateVaultLocation(Block block) {
        BlockState blockState = block.getState();

        if (blockState instanceof Chest chest) {
            Inventory inventory = chest.getInventory();

            System.out.println(inventory.getClass().getName());

            if (inventory instanceof DoubleChestInventory) {
                DoubleChest doubleChest = (DoubleChest) inventory.getHolder();

                if (doubleChest == null) {
                    return null;
                }

                Chest rightChest = (Chest) doubleChest.getRightSide();

                if (rightChest == null) {
                    return null;
                }

                String vaultId = Vault.getId(rightChest.getLocation());

                if (VAULT_MAP.containsKey(vaultId)) {
                    return rightChest.getLocation();
                }

                Chest leftChest = (Chest) doubleChest.getLeftSide();

                if (leftChest == null) {
                    return null;
                }

                return leftChest.getLocation();
            } else {
                return chest.getLocation();
            }
        } else if (blockState instanceof ShulkerBox) {
            return block.getLocation();
        } else return null;
    }

    @Override
    public void onLoad() {
        // Plugin load logic

    }

    @Override
    public void onEnable() {
        // Plugin startup logic

        Bukkit.getPluginManager().registerEvents(this, this);
        PluginCommand vaultCommandPlugin = getCommand("vault");

        if (vaultCommandPlugin != null) {
            VaultCommand vaultCommand = new VaultCommand();

            vaultCommandPlugin.setExecutor(vaultCommand);
            vaultCommandPlugin.setTabCompleter(vaultCommand);
        } else {
            getLogger().warning("vault command is occupied");
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String id     = "player_" + player.getUniqueId();

        if (ACCOUNT_MAP.containsKey(id)) {
            return;
        }

        Account account = new Account(id, player.getUniqueId().toString(), "player");

        ACCOUNT_MAP.put(id, account);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player     = event.getPlayer();
        UUID   playerUuid = player.getUniqueId();

        if (VAULT_CREATION_PLAYERS.contains(playerUuid) || VAULT_APPENDING_PLAYERS.containsKey(playerUuid)) {
            event.setCancelled(true);

            return;
        }

        Vault vault = getVault(event.getBlock());

        if (vault == null) {
            return;
        }

        String   accountId = "player_" + player.getUniqueId();
        String[] accounts  = vault.accounts();

        for (String account : accounts) {
            if (account.equals(accountId)) {
                VAULT_LINK_MAP.get(accountId).remove(vault.id());
                VAULT_MAP.remove(vault.id());

                player.sendMessage(Component.text("vault destroyed"));

                return;
            }
        }

        event.setCancelled(true);

        player.sendMessage(Component.text("no access"));
    }

    @EventHandler
    public void giveAccessHandler(PlayerInteractEvent event) {
        Player player     = event.getPlayer();
        UUID   playerUuid = player.getUniqueId();

        if (!VAULT_APPENDING_PLAYERS.containsKey(playerUuid)) {
            return;
        }

        event.setCancelled(true);

        UUID   targetPlayerUuid = VAULT_APPENDING_PLAYERS.get(playerUuid);
        String targetAccountId  = "player_" + targetPlayerUuid;

        VAULT_APPENDING_PLAYERS.remove(playerUuid);

        Account account = new Account(targetAccountId, targetPlayerUuid.toString(), "player");

        ACCOUNT_MAP.put(targetAccountId, account);

        Block block = event.getClickedBlock();

        if (block == null) {
            player.sendMessage(Component.text("that's not a chest. lol"));

            return;
        }

        Vault vault = getVault(block);

        if (vault == null) {
            player.sendMessage(Component.text("that's not a vault. lol"));

            return;
        }

        String   accountId = "player_" + playerUuid;
        String[] accounts  = vault.accounts();

        for (String foundAccountId : accounts) {
            if (foundAccountId.equals(accountId)) {
                Vault newVault = new Vault(vault.id(), vault.vec(), vault.world(), new String[accounts.length + 1]);

                newVault.accounts()[0] = targetAccountId;

                System.arraycopy(accounts, 0, newVault.accounts(), 1, accounts.length);

                VAULT_MAP.put(vault.id(), vault);

                if (!VAULT_LINK_MAP.containsKey(accountId)) {
                    VAULT_LINK_MAP.put(accountId, new ArrayList<>());
                }

                VAULT_LINK_MAP.get(accountId).add(vault.id());

                player.sendMessage(Component.text("no access"));

                return;
            }
        }

        event.setCancelled(true);

        player.sendMessage(Component.text("no access"));
    }

    @EventHandler
    public void createVaultHandler(PlayerInteractEvent event) {
        Player player     = event.getPlayer();
        UUID   playerUuid = player.getUniqueId();

        if (!VAULT_CREATION_PLAYERS.contains(playerUuid)) {
            return;
        }

        event.setCancelled(true);

        VAULT_CREATION_PLAYERS.remove(playerUuid);

        Block block = event.getClickedBlock();

        if (block == null) {
            player.sendMessage(Component.text("that's not a chest. lol"));

            return;
        }

        Location vaultLocation = calculateVaultLocation(block);

        if (vaultLocation == null) {
            return;
        }

        String vaultId = Vault.getId(vaultLocation);

        if (VAULT_MAP.containsKey(vaultId)) {
            player.sendMessage(Component.text("this vault already exists"));

            return;
        }

        String accountId = "player_" + player.getUniqueId();

        Vault vault = Vault.create(vaultLocation, new String[]{accountId});

        VAULT_MAP.put(vaultId, vault);

        if (!VAULT_LINK_MAP.containsKey(accountId)) {
            VAULT_LINK_MAP.put(accountId, new ArrayList<>());
        }

        VAULT_LINK_MAP.get(accountId).add(vaultId);

        player.sendMessage(Component.text("vault created"));
    }
}
