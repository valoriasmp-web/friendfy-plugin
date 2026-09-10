package id.valoria.justfriend.service;

import id.valoria.justfriend.JustFriendPlugin;
import id.valoria.justfriend.api.event.BuddyMatchAcceptEvent;
import id.valoria.justfriend.api.event.BuddyMatchFoundEvent;
import id.valoria.justfriend.api.event.BuddyQueueJoinEvent;
import id.valoria.justfriend.api.event.BuddyQueueLeaveEvent;
import id.valoria.justfriend.model.Activity;
import id.valoria.justfriend.model.MatchOffer;
import id.valoria.justfriend.model.QueueEntry;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MatchmakingService {
    private final JustFriendPlugin plugin;
    private final Map<UUID, QueueEntry> queue = new LinkedHashMap<>();
    private final Map<UUID, MatchOffer> offers = new HashMap<>();
    private final Map<String, Long> declinedPairs = new ConcurrentHashMap<>();
    private final Set<String> previousBuddies = ConcurrentHashMap.newKeySet();
    private BukkitTask task;

    public MatchmakingService(JustFriendPlugin plugin) { this.plugin = plugin; }

    public void start() {
        long interval = Math.max(20L, plugin.getConfig().getLong("matchmaking.interval-ticks", 100L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void stop() { if (task != null) task.cancel(); queue.clear(); offers.clear(); }

    public boolean enqueue(Player player, Activity activity) {
        if (!player.hasPermission("friendfy.match") || plugin.getSessions().hasSession(player.getUniqueId())) return false;
        if (queue.containsKey(player.getUniqueId())) { plugin.getMessages().send(player, "already-queued", Map.of()); return false; }
        BuddyQueueJoinEvent event = new BuddyQueueJoinEvent(player, activity);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        QueueEntry entry = new QueueEntry(player.getUniqueId(), activity, System.currentTimeMillis(), safePlayTicks(player), player.getWorld().getName());
        queue.put(player.getUniqueId(), entry);
        plugin.getMessages().send(player, "queued", Map.of("activity", activity.display()));
        return true;
    }

    public boolean enqueue(UUID id, Activity activity) {
        Player player = Bukkit.getPlayer(id);
        return player != null && enqueue(player, activity);
    }

    public boolean leave(Player player, boolean notify) {
        MatchOffer offer = offers.remove(player.getUniqueId());
        if (offer != null) offers.remove(offer.other(player.getUniqueId()));
        QueueEntry removed = queue.remove(player.getUniqueId());
        if (removed != null) Bukkit.getPluginManager().callEvent(new BuddyQueueLeaveEvent(player));
        if (notify) plugin.getMessages().send(player, removed == null ? "not-queued" : "queue-cancelled", Map.of());
        return removed != null;
    }

    public boolean leave(UUID id) { Player p = Bukkit.getPlayer(id); return p != null && leave(p, false); }
    public boolean isQueued(UUID id) { return queue.containsKey(id); }
    public int size() { return queue.size(); }
    public Collection<QueueEntry> entries() { return Collections.unmodifiableCollection(new ArrayList<>(queue.values())); }

    public void accept(Player player) {
        MatchOffer offer = offers.get(player.getUniqueId());
        if (offer == null || offer.expiresAt < System.currentTimeMillis()) { plugin.getMessages().send(player, "match-expired", Map.of()); return; }
        Player partner = Bukkit.getPlayer(offer.other(player.getUniqueId()));
        if (partner == null) { expire(offer); return; }
        offer.accepted.add(player.getUniqueId());
        Bukkit.getPluginManager().callEvent(new BuddyMatchAcceptEvent(player, partner));
        plugin.getMessages().send(player, "match-accepted", Map.of());
        if (!offer.complete()) return;
        offers.remove(offer.first); offers.remove(offer.second);
        QueueEntry a = queue.remove(offer.first); QueueEntry b = queue.remove(offer.second);
        if (a == null || b == null) return;
        Activity activity = a.activity == Activity.QUICK ? b.activity : a.activity;
        plugin.getSessions().beginAdventure(offer.first, offer.second, activity).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!success) {
                Player pa = Bukkit.getPlayer(offer.first), pb = Bukkit.getPlayer(offer.second);
                if (pa != null) enqueue(pa, a.activity);
                if (pb != null) enqueue(pb, b.activity);
            } else previousBuddies.add(pairKey(offer.first, offer.second));
        }));
    }

    public void decline(Player player) {
        MatchOffer offer = offers.get(player.getUniqueId());
        if (offer == null) return;
        declinedPairs.put(pairKey(offer.first, offer.second), System.currentTimeMillis());
        offers.remove(offer.first); offers.remove(offer.second);
        plugin.getMessages().send(player, "match-declined", Map.of());
        Player other = Bukkit.getPlayer(offer.other(player.getUniqueId()));
        if (other != null) plugin.getMessages().send(other, "match-expired", Map.of());
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Set<MatchOffer> uniqueOffers = new HashSet<>(offers.values());
        for (MatchOffer offer : uniqueOffers) if (offer.expiresAt <= now) expire(offer);
        queue.entrySet().removeIf(e -> {
            Player p = Bukkit.getPlayer(e.getKey());
            return p == null || !p.isOnline() || plugin.getSessions().hasSession(e.getKey());
        });
        List<QueueEntry> candidates = new ArrayList<>(queue.values());
        candidates.sort(Comparator.comparingLong(e -> e.joinedAt));
        for (QueueEntry entry : candidates) {
            if (offers.containsKey(entry.playerId)) continue;
            Player first = Bukkit.getPlayer(entry.playerId);
            if (!eligible(first)) continue;
            QueueEntry best = null; int bestScore = Integer.MIN_VALUE;
            for (QueueEntry other : candidates) {
                if (entry == other || offers.containsKey(other.playerId)) continue;
                Player second = Bukkit.getPlayer(other.playerId);
                if (!eligible(second) || blockedEither(entry.playerId, other.playerId) || !expandedCompatibility(entry, other, now)) continue;
                int score = score(entry, other, first, second, now);
                if (score > bestScore) { bestScore = score; best = other; }
            }
            if (best != null) createOffer(entry, best, bestScore);
        }
    }

    private boolean eligible(Player p) {
        return p != null && p.isOnline() && !plugin.settings(p.getUniqueId()).dnd && plugin.settings(p.getUniqueId()).requests;
    }

    private boolean expandedCompatibility(QueueEntry a, QueueEntry b, long now) {
        boolean an = isNewbie(a), bn = isNewbie(b);
        if (!an && !bn) return true;
        long waited = Math.max(now - a.joinedAt, now - b.joinedAt) / 1000L;
        long low = plugin.getConfig().getLong("matchmaking.expansion-low-progress-seconds", 30);
        long volunteer = plugin.getConfig().getLong("matchmaking.expansion-volunteer-seconds", 60);
        if (waited < low) return an && bn;
        if (waited < volunteer) return an || bn || Math.max(a.playTicks, b.playTicks) < newbieTicks() * 2L;
        Player pa = Bukkit.getPlayer(a.playerId), pb = Bukkit.getPlayer(b.playerId);
        return an && bn || (pa != null && plugin.settings(pa.getUniqueId()).volunteer) || (pb != null && plugin.settings(pb.getUniqueId()).volunteer) || a.activity == b.activity;
    }

    private int score(QueueEntry a, QueueEntry b, Player pa, Player pb, long now) {
        int score = 0;
        if (a.activity == b.activity || a.activity == Activity.QUICK || b.activity == Activity.QUICK) score += points("same-activity", 40);
        if (Math.abs(a.playTicks - b.playTicks) <= newbieTicks()) score += points("similar-progress", 20);
        if (a.world.equals(b.world)) score += points("same-world", 15);
        if (isNewbie(a) && isNewbie(b)) score += points("both-newbie", 15);
        if (pa.getWorld().equals(pb.getWorld()) && pa.getLocation().distanceSquared(pb.getLocation()) <= 2500) score += points("near-distance", 10);
        String pair = pairKey(a.playerId, b.playerId);
        if (!previousBuddies.contains(pair)) score += points("never-matched", 10); else score += points("previous-buddy", -15);
        if (now - declinedPairs.getOrDefault(pair, 0L) < 86400000L) score += points("previous-decline", -40);
        long expansion = plugin.getConfig().getLong("matchmaking.expansion-volunteer-seconds", 60) * 1000L;
        if (now - Math.min(a.joinedAt, b.joinedAt) >= expansion && (plugin.settings(a.playerId).volunteer || plugin.settings(b.playerId).volunteer)) score += points("volunteer-after-expansion", 25);
        return score;
    }

    private int points(String path, int fallback) { return plugin.getMatchConfig().getInt("scores." + path, fallback); }

    private void createOffer(QueueEntry a, QueueEntry b, int score) {
        Player pa = Bukkit.getPlayer(a.playerId), pb = Bukkit.getPlayer(b.playerId);
        if (pa == null || pb == null) return;
        BuddyMatchFoundEvent event = new BuddyMatchFoundEvent(pa, pb, score);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        long expiry = System.currentTimeMillis() + plugin.getConfig().getLong("matchmaking.offer-timeout-seconds", 30) * 1000L;
        MatchOffer offer = new MatchOffer(a.playerId, b.playerId, expiry);
        offers.put(a.playerId, offer); offers.put(b.playerId, offer);
        notifyOffer(pa, pb); notifyOffer(pb, pa);
    }

    private void notifyOffer(Player receiver, Player partner) {
        if (plugin.getIntegrations().isBedrock(receiver.getUniqueId())) {
            plugin.getGui().openMatch(receiver, partner);
            receiver.sendTitle("§d§lMATCH FOUND", "§f" + partner.getName(), 5, 50, 10);
            return;
        }
        receiver.sendTitle(plugin.getMessages().get("match-found", Map.of("player", partner.getName())).split("\\n")[0], partner.getName(), 10, 50, 10);
        TextComponent accept = new TextComponent("[ACCEPT]"); accept.setColor(net.md_5.bungee.api.ChatColor.GREEN); accept.setBold(true); accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fr accept"));
        TextComponent space = new TextComponent("   ");
        TextComponent decline = new TextComponent("[DECLINE]"); decline.setColor(net.md_5.bungee.api.ChatColor.RED); decline.setBold(true); decline.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fr decline"));
        receiver.spigot().sendMessage(accept, space, decline);
    }

    private void expire(MatchOffer offer) {
        offers.remove(offer.first); offers.remove(offer.second);
        for (UUID id : List.of(offer.first, offer.second)) { Player p = Bukkit.getPlayer(id); if (p != null) plugin.getMessages().send(p, "match-expired", Map.of()); }
    }

    private boolean blockedEither(UUID a, UUID b) { return plugin.blocked(a).contains(b) || plugin.blocked(b).contains(a); }
    private boolean isNewbie(QueueEntry e) { return e.playTicks <= newbieTicks(); }
    private long newbieTicks() { return plugin.getConfig().getLong("matchmaking.newbie-playtime-hours", 5) * 60L * 60L * 20L; }
    private long safePlayTicks(Player p) { try { return p.getStatistic(Statistic.PLAY_ONE_MINUTE); } catch (RuntimeException e) { return 0L; } }
    private String pairKey(UUID a, UUID b) { return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a; }
}
