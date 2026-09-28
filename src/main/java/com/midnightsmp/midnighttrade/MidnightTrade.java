package com.midnightsmp.midnighttrade;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MidnightTrade extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, UUID> pendingRequests = new HashMap<>();
    private final Map<UUID, UUID> activeTrades = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("trade").setExecutor(this);
        getCommand("tradedeny").setExecutor(this);
        getLogger().info("MidnightTrade enabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (cmd.getName().equalsIgnoreCase("trade")) {
            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "Usage: /trade <PlayerName>");
                return true;
            }

            String targetName = args[0];
            Player target = Bukkit.getPlayer(targetName);

            if (target == null || !target.isOnline()) {
                player.sendMessage(ChatColor.RED + "❌ Player not found or offline!");
                return true;
            }

            if (target.equals(player)) {
                player.sendMessage(ChatColor.RED + "❌ You cannot trade with yourself!");
                return true;
            }

            // Enforce 20-block distance limit
            double maxDist = getConfig().getDouble("max-trade-distance", 20.0);
            if (!player.getWorld().equals(target.getWorld()) || player.getLocation().distance(target.getLocation()) > maxDist) {
                player.sendMessage(ChatColor.RED + "❌ You must be within " + (int)maxDist + " blocks of " + target.getName() + " to trade!");
                return true;
            }

            // Accept trade if pending request exists
            if (pendingRequests.containsKey(player.getUniqueId()) && pendingRequests.get(player.getUniqueId()).equals(target.getUniqueId())) {
                startTradeGUI(player, target);
                pendingRequests.remove(player.getUniqueId());
                return true;
            }

            // Send new request
            pendingRequests.put(target.getUniqueId(), player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "📨 Sent trade request to " + target.getName() + " (Valid within 20 blocks)!");
            target.sendMessage(ChatColor.GOLD + "🤝 " + player.getName() + " wants to trade with you! Type " + ChatColor.GREEN + "/trade " + player.getName() + ChatColor.GOLD + " to accept.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("tradedeny")) {
            if (pendingRequests.containsKey(player.getUniqueId())) {
                UUID senderUUID = pendingRequests.remove(player.getUniqueId());
                Player senderPlayer = Bukkit.getPlayer(senderUUID);
                player.sendMessage(ChatColor.YELLOW + "Trade request denied.");
                if (senderPlayer != null) {
                    senderPlayer.sendMessage(ChatColor.RED + player.getName() + " denied your trade request.");
                }
            } else {
                player.sendMessage(ChatColor.RED + "You have no pending trade requests.");
            }
            return true;
        }

        return true;
    }

    private void startTradeGUI(Player p1, Player p2) {
        activeTrades.put(p1.getUniqueId(), p2.getUniqueId());
        activeTrades.put(p2.getUniqueId(), p1.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_GRAY + "Trade: " + p1.getName() + " ↔ " + p2.getName());

        ItemStack divider = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 4; i < 54; i += 9) {
            inv.setItem(i, divider);
        }

        p1.openInventory(inv);
        p2.openInventory(inv);

        p1.sendMessage(ChatColor.GREEN + "✅ Trade started with " + p2.getName() + "!");
        p2.sendMessage(ChatColor.GREEN + "✅ Trade started with " + p1.getName() + "!");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (activeTrades.containsKey(player.getUniqueId())) {
            String title = event.getView().getTitle();
            if (title.contains("Trade:")) {
                int slot = event.getRawSlot();
                // Protect middle divider glass
                if (slot >= 0 && slot < 54 && slot % 9 == 4) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (activeTrades.containsKey(uuid)) {
            UUID partnerUUID = activeTrades.remove(uuid);
            activeTrades.remove(partnerUUID);

            player.sendMessage(ChatColor.RED + "Trade cancelled.");
            Player partner = Bukkit.getPlayer(partnerUUID);
            if (partner != null && partner.isOnline()) {
                partner.sendMessage(ChatColor.RED + "Trade cancelled by partner.");
                partner.closeInventory();
            }
        }
    }

    private ItemStack createGuiItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
}
