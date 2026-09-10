package id.valoria.justfriend.gui;

import id.valoria.justfriend.JustFriendPlugin;
import id.valoria.justfriend.model.Activity;
import id.valoria.justfriend.model.PlayerSettings;
import id.valoria.justfriend.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class GuiManager {
    private final JustFriendPlugin plugin;
    public GuiManager(JustFriendPlugin plugin) { this.plugin = plugin; }

    public void openMain(Player player) {
        YamlConfiguration cfg = plugin.getGuiConfig();
        Inventory inv = Bukkit.createInventory(null, cfg.getInt("main.size", 27), Text.color(cfg.getString("main.title", "FRIENDFY - CARI TEMAN")));
        fill(inv);
        inv.setItem(cfg.getInt("main.quick-match-slot", 11), item(Material.COMPASS, "&d&lQuick Match", "&7Cari teman terbaik secara otomatis.", "&eKlik untuk mulai"));
        inv.setItem(cfg.getInt("main.activity-slot", 13), item(Material.MAP, "&b&lPilih Aktivitas", "&7Mining, building, exploring,", "&7dungeon, santai, dan lainnya."));
        inv.setItem(cfg.getInt("main.settings-slot", 15), item(Material.COMPARATOR, "&e&lPengaturan", "&7Atur privasi dan tracker."));
        boolean volunteer = plugin.settings(player.getUniqueId()).volunteer;
        inv.setItem(cfg.getInt("main.volunteer-slot", 17), item(Material.TOTEM_OF_UNDYING, "&6&lVolunteer Buddy", volunteer ? "&aAKTIF" : "&cNONAKTIF", "&7Temani pemain baru yang menunggu."));
        boolean queued = plugin.getMatchmaking().isQueued(player.getUniqueId());
        inv.setItem(cfg.getInt("main.status-slot", 22), item(queued ? Material.REDSTONE_BLOCK : Material.CLOCK, queued ? "&c&lBatalkan Pencarian" : "&a&lStatus", queued ? "&7Klik untuk keluar dari antrean." : "&7Kamu belum berada dalam antrean."));
        player.openInventory(inv);
    }

    public void openActivities(Player player) {
        List<String> values = plugin.getGuiConfig().getStringList("activities.values");
        int size = Math.max(9, Math.min(54, ((values.size() + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(null, size, Text.color(plugin.getGuiConfig().getString("activities.title", "PILIH AKTIVITAS")));
        Material[] icons = {Material.GRASS_BLOCK, Material.DIAMOND_PICKAXE, Material.BRICKS, Material.SPYGLASS, Material.WHEAT, Material.FISHING_ROD, Material.IRON_SWORD, Material.BOW, Material.CAMPFIRE, Material.GOLDEN_APPLE};
        for (int i=0; i<values.size() && i<size; i++) {
            Activity activity = Activity.parse(values.get(i));
            inv.setItem(i, item(icons[i % icons.length], "&d&l" + activity.display(), "&7Klik untuk mencari buddy."));
        }
        player.openInventory(inv);
    }

    public void openSettings(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Text.color(plugin.getGuiConfig().getString("settings.title", "FRIENDFY - SETTINGS")));
        fill(inv); PlayerSettings s = plugin.settings(player.getUniqueId());
        inv.setItem(10, toggle(Material.BELL, "Never Alone", s.neverAlone));
        inv.setItem(11, toggle(Material.PLAYER_HEAD, "Buddy Requests", s.requests));
        inv.setItem(12, toggle(Material.TOTEM_OF_UNDYING, "Volunteer Buddy", s.volunteer));
        inv.setItem(13, toggle(Material.COMPASS, "ActionBar Tracker", s.tracker));
        inv.setItem(14, toggle(Material.RECOVERY_COMPASS, "Buddy Compass", s.compass));
        inv.setItem(15, toggle(Material.ENDER_PEARL, "Buddy TP", s.buddyTp));
        inv.setItem(16, toggle(Material.NOTE_BLOCK, "Sound", s.sound));
        inv.setItem(22, toggle(Material.BARRIER, "DND", s.dnd));
        player.openInventory(inv);
    }

    public void openAdmin(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "FRIENDFY - ADMIN"); fill(inv);
        inv.setItem(11, item(Material.HOPPER, "&dQueue Monitor", "&7Menunggu: &f" + plugin.getMatchmaking().size()));
        inv.setItem(13, item(Material.HEART_OF_THE_SEA, "&dActive Sessions", "&7Aktif: &f" + plugin.getSessions().count()));
        inv.setItem(15, item(Material.REDSTONE_TORCH, "&eIntegration Status", plugin.getIntegrations().status().entrySet().stream().map(e -> (e.getValue() ? "&a" : "&c") + e.getKey()).toArray(String[]::new)));
        player.openInventory(inv);
    }

    public void openMatch(Player player, Player partner) {
        String title = Text.color(plugin.getGuiConfig().getString("match.title", "FRIENDFY - MATCH FOUND"));
        Inventory inv = Bukkit.createInventory(null, 27, title); fill(inv);
        inv.setItem(plugin.getGuiConfig().getInt("match.accept-slot",11), item(Material.LIME_WOOL,"&a&lACCEPT","&7Mulai petualangan bersama.","&eKlik untuk menerima"));
        inv.setItem(plugin.getGuiConfig().getInt("match.player-slot",13), item(Material.PLAYER_HEAD,"&d&l"+partner.getName(),"&7Kalian sama-sama mencari teman.","&7Match harus diterima keduanya."));
        inv.setItem(plugin.getGuiConfig().getInt("match.decline-slot",15), item(Material.RED_WOOL,"&c&lDECLINE","&7Lewati match ini.","&7Kamu tetap berada dalam antrean."));
        player.openInventory(inv);
    }

    public void handle(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return; Player p = (Player) event.getWhoClicked(); String title = event.getView().getTitle();
        if (plugin.getSessions().isBuddyCompass(event.getCurrentItem()) || plugin.getSessions().isBuddyCompass(event.getCursor())) { event.setCancelled(true); return; }
        if (title.equals(Text.color(plugin.getGuiConfig().getString("main.title", "FRIENDFY - CARI TEMAN")))) {
            event.setCancelled(true); int slot = event.getRawSlot();
            if (slot == plugin.getGuiConfig().getInt("main.quick-match-slot",11)) { p.closeInventory(); plugin.getMatchmaking().enqueue(p, Activity.QUICK); }
            else if (slot == plugin.getGuiConfig().getInt("main.activity-slot",13)) openActivities(p);
            else if (slot == plugin.getGuiConfig().getInt("main.settings-slot",15)) openSettings(p);
            else if (slot == plugin.getGuiConfig().getInt("main.volunteer-slot",17)) { PlayerSettings s=plugin.settings(p.getUniqueId()); s.volunteer=!s.volunteer; plugin.saveSettings(p.getUniqueId()); openMain(p); }
            else if (slot == plugin.getGuiConfig().getInt("main.status-slot",22) && plugin.getMatchmaking().isQueued(p.getUniqueId())) { p.closeInventory(); plugin.getMatchmaking().leave(p,true); }
            return;
        }
        if (title.equals(Text.color(plugin.getGuiConfig().getString("activities.title", "PILIH AKTIVITAS")))) {
            event.setCancelled(true); List<String> values=plugin.getGuiConfig().getStringList("activities.values"); int slot=event.getRawSlot();
            if (slot>=0 && slot<values.size()) { p.closeInventory(); plugin.getMatchmaking().enqueue(p,Activity.parse(values.get(slot))); } return;
        }
        if (title.equals(Text.color(plugin.getGuiConfig().getString("settings.title", "FRIENDFY - SETTINGS")))) {
            event.setCancelled(true); PlayerSettings s=plugin.settings(p.getUniqueId());
            switch(event.getRawSlot()) { case 10:s.neverAlone=!s.neverAlone;break; case 11:s.requests=!s.requests;break; case 12:s.volunteer=!s.volunteer;break; case 13:s.tracker=!s.tracker;break; case 14:s.compass=!s.compass;break; case 15:s.buddyTp=!s.buddyTp;break; case 16:s.sound=!s.sound;break; case 22:s.dnd=!s.dnd;break; default:return; }
            plugin.saveSettings(p.getUniqueId()); openSettings(p); return;
        }
        if (title.equals(Text.color(plugin.getGuiConfig().getString("match.title","FRIENDFY - MATCH FOUND")))) {
            event.setCancelled(true);
            if(event.getRawSlot()==plugin.getGuiConfig().getInt("match.accept-slot",11)){p.closeInventory();plugin.getMatchmaking().accept(p);}
            else if(event.getRawSlot()==plugin.getGuiConfig().getInt("match.decline-slot",15)){p.closeInventory();plugin.getMatchmaking().decline(p);}
            return;
        }
        if (title.equals("FRIENDFY - ADMIN")) event.setCancelled(true);
    }

    private ItemStack toggle(Material material, String name, boolean enabled) { return item(material, "&f&l"+name, enabled ? "&aAKTIF" : "&cNONAKTIF", "&7Klik untuk mengubah."); }
    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta=item.getItemMeta(); if(meta==null)return item;
        meta.setDisplayName(Text.color(name)); List<String> lines=new ArrayList<>(); for(String line:lore)lines.add(Text.color(line)); meta.setLore(lines); meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); item.setItemMeta(meta); return item;
    }
    private void fill(Inventory inv) { ItemStack pane=item(Material.GRAY_STAINED_GLASS_PANE," "); for(int i=0;i<inv.getSize();i++)inv.setItem(i,pane); }
}
