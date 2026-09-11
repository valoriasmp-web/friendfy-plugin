package id.valoria.justfriend.gui;

import id.valoria.justfriend.JustFriendPlugin;
import id.valoria.justfriend.model.Activity;
import id.valoria.justfriend.model.PlayerSettings;
import id.valoria.justfriend.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public final class GuiManager {
    private final JustFriendPlugin plugin;
    private final Map<UUID, Map<Integer, UUID>> groupViews = new HashMap<>();
    private final Map<UUID, Map<Integer, UUID>> candidateViews = new HashMap<>();
    private final Map<UUID, Integer> candidatePages = new HashMap<>();
    private static final int[] MEMBER_SLOTS = {10, 11, 12, 13};
    private static final int[] CANDIDATE_SLOTS = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
    public GuiManager(JustFriendPlugin plugin) { this.plugin = plugin; }

    public void openMain(Player player) {
        YamlConfiguration cfg = plugin.getGuiConfig();
        Inventory inv = Bukkit.createInventory(null, cfg.getInt("main.size", 27), Text.color(cfg.getString("main.title", "FRIENDFY - CARI TEMAN")));
        fill(inv);
        inv.setItem(cfg.getInt("main.quick-match-slot", 11), item(Material.COMPASS, "&d&lQuick Match", "&7Cari teman terbaik secara otomatis.", "&eKlik untuk mulai"));
        if (player.hasPermission("friendfy.group") && plugin.getConfig().getBoolean("group.enabled", true) && plugin.getSessions().hasSession(player.getUniqueId())) inv.setItem(cfg.getInt("main.group-slot", 10), item(Material.PLAYER_HEAD, "&d&lGrup Buddy", "&7Kelola grup maksimal 4 pemain.", "&eKlik untuk membuka"));
        inv.setItem(cfg.getInt("main.activity-slot", 13), item(Material.MAP, "&b&lPilih Aktivitas", "&7Mining, building, exploring,", "&7dungeon, santai, dan lainnya."));
        inv.setItem(cfg.getInt("main.settings-slot", 15), item(Material.COMPARATOR, "&e&lPengaturan", "&7Atur privasi dan tracker."));
        boolean volunteer = plugin.settings(player.getUniqueId()).volunteer;
        inv.setItem(cfg.getInt("main.volunteer-slot", 17), item(Material.TOTEM_OF_UNDYING, "&6&lVolunteer Buddy", volunteer ? "&aAKTIF" : "&cNONAKTIF", "&7Temani pemain baru yang menunggu."));
        boolean queued = plugin.getMatchmaking().isQueued(player.getUniqueId());
        id.valoria.justfriend.model.BuddySession session = plugin.getSessions().getSession(player.getUniqueId());
        String statusName = queued ? "&c&lBatalkan Pencarian" : session == null ? "&a&lStatus" : "&d&lBuddy Session Aktif";
        String statusLore = queued ? "&7Klik untuk keluar dari antrean." : session == null ? "&7Kamu belum berada dalam antrean." : "&7Anggota: &f" + session.size() + "/" + plugin.getSessions().maxGroupSize();
        inv.setItem(cfg.getInt("main.status-slot", 22), item(queued ? Material.REDSTONE_BLOCK : Material.CLOCK, statusName, statusLore));
        if (player.hasPermission("friendfy.guide")) inv.setItem(cfg.getInt("main.guide-slot", 23), item(Material.WRITTEN_BOOK, "&b&lPanduan Friendfy", "&7Panduan lengkap semua fitur.", "&eKlik untuk langsung membaca"));
        player.openInventory(inv);
    }

    public void openGroup(Player player) {
        id.valoria.justfriend.model.BuddySession session = plugin.getSessions().getSession(player.getUniqueId());
        if (!player.hasPermission("friendfy.group")) { plugin.getMessages().send(player, "no-permission", Map.of()); return; }
        if (!plugin.getConfig().getBoolean("group.enabled", true) || session == null) { plugin.getMessages().send(player, "group-locked", Map.of()); return; }
        Inventory inv = Bukkit.createInventory(null, 27, Text.color(plugin.getGuiConfig().getString("group.title", "FRIENDFY - GRUP"))); fill(inv);
        Map<Integer,UUID> view = new HashMap<>(); int index = 0;
        for (UUID member : session.members()) {
            if (index >= MEMBER_SLOTS.length) break;
            int slot = MEMBER_SLOTS[index++]; OfflinePlayer offline = Bukkit.getOfflinePlayer(member);
            boolean leader = session.isLeader(member); boolean online = offline.isOnline();
            List<String> lore = new ArrayList<>(); lore.add(leader ? "&6Ketua Grup" : "&7Anggota Grup"); lore.add(online ? "&aOnline" : "&cOffline");
            if (session.isLeader(player.getUniqueId()) && !member.equals(player.getUniqueId())) lore.add("&cKlik untuk mengeluarkan");
            inv.setItem(slot, playerHead(offline, (leader ? "&6&l" : "&f&l") + (offline.getName() == null ? "Player" : offline.getName()), lore)); view.put(slot, member);
        }
        groupViews.put(player.getUniqueId(), view);
        if (session.size() < plugin.getSessions().maxGroupSize()) inv.setItem(20, item(Material.LIME_DYE, "&a&lUndang Pemain", "&7Pilih pemain online untuk bergabung.", "&7Kapasitas: &f" + session.size() + "/" + plugin.getSessions().maxGroupSize()));
        else inv.setItem(20, item(Material.BARRIER, "&c&lGrup Penuh", "&7Kapasitas maksimal 4 pemain."));
        inv.setItem(22, item(Material.ARROW, "&eKembali", "&7Kembali ke menu utama."));
        inv.setItem(24, item(Material.RED_DYE, "&c&lKeluar dari Grup", session.size() <= 2 ? "&7Mengakhiri Buddy Session." : "&7Keluar tanpa membubarkan grup."));
        player.openInventory(inv);
    }

    public void openGroupCandidates(Player player, int requestedPage) {
        List<Player> candidates = plugin.getSessions().eligibleGroupInvites(player);
        int pages = Math.max(1, (candidates.size() + CANDIDATE_SLOTS.length - 1) / CANDIDATE_SLOTS.length);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inv = Bukkit.createInventory(null, 45, Text.color(plugin.getGuiConfig().getString("group.invite-title", "FRIENDFY - UNDANG"))); fill(inv);
        Map<Integer,UUID> view = new HashMap<>(); int start = page * CANDIDATE_SLOTS.length;
        for (int i=0; i<CANDIDATE_SLOTS.length && start+i<candidates.size(); i++) {
            Player target = candidates.get(start+i); int slot = CANDIDATE_SLOTS[i];
            inv.setItem(slot, playerHead(target, "&a&l" + target.getName(), List.of("&7Klik untuk mengirim undangan grup."))); view.put(slot, target.getUniqueId());
        }
        if (candidates.isEmpty()) inv.setItem(22, item(Material.BARRIER, "&cTidak Ada Kandidat", "&7Semua pemain sedang sibuk, DND,", "&7atau sudah berada dalam sesi."));
        if (page > 0) inv.setItem(39, item(Material.ARROW, "&eHalaman Sebelumnya"));
        if (page + 1 < pages) inv.setItem(41, item(Material.ARROW, "&eHalaman Berikutnya"));
        inv.setItem(36, item(Material.OAK_DOOR, "&eKembali ke Grup"));
        candidateViews.put(player.getUniqueId(), view); candidatePages.put(player.getUniqueId(), page); player.openInventory(inv);
    }

    public void openGroupInvite(Player target, Player inviter, id.valoria.justfriend.model.BuddySession session) {
        Inventory inv = Bukkit.createInventory(null, 27, Text.color(plugin.getGuiConfig().getString("group.offer-title", "FRIENDFY - UNDANGAN GRUP"))); fill(inv);
        inv.setItem(11, item(Material.LIME_WOOL, "&a&lTERIMA", "&7Gabung dengan grup " + inviter.getName() + ".", "&eKlik untuk menerima"));
        inv.setItem(13, playerHead(inviter, "&d&l" + inviter.getName(), List.of("&7Anggota: &f" + session.size() + "/" + plugin.getSessions().maxGroupSize(), "&7Setelah menerima kamu akan", "&7diteleport dekat grup.")));
        inv.setItem(15, item(Material.RED_WOOL, "&c&lTOLAK", "&7Tolak undangan grup ini.")); target.openInventory(inv);
    }

    public void openGuide(Player player) {
        if (!player.hasPermission("friendfy.guide")) { plugin.getMessages().send(player, "no-permission", Map.of()); return; }
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK); ItemMeta raw = book.getItemMeta();
        if (!(raw instanceof BookMeta)) return; BookMeta meta = (BookMeta) raw;
        meta.setTitle("Panduan Friendfy"); meta.setAuthor("Valoria SMP");
        List<String> pages = new ArrayList<>();
        pages.add("&d&lFRIENDFY\n\n&0Cari teman petualangan melalui GUI.\n\n&0Buka dengan &d/fr&0. Semua fitur pemain aktif otomatis tanpa LuckPerms.");
        pages.add("&d&lQUICK MATCH\n\n&0Klik Compass untuk masuk antrean otomatis.\n\n&0Gunakan menu Aktivitas jika ingin mencari teman untuk mining, building, dungeon, atau aktivitas lain.");
        pages.add("&d&lACCEPT MATCH\n\n&0Kedua pemain harus menerima. Bedrock mendapat GUI, Java mendapat tombol chat.\n\n&0Jika ditolak atau expired, pencarian dapat dilanjutkan.");
        pages.add("&d&lSAFE RTP\n\n&0Setelah dua pemain accept, Friendfy mencari dua titik aman berdekatan di world Survival.\n\n&0Bergerak tidak membatalkan countdown.");
        pages.add("&d&lGRUP BUDDY\n\n&0Menu Grup muncul di kiri Compass setelah sesi dimulai.\n\n&0Undang pemain sampai maksimal 4 anggota. Undangan dijawab melalui GUI.");
        pages.add("&d&lTRACKER\n\n&0ActionBar menunjukkan nama, jarak, arah, dan beda ketinggian anggota terdekat.\n\n&0Buddy Compass menunjuk ke anggota grup terdekat.");
        pages.add("&d&lBUDDY TP\n\n&0Gunakan &d/fr tp &0untuk meminta teleport ke ketua/anggota.\n\n&0Tujuan harus menerima dengan &d/fr tp accept&0.");
        pages.add("&d&lPENGATURAN\n\n&0Atur Never Alone, request, volunteer, tracker, compass, Buddy TP, suara, dan DND dari menu Settings.");
        pages.add("&d&lNEVER ALONE\n\n&0Mengingatkan pemain yang sendirian. Notifikasi tidak muncul bila anggota BetterTeams atau pasangan Marriage sedang online.");
        pages.add("&d&lPRIVASI\n\n&0DND menolak pencarian dan undangan.\n\n&d/fr block <nama> &0memblokir pemain. Buka dengan &d/fr unblock <nama>&0.");
        pages.add("&d&lCOMMAND\n\n&d/fr &0GUI\n&d/fr group &0grup\n&d/fr guide &0panduan\n&d/fr tp &0Buddy TP\n&d/fr end &0keluar sesi\n&d/fr volunteer &0mode bantuan");
        if (player.hasPermission("friendfy.admin")) pages.add("&c&lADMIN\n\n&0Gunakan &c/fr admin &0untuk status integrasi, target RTP, antrean, sesi aktif, debug, menghentikan sesi, dan reload konfigurasi.");
        meta.addPage(pages.stream().map(Text::color).toArray(String[]::new)); book.setItemMeta(meta);
        player.closeInventory(); Bukkit.getScheduler().runTask(plugin, () -> player.openBook(book));
    }

    public void cleanup(UUID player) { groupViews.remove(player); candidateViews.remove(player); candidatePages.remove(player); }

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
            else if (slot == plugin.getGuiConfig().getInt("main.group-slot",10) && plugin.getSessions().hasSession(p.getUniqueId())) openGroup(p);
            else if (slot == plugin.getGuiConfig().getInt("main.activity-slot",13)) openActivities(p);
            else if (slot == plugin.getGuiConfig().getInt("main.settings-slot",15)) openSettings(p);
            else if (slot == plugin.getGuiConfig().getInt("main.volunteer-slot",17)) { PlayerSettings s=plugin.settings(p.getUniqueId()); s.volunteer=!s.volunteer; plugin.saveSettings(p.getUniqueId()); openMain(p); }
            else if (slot == plugin.getGuiConfig().getInt("main.status-slot",22) && plugin.getMatchmaking().isQueued(p.getUniqueId())) { p.closeInventory(); plugin.getMatchmaking().leave(p,true); }
            else if (slot == plugin.getGuiConfig().getInt("main.guide-slot",23)) openGuide(p);
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
        if (title.equals(Text.color(plugin.getGuiConfig().getString("group.title", "FRIENDFY - GRUP")))) {
            event.setCancelled(true); int slot = event.getRawSlot(); id.valoria.justfriend.model.BuddySession session = plugin.getSessions().getSession(p.getUniqueId());
            if (session == null) { p.closeInventory(); return; }
            if (slot == 20 && session.size() < plugin.getSessions().maxGroupSize()) openGroupCandidates(p, 0);
            else if (slot == 22) openMain(p);
            else if (slot == 24) { p.closeInventory(); plugin.getSessions().leaveGroup(p); }
            else {
                UUID target = groupViews.getOrDefault(p.getUniqueId(), Collections.emptyMap()).get(slot);
                if (target != null && !target.equals(p.getUniqueId()) && plugin.getSessions().removeGroupMember(p, target)) openGroup(p);
            }
            return;
        }
        if (title.equals(Text.color(plugin.getGuiConfig().getString("group.invite-title", "FRIENDFY - UNDANG")))) {
            event.setCancelled(true); int slot = event.getRawSlot();
            if (slot == 36) { openGroup(p); return; }
            int page = candidatePages.getOrDefault(p.getUniqueId(), 0);
            if (slot == 39) { openGroupCandidates(p, page - 1); return; }
            if (slot == 41) { openGroupCandidates(p, page + 1); return; }
            UUID targetId = candidateViews.getOrDefault(p.getUniqueId(), Collections.emptyMap()).get(slot);
            if (targetId != null) { Player target = Bukkit.getPlayer(targetId); p.closeInventory(); plugin.getSessions().inviteToGroup(p, target); }
            return;
        }
        if (title.equals(Text.color(plugin.getGuiConfig().getString("group.offer-title", "FRIENDFY - UNDANGAN GRUP")))) {
            event.setCancelled(true);
            if (event.getRawSlot() == 11) { p.closeInventory(); plugin.getSessions().answerGroupInvite(p, true); }
            else if (event.getRawSlot() == 15) { p.closeInventory(); plugin.getSessions().answerGroupInvite(p, false); }
            return;
        }
        if (title.equals("FRIENDFY - ADMIN")) event.setCancelled(true);
    }

    private ItemStack toggle(Material material, String name, boolean enabled) { return item(material, "&f&l"+name, enabled ? "&aAKTIF" : "&cNONAKTIF", "&7Klik untuk mengubah."); }
    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta=item.getItemMeta(); if(meta==null)return item;
        meta.setDisplayName(Text.color(name)); List<String> lines=new ArrayList<>(); for(String line:lore)lines.add(Text.color(line)); meta.setLore(lines); meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); item.setItemMeta(meta); return item;
    }
    private ItemStack playerHead(OfflinePlayer owner, String name, List<String> lore) {
        ItemStack item = item(Material.PLAYER_HEAD, name, lore.toArray(String[]::new)); ItemMeta raw = item.getItemMeta();
        if (raw instanceof SkullMeta) { SkullMeta meta = (SkullMeta) raw; meta.setOwningPlayer(owner); item.setItemMeta(meta); }
        return item;
    }
    private void fill(Inventory inv) { ItemStack pane=item(Material.GRAY_STAINED_GLASS_PANE," "); for(int i=0;i<inv.getSize();i++)inv.setItem(i,pane); }
}
