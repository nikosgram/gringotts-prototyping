package com.oglofus.gringottsprototypingpaper;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

// id = type + "_" + owner
record Account(String id, String owner, String type) {
    public double calculateMoney() {
        return GringottsPrototypingPaper.VAULT_LINK_MAP.getOrDefault(id, new ArrayList<>()).stream()
                .map(GringottsPrototypingPaper.VAULT_MAP::get).mapToDouble(vault -> {
                    UUID  worldUuid = vault.world();
                    World world     = Bukkit.getWorld(worldUuid);

                    if (world == null) {
                        return 0;
                    }

                    Vec3d vec3d = vault.vec();

                    Block      block      = world.getBlockAt(vec3d.x(), vec3d.y(), vec3d.z());
                    BlockState blockState = block.getState();

                    double total = 0;

                    if (blockState instanceof Container container) {
                        total += container.getInventory().all(Material.EMERALD).values().parallelStream()
                                .mapToDouble(ItemStack::getAmount).sum();
                        total += container.getInventory().all(Material.EMERALD_BLOCK).values().parallelStream()
                                .mapToDouble(ItemStack::getAmount).sum() * 9;
                    }

                    return total;
                }).sum();

    }
}

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

class VaultCommand implements CommandExecutor, TabCompleter {
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

                GringottsPrototypingPaper.VAULT_CREATION_PLAYERS.remove(playerUuid);

                GringottsPrototypingPaper.VAULT_APPENDING_PLAYERS.put(playerUuid, targetPlayerUuid);

                player.sendMessage(Component.text("Click on a chest to give access to `" + targetPlayer + "`!"));

                return true;
            }
            case "create" -> {
                GringottsPrototypingPaper.VAULT_APPENDING_PLAYERS.remove(playerUuid);

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

class MoneyCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        UUID    playerUuid = player.getUniqueId();
        String  accountId  = "player_" + playerUuid;
        Account account    = GringottsPrototypingPaper.ACCOUNT_MAP.get(accountId);

        long   startTime = System.currentTimeMillis();
        double sum       = account.calculateMoney();
        long   total     = System.currentTimeMillis() - startTime;

        player.sendMessage(Component.text("sum of " + sum + " took " + total + "ms"));

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        return null;
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

        PluginCommand moneyCommandPlugin = getCommand("money");

        if (moneyCommandPlugin != null) {
            MoneyCommand moneyCommand = new MoneyCommand();

            moneyCommandPlugin.setExecutor(moneyCommand);
            moneyCommandPlugin.setTabCompleter(moneyCommand);
        } else {
            getLogger().warning("money command is occupied");
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
