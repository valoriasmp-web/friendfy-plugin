package id.valoria.justfriend.service;

import id.valoria.justfriend.JustFriendPlugin;
import id.valoria.justfriend.api.event.BuddySessionEndEvent;
import id.valoria.justfriend.api.event.BuddySessionStartEvent;
import id.valoria.justfriend.api.event.BuddyTeleportEvent;
import id.valoria.justfriend.model.Activity;
import id.valoria.justfriend.model.BuddySession;
import id.valoria.justfriend.model.PlayerSettings;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionService {
    private final JustFriendPlugin plugin;
    private final SafeRtpService safeRtp;
    private final Map<UUID, BuddySession> byPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> tpRequests = new ConcurrentHashMap<>();
    private final Map<UUID, Long> tpCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Location> deathPoints = new ConcurrentHashMap<>();
    private final Map<UUID, GroupInvite> groupInvites = new ConcurrentHashMap<>();
    private final Set<UUID> pendingCountdown = ConcurrentHashMap.newKeySet();
    private final NamespacedKey compassKey;
    private String detectedWorldName;
    private BukkitTask tracker;

    public SessionService(JustFriendPlugin plugin, SafeRtpService safeRtp) {
        this.plugin = plugin; this.safeRtp = safeRtp; this.compassKey = new NamespacedKey(plugin, "buddy_compass");
    }

    public void start() {
        long interval = Math.max(20L, plugin.getConfig().getLong("tracking.interval-ticks", 20L));
        tracker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void restore(Collection<BuddySession> sessions) {
        long now = System.currentTimeMillis();
        for (BuddySession s : sessions) {
            if (s.endsAt <= now) { plugin.getDatabase().endSession(s, "expired-during-restart"); continue; }
            for (UUID member : s.members()) byPlayer.put(member, s);
        }
    }

    public void stop() {
        if (tracker != null) tracker.cancel();
        for (BuddySession s : new HashSet<>(byPlayer.values())) plugin.getDatabase().saveSession(s);
        byPlayer.clear(); pendingCountdown.clear(); groupInvites.clear();
    }

    public CompletableFuture<Boolean> beginAdventure(UUID firstId, UUID secondId, Activity activity) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Player first = Bukkit.getPlayer(firstId), second = Bukkit.getPlayer(secondId);
        if (!available(first, second)) { result.complete(false); return result; }
        plugin.getMessages().send(first, "rtp-searching", Map.of()); plugin.getMessages().send(second, "rtp-searching", Map.of());
        World world = resolveSurvivalWorld(first);
        if (world == null) {
            plugin.getMessages().send(first, "rtp-world-missing", Map.of("world", plugin.getConfig().getString("survival-world", "world")));
            plugin.getMessages().send(second, "rtp-world-missing", Map.of("world", plugin.getConfig().getString("survival-world", "world")));
            result.complete(false); return result;
        }
        safeRtp.findPair(world).whenComplete((pair, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null || pair == null || pair.length < 2) {
                plugin.getMessages().send(first, "rtp-failed", Map.of()); plugin.getMessages().send(second, "rtp-failed", Map.of()); result.complete(false); return;
            }
            countdownPair(first, second, pair, activity, result);
        }));
        return result;
    }

    public World resolveSurvivalWorld(Player player) {
        String configured = plugin.getConfig().getString("survival-world", "auto");
        boolean autoDetection = plugin.getConfig().getBoolean("world-detection.enabled", true);
        if (!autoDetection && configured != null && !configured.isBlank() && !configured.equalsIgnoreCase("auto")) return Bukkit.getWorld(configured);
        if (detectedWorldName != null) {
            World cached = Bukkit.getWorld(detectedWorldName);
            if (cached != null) return cached;
        }
        String waiting = plugin.getConfig().getString("waiting-area.world", "lobby");
        List<String> excluded = plugin.getConfig().getStringList("world-detection.excluded-keywords");
        List<String> preferred = plugin.getConfig().getStringList("world-detection.preferred-keywords");
        boolean requireNormal = plugin.getConfig().getBoolean("world-detection.require-normal-environment", true);
        double expectedBorder = plugin.getConfig().getDouble("world-detection.expected-worldborder-size", 30000.0);
        World best = null; int bestScore = Integer.MIN_VALUE;
        for (World world : Bukkit.getWorlds()) {
            if (requireNormal && world.getEnvironment() != World.Environment.NORMAL) continue;
            if (world.getName().equalsIgnoreCase(waiting) || plugin.getConfig().getStringList("disabled-worlds").contains(world.getName())) continue;
            String lower = world.getName().toLowerCase(Locale.ROOT);
            boolean excludedName = excluded.stream().map(s -> s.toLowerCase(Locale.ROOT)).anyMatch(lower::contains);
            if (excludedName) continue;
            int score = world.getEnvironment() == World.Environment.NORMAL ? 100 : 0;
            for (int i=0;i<preferred.size();i++) if (lower.contains(preferred.get(i).toLowerCase(Locale.ROOT))) score += 300 - i * 40;
            if (expectedBorder > 0 && Math.abs(world.getWorldBorder().getSize() - expectedBorder) <= Math.max(16.0, expectedBorder * 0.01)) score += 400;
            if (player != null && player.getWorld().equals(world) && !lower.contains("lobby")) score += 75;
            score += Math.min(50, world.getLoadedChunks().length);
            if (score > bestScore) { bestScore = score; best = world; }
        }
        if (best != null) {
            detectedWorldName = best.getName();
            plugin.getLogger().info("World Survival terdeteksi otomatis: " + detectedWorldName + " (score " + bestScore + ")");
        }
        return best;
    }

    public String getResolvedWorldName(Player player) {
        World world = resolveSurvivalWorld(player);
        return world == null ? "TIDAK DITEMUKAN" : world.getName();
    }

    public void resetWorldDetection() { detectedWorldName = null; }

    private void countdownPair(Player first, Player second, Location[] pair, Activity activity, CompletableFuture<Boolean> result) {
        int seconds = Math.max(1, plugin.getConfig().getInt("buddy.tp-countdown", 5));
        pendingCountdown.add(first.getUniqueId()); pendingCountdown.add(second.getUniqueId());
        new BukkitRunnable() {
            int left = seconds;
            @Override public void run() {
                if (!available(first, second) || !safeRtp.isSafe(pair[0]) || !safeRtp.isSafe(pair[1])) { cancel(); finish(false); return; }
                if (left > 0) {
                    showCountdown(first, second, left); showCountdown(second, first, left); left--; return;
                }
                BuddySession provisional = new BuddySession(UUID.randomUUID(), first.getUniqueId(), second.getUniqueId(), activity, System.currentTimeMillis(), System.currentTimeMillis() + plugin.getConfig().getLong("buddy.default-duration", 3600) * 1000L);
                provisional.origin = pair[0].clone();
                Location oldFirst = first.getLocation().clone(), oldSecond = second.getLocation().clone();
                boolean a = teleport(provisional, first, pair[0]); boolean b = teleport(provisional, second, pair[1]);
                if (!a || !b) {
                    if (a) first.teleport(oldFirst, PlayerTeleportEvent.TeleportCause.PLUGIN);
                    if (b) second.teleport(oldSecond, PlayerTeleportEvent.TeleportCause.PLUGIN);
                    cancel(); finish(false); return;
                }
                byPlayer.put(first.getUniqueId(), provisional); byPlayer.put(second.getUniqueId(), provisional);
                plugin.getDatabase().saveSession(provisional); giveCompass(first, provisional); giveCompass(second, provisional);
                Bukkit.getPluginManager().callEvent(new BuddySessionStartEvent(provisional));
                plugin.getMessages().send(first, "session-started", Map.of("player", second.getName())); plugin.getMessages().send(second, "session-started", Map.of("player", first.getName()));
                plugin.getMessages().send(first, "group-unlocked", Map.of()); plugin.getMessages().send(second, "group-unlocked", Map.of());
                cancel(); finish(true);
            }
            private void finish(boolean success) { pendingCountdown.remove(first.getUniqueId()); pendingCountdown.remove(second.getUniqueId()); result.complete(success); }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private boolean available(Player first, Player second) {
        if (first == null || second == null || !first.isOnline() || !second.isOnline() || first.isDead() || second.isDead()) return false;
        if (!plugin.getConfig().getBoolean("teleport.cancel-on-damage", true)) return true;
        return !plugin.getCombat().isInCombat(first.getUniqueId()) && !plugin.getCombat().isInCombat(second.getUniqueId());
    }

    private void showCountdown(Player receiver, Player partner, int seconds) {
        receiver.sendTitle(plugin.getMessages().get("countdown-title"), plugin.getMessages().get("countdown-subtitle", Map.of("player", partner.getName(), "seconds", seconds)), 0, 25, 0);
        if (plugin.settings(receiver.getUniqueId()).sound) receiver.playSound(receiver.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.2f);
    }

    private boolean teleport(BuddySession session, Player player, Location location) {
        BuddyTeleportEvent event = new BuddyTeleportEvent(session, player, location.clone());
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled() && safeRtp.isSafe(event.getDestination()) && player.teleport(event.getDestination(), PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, GroupInvite> entry : new ArrayList<>(groupInvites.entrySet())) {
            if (entry.getValue().expiresAt <= now && groupInvites.remove(entry.getKey(), entry.getValue())) {
                Player target = Bukkit.getPlayer(entry.getKey());
                if (target != null) plugin.getMessages().send(target, "group-invite-expired", Map.of());
            }
        }
        for (BuddySession s : new HashSet<>(byPlayer.values())) {
            if (!s.active) continue;
            if (now >= s.endsAt) { end(s, "duration-complete"); continue; }
            handleGrace(s, now);
            if (!s.active) continue;
            for (UUID memberId : s.members()) {
                Player viewer = Bukkit.getPlayer(memberId);
                Player target = nearestOnlineMember(s, viewer);
                if (viewer != null && viewer.isOnline() && target != null) track(viewer, target, s);
            }
        }
    }

    private void handleGrace(BuddySession s, long now) {
        long grace = plugin.getConfig().getLong("buddy.reconnect-grace", 600) * 1000L;
        for (UUID member : new ArrayList<>(s.members())) {
            long disconnectedAt = s.disconnectedAt(member);
            if (disconnectedAt <= 0 || now - disconnectedAt < grace) continue;
            if (s.size() <= 2) { end(s, "reconnect-timeout"); return; }
            removeMember(s, member, "offline-timeout", null);
        }
    }

    private void track(Player viewer, Player buddy, BuddySession session) {
        PlayerSettings settings = plugin.settings(viewer.getUniqueId());
        if (!settings.tracker || !plugin.getConfig().getBoolean("tracking.actionbar", true)) return;
        String text;
        if (!viewer.getWorld().equals(buddy.getWorld())) text = "Buddy: " + buddy.getName() + " | " + buddy.getWorld().getName();
        else {
            Location from = viewer.getLocation(), to = buddy.getLocation();
            int distance = (int) Math.round(from.distance(to)); int dy = to.getBlockY() - from.getBlockY();
            boolean simple = plugin.getConfig().getBoolean("tracking.simple-bedrock-format", true) && plugin.getIntegrations().isBedrock(viewer.getUniqueId());
            String direction = direction(from, to, simple);
            String label = session.size() > 2 ? "Grup" : "Buddy";
            text = simple ? label + ": " + buddy.getName() + " | " + distance + "m | " + direction : "§d❤ §f" + buddy.getName() + " §7• " + distance + "m • §d" + direction;
            if (Math.abs(dy) >= 5) text += simple ? " | Y " + (dy > 0 ? "+" : "") + dy : " §7" + (dy > 0 ? "↑ +" : "↓ ") + dy;
            int warning = plugin.getConfig().getInt("buddy.warning-distance", 250);
            if (distance >= warning) text = "§e⚠ §f" + buddy.getName() + " §7• " + distance + "m • §e" + direction;
        }
        viewer.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
        updateCompass(viewer, buddy, session);
    }

    private Player nearestOnlineMember(BuddySession session, Player viewer) {
        if (viewer == null) return null;
        Player best = null; double bestDistance = Double.MAX_VALUE;
        for (UUID id : session.members()) {
            if (id.equals(viewer.getUniqueId())) continue;
            Player candidate = Bukkit.getPlayer(id);
            if (candidate == null || !candidate.isOnline()) continue;
            double distance = candidate.getWorld().equals(viewer.getWorld()) ? candidate.getLocation().distanceSquared(viewer.getLocation()) : Double.MAX_VALUE - 1;
            if (best == null || distance < bestDistance) { best = candidate; bestDistance = distance; }
        }
        return best;
    }

    private String direction(Location from, Location to, boolean simple) {
        double angle = Math.toDegrees(Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX()));
        String[] simpleNames = {"East","South-East","South","South-West","West","North-West","North","North-East"};
        String[] arrows = {"→","↘","↓","↙","←","↖","↑","↗"};
        int index = (int) Math.round(((angle + 360.0) % 360.0) / 45.0) % 8;
        return simple ? simpleNames[index] : arrows[index];
    }

    public boolean requestTeleport(Player requester) {
        BuddySession s = byPlayer.get(requester.getUniqueId());
        if (s == null || !plugin.getConfig().getBoolean("buddy.tp-enabled", true) || !requester.hasPermission("friendfy.tp")) return false;
        Player target = preferredTeleportTarget(s, requester); UUID targetId = target == null ? null : target.getUniqueId();
        if (target == null || !target.isOnline() || !plugin.settings(targetId).buddyTp || plugin.blocked(targetId).contains(requester.getUniqueId())) return false;
        long cooldown = plugin.getConfig().getLong("buddy.tp-cooldown", 120) * 1000L;
        long remaining = cooldown - (System.currentTimeMillis() - tpCooldown.getOrDefault(requester.getUniqueId(), 0L));
        if (remaining > 0) { plugin.getMessages().send(requester, "tp-cooldown", Map.of("seconds", (remaining + 999) / 1000)); return false; }
        tpRequests.put(targetId, requester.getUniqueId()); tpCooldown.put(requester.getUniqueId(), System.currentTimeMillis());
        plugin.getMessages().send(requester, "tp-requested", Map.of()); plugin.getMessages().send(target, "tp-incoming", Map.of("player", requester.getName())); return true;
    }

    private Player preferredTeleportTarget(BuddySession session, Player requester) {
        if (!session.isLeader(requester.getUniqueId())) {
            Player leader = Bukkit.getPlayer(session.leader());
            if (leader != null && leader.isOnline()) return leader;
        }
        return nearestOnlineMember(session, requester);
    }

    public void answerTeleport(Player target, boolean accept) {
        UUID requesterId = tpRequests.remove(target.getUniqueId()); if (requesterId == null) return;
        Player requester = Bukkit.getPlayer(requesterId); if (requester == null) return;
        if (!accept) { plugin.getMessages().send(requester, "tp-denied", Map.of()); return; }
        BuddySession s = byPlayer.get(requesterId);
        if (s == null || s != byPlayer.get(target.getUniqueId()) || !available(requester, target)) { plugin.getMessages().send(requester, "tp-unsafe", Map.of()); return; }
        Location destination = target.getLocation().clone().add(1.5, 0, 0);
        if (!safeRtp.isSafe(destination)) destination = target.getLocation().clone();
        final Location dest = destination;
        int seconds = Math.max(1, plugin.getConfig().getInt("buddy.tp-countdown", 5));
        new BukkitRunnable() {
            int left = seconds;
            @Override public void run() {
                if (!available(requester, target) || !safeRtp.isSafe(dest)) { plugin.getMessages().send(requester, "tp-unsafe", Map.of()); cancel(); return; }
                if (left-- > 0) { requester.sendTitle("§dBUDDY TELEPORT", "§fTeleport dalam §d" + (left + 1) + " §fdetik", 0, 25, 0); return; }
                teleport(s, requester, dest); cancel();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void onQuit(Player player) {
        BuddySession s = byPlayer.get(player.getUniqueId()); if (s == null) return;
        s.disconnectedAt(player.getUniqueId(), System.currentTimeMillis()); plugin.getDatabase().saveSession(s);
        for (UUID member : s.members()) {
            if (member.equals(player.getUniqueId())) continue;
            Player buddy = Bukkit.getPlayer(member);
            if (buddy != null) plugin.getMessages().send(buddy, "buddy-offline", Map.of("player", player.getName(), "seconds", plugin.getConfig().getLong("buddy.reconnect-grace", 600)));
        }
    }

    public void onJoin(Player player) {
        BuddySession s = byPlayer.get(player.getUniqueId()); if (s == null) return;
        s.disconnectedAt(player.getUniqueId(), 0); giveCompass(player, s);
        for (UUID member : s.members()) {
            if (member.equals(player.getUniqueId())) continue;
            Player buddy = Bukkit.getPlayer(member);
            if (buddy != null) plugin.getMessages().send(buddy, "buddy-returned", Map.of("player", player.getName()));
        }
        plugin.getDatabase().saveSession(s);
    }

    public void onDeath(Player player, Location location) { if (hasSession(player.getUniqueId())) deathPoints.put(player.getUniqueId(), location.clone()); }
    public Location deathPoint(UUID id) { return deathPoints.get(id); }
    public boolean hasSession(UUID id) { return byPlayer.containsKey(id); }
    public BuddySession getSession(UUID id) { return byPlayer.get(id); }
    public int count() { return new HashSet<>(byPlayer.values()).size(); }
    public Collection<BuddySession> sessions() { return Collections.unmodifiableSet(new HashSet<>(byPlayer.values())); }
    public boolean isPendingCountdown(UUID id) { return pendingCountdown.contains(id); }

    public void endByPlayer(UUID id, String reason) { BuddySession s = byPlayer.get(id); if (s != null) end(s, reason); }

    public void end(BuddySession s, String reason) {
        if (!s.active) return; s.active = false;
        List<UUID> members = new ArrayList<>(s.members());
        for (UUID id : members) { byPlayer.remove(id, s); removeCompass(id); groupInvites.remove(id); }
        for (UUID id : members) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                UUID other = s.other(id); OfflinePlayer op = Bukkit.getOfflinePlayer(other);
                plugin.getMessages().send(p, "session-ended", Map.of("player", s.size() > 2 ? "grup" : (op.getName() == null ? "Buddy" : op.getName())));
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
            }
        }
        long played = Math.max(0, System.currentTimeMillis() - s.startedAt);
        if (played >= 15 * 60 * 1000L && "duration-complete".equals(reason)) {
            for (UUID id : members) { PlayerSettings settings = plugin.settings(id); settings.socialXp += 10; plugin.getDatabase().saveSettings(id, settings); }
        }
        plugin.getDatabase().endSession(s, reason); Bukkit.getPluginManager().callEvent(new BuddySessionEndEvent(s, reason));
    }

    public int maxGroupSize() { return plugin.getConfig().getBoolean("group.enabled", true) ? Math.min(4, Math.max(2, plugin.getConfig().getInt("group.max-members", 4))) : 2; }

    public List<Player> eligibleGroupInvites(Player inviter) {
        BuddySession session = byPlayer.get(inviter.getUniqueId());
        if (!plugin.getConfig().getBoolean("group.enabled", true) || session == null || !session.active || session.size() >= maxGroupSize()) return Collections.emptyList();
        List<Player> result = new ArrayList<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            UUID id = target.getUniqueId();
            if (session.contains(id) || byPlayer.containsKey(id) || plugin.getMatchmaking().isQueued(id) || plugin.settings(id).dnd || !plugin.settings(id).requests) continue;
            if (plugin.blocked(inviter.getUniqueId()).contains(id) || plugin.blocked(id).contains(inviter.getUniqueId())) continue;
            result.add(target);
        }
        result.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public boolean inviteToGroup(Player inviter, Player target) {
        if (!inviter.hasPermission("friendfy.group")) { plugin.getMessages().send(inviter, "no-permission", Map.of()); return false; }
        BuddySession session = byPlayer.get(inviter.getUniqueId());
        if (session == null || !session.active) { plugin.getMessages().send(inviter, "group-locked", Map.of()); return false; }
        if (session.size() >= maxGroupSize()) { plugin.getMessages().send(inviter, "group-full", Map.of()); return false; }
        if (target == null || !target.isOnline() || !eligibleGroupInvites(inviter).contains(target)) { plugin.getMessages().send(inviter, "group-target-unavailable", Map.of()); return false; }
        long expires = System.currentTimeMillis() + plugin.getConfig().getLong("group.invite-timeout-seconds", 30L) * 1000L;
        groupInvites.put(target.getUniqueId(), new GroupInvite(session.id, inviter.getUniqueId(), expires));
        plugin.getMessages().send(inviter, "group-invite-sent", Map.of("player", target.getName()));
        plugin.getMessages().send(target, "group-invite-received", Map.of("player", inviter.getName()));
        plugin.getGui().openGroupInvite(target, inviter, session);
        return true;
    }

    public void answerGroupInvite(Player target, boolean accept) {
        GroupInvite invite = groupInvites.remove(target.getUniqueId());
        if (invite == null || invite.expiresAt <= System.currentTimeMillis()) { plugin.getMessages().send(target, "group-invite-expired", Map.of()); return; }
        BuddySession session = findSession(invite.sessionId); Player inviter = Bukkit.getPlayer(invite.inviterId);
        if (!accept) {
            plugin.getMessages().send(target, "group-invite-declined", Map.of());
            if (inviter != null) plugin.getMessages().send(inviter, "group-invite-declined-by", Map.of("player", target.getName()));
            return;
        }
        if (session == null || !session.active || session.size() >= maxGroupSize() || byPlayer.containsKey(target.getUniqueId())) {
            plugin.getMessages().send(target, "group-target-unavailable", Map.of()); return;
        }
        plugin.getMatchmaking().leave(target, false);
        if (!session.addMember(target.getUniqueId())) { plugin.getMessages().send(target, "group-full", Map.of()); return; }
        byPlayer.put(target.getUniqueId(), session); session.disconnectedAt(target.getUniqueId(), 0L);
        plugin.getDatabase().saveSession(session); giveCompass(target, session);
        for (UUID member : session.members()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null) plugin.getMessages().send(online, "group-member-joined", Map.of("player", target.getName(), "count", session.size(), "max", maxGroupSize()));
        }
        Player anchor = inviter != null && session.contains(inviter.getUniqueId()) ? inviter : Bukkit.getPlayer(session.leader());
        if (anchor != null && plugin.getConfig().getBoolean("group.teleport-new-member", true)) teleportJoinedMember(target, anchor, session);
    }

    public void leaveGroup(Player player) {
        BuddySession session = byPlayer.get(player.getUniqueId());
        if (session == null) { plugin.getMessages().send(player, "group-locked", Map.of()); return; }
        if (session.size() <= 2) { end(session, "player-ended"); return; }
        removeMember(session, player.getUniqueId(), "left", player);
    }

    public boolean removeGroupMember(Player actor, UUID targetId) {
        BuddySession session = byPlayer.get(actor.getUniqueId());
        if (session == null || !session.isLeader(actor.getUniqueId())) { plugin.getMessages().send(actor, "group-leader-only", Map.of()); return false; }
        if (targetId == null || targetId.equals(actor.getUniqueId()) || !session.contains(targetId) || session.size() <= 2) return false;
        removeMember(session, targetId, "kicked", actor); return true;
    }

    private void removeMember(BuddySession session, UUID memberId, String reason, Player actor) {
        String name = Optional.ofNullable(Bukkit.getOfflinePlayer(memberId).getName()).orElse("Player");
        UUID oldLeader = session.leader();
        if (!session.removeMember(memberId)) return;
        byPlayer.remove(memberId, session); removeCompass(memberId); groupInvites.remove(memberId);
        Player removed = Bukkit.getPlayer(memberId);
        if (removed != null) {
            plugin.getMessages().send(removed, "kicked".equals(reason) ? "group-kicked" : "group-left", Map.of());
            removed.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        }
        for (UUID id : session.members()) {
            Player online = Bukkit.getPlayer(id);
            if (online != null) plugin.getMessages().send(online, "group-member-left", Map.of("player", name, "count", session.size(), "max", maxGroupSize()));
        }
        if (memberId.equals(oldLeader) && session.leader() != null) {
            String leaderName = Optional.ofNullable(Bukkit.getOfflinePlayer(session.leader()).getName()).orElse("Player");
            for (UUID id : session.members()) { Player online = Bukkit.getPlayer(id); if (online != null) plugin.getMessages().send(online, "group-new-leader", Map.of("player", leaderName)); }
        }
        plugin.getDatabase().saveSession(session);
    }

    private BuddySession findSession(UUID sessionId) {
        for (BuddySession session : new HashSet<>(byPlayer.values())) if (session.id.equals(sessionId)) return session;
        return null;
    }

    private void teleportJoinedMember(Player target, Player anchor, BuddySession session) {
        Location destination = safeNear(anchor.getLocation());
        if (destination == null) { plugin.getMessages().send(target, "tp-unsafe", Map.of()); return; }
        int seconds = Math.max(1, plugin.getConfig().getInt("buddy.tp-countdown", 5));
        new BukkitRunnable() {
            int left = seconds;
            @Override public void run() {
                if (!session.active || !session.contains(target.getUniqueId()) || !available(target, anchor) || !safeRtp.isSafe(destination)) {
                    plugin.getMessages().send(target, "tp-unsafe", Map.of()); cancel(); return;
                }
                if (left-- > 0) { target.sendTitle("§d§lBERGABUNG KE GRUP", "§fTeleport dalam §d" + (left + 1) + " §fdetik", 0, 25, 0); return; }
                teleport(session, target, destination); cancel();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private Location safeNear(Location origin) {
        for (int radius = 2; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    for (int dy = -2; dy <= 2; dy++) {
                        Location candidate = origin.clone().add(dx, dy, dz);
                        if (safeRtp.isSafe(candidate)) return candidate;
                    }
                }
            }
        }
        return safeRtp.isSafe(origin) ? origin.clone() : null;
    }

    private void giveCompass(Player player, BuddySession s) {
        if (!plugin.getConfig().getBoolean("buddy.compass", true) || !plugin.settings(player.getUniqueId()).compass || hasCompass(player)) return;
        ItemStack item = new ItemStack(Material.COMPASS); ItemMeta meta = item.getItemMeta(); if (meta == null) return;
        meta.setDisplayName("§d§lBuddy Compass"); meta.setLore(List.of("§7Menunjuk ke anggota terdekat.", "§8Item sementara - tidak dapat dipindahkan."));
        meta.getPersistentDataContainer().set(compassKey, PersistentDataType.STRING, s.id.toString()); meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); item.setItemMeta(meta);
        Map<Integer, ItemStack> left = player.getInventory().addItem(item); if (!left.isEmpty()) plugin.getMessages().send(player, "compass-full", Map.of());
    }

    private void updateCompass(Player owner, Player buddy, BuddySession session) {
        if (!owner.getWorld().equals(buddy.getWorld())) return;
        for (ItemStack item : owner.getInventory().getContents()) {
            if (!isBuddyCompass(item)) continue;
            ItemMeta raw = item.getItemMeta(); if (raw instanceof CompassMeta) {
                CompassMeta meta = (CompassMeta) raw; meta.setLodestone(buddy.getLocation()); meta.setLodestoneTracked(false); item.setItemMeta(meta);
            } else owner.setCompassTarget(buddy.getLocation());
        }
    }

    public boolean isBuddyCompass(ItemStack item) { return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(compassKey, PersistentDataType.STRING); }
    private boolean hasCompass(Player p) { for (ItemStack i : p.getInventory().getContents()) if (isBuddyCompass(i)) return true; return false; }
    private void removeCompass(UUID id) { Player p = Bukkit.getPlayer(id); if (p == null) return; ItemStack[] items = p.getInventory().getContents(); for (int i=0;i<items.length;i++) if (isBuddyCompass(items[i])) p.getInventory().setItem(i, null); }

    private static final class GroupInvite {
        private final UUID sessionId;
        private final UUID inviterId;
        private final long expiresAt;
        private GroupInvite(UUID sessionId, UUID inviterId, long expiresAt) { this.sessionId = sessionId; this.inviterId = inviterId; this.expiresAt = expiresAt; }
    }
}
