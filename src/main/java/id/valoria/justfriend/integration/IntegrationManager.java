package id.valoria.justfriend.integration;

import id.valoria.justfriend.JustFriendPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.*;

public final class IntegrationManager {
    private final JustFriendPlugin plugin;
    private Method teamGet;
    private Method teamOnlineMembers;
    private Plugin marriagePlugin;
    private Method marriageGet;
    private Method marriagePartner;
    private Method floodgateGet;
    private Method floodgateIsPlayer;

    public IntegrationManager(JustFriendPlugin plugin) { this.plugin = plugin; }

    public void initialize() {
        hookBetterTeams(); hookMarriage(); hookFloodgate(); hookCitizens();
        for (String name : List.of("BetterTeams", "Marry2026", "BetterRTP", "floodgate", "Citizens", "PlaceholderAPI", "WorldGuard", "GriefPrevention")) {
            plugin.getLogger().info(name + " hook: " + (Bukkit.getPluginManager().isPluginEnabled(name) ? "enabled" : "not installed"));
        }
    }

    private void hookBetterTeams() {
        try {
            Class<?> team = Class.forName("com.booksaw.betterTeams.Team");
            teamGet = team.getMethod("getTeam", OfflinePlayer.class);
            teamOnlineMembers = team.getMethod("getOnlineMembers");
        } catch (ReflectiveOperationException ignored) { teamGet = null; }
    }

    private void hookMarriage() {
        marriagePlugin = Bukkit.getPluginManager().getPlugin("Marry2026");
        if (marriagePlugin == null) return;
        try {
            marriageGet = marriagePlugin.getClass().getDeclaredMethod("getMarriage", UUID.class);
            marriageGet.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException ignored) { marriageGet = null; }
    }

    private void hookFloodgate() {
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateGet = api.getMethod("getInstance");
            floodgateIsPlayer = api.getMethod("isFloodgatePlayer", UUID.class);
        } catch (ReflectiveOperationException ignored) { floodgateGet = null; }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void hookCitizens() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Citizens") || !plugin.getConfig().getBoolean("npc.enabled", true)) return;
        try {
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName("net.citizensnpcs.api.event.NPCRightClickEvent");
            Listener marker = new Listener() {};
            Bukkit.getPluginManager().registerEvent(eventClass, marker, EventPriority.NORMAL, (listener, event) -> {
                try {
                    Object npc = event.getClass().getMethod("getNPC").invoke(event);
                    int id = ((Number) npc.getClass().getMethod("getId").invoke(npc)).intValue();
                    if (id != plugin.getConfig().getInt("npc.citizens-id", -1)) return;
                    Object clicker = event.getClass().getMethod("getClicker").invoke(event);
                    if (clicker instanceof Player) plugin.getGui().openMain((Player) clicker);
                } catch (ReflectiveOperationException ignored) { }
            }, plugin, true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Citizens ditemukan, tetapi listener NPC tidak kompatibel: " + e.getMessage());
        }
    }

    public boolean hasOnlineTeamMate(Player player) {
        if (teamGet == null) return false;
        try {
            Object team = teamGet.invoke(null, player);
            if (team == null) return false;
            Object result = teamOnlineMembers.invoke(team);
            if (!(result instanceof Collection<?>)) return false;
            for (Object member : (Collection<?>) result) if (member instanceof Player && !((Player) member).getUniqueId().equals(player.getUniqueId()) && ((Player) member).isOnline()) return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return false;
    }

    public UUID getOnlineMarriagePartner(Player player) {
        if (marriagePlugin == null || marriageGet == null) return null;
        try {
            Object record = marriageGet.invoke(marriagePlugin, player.getUniqueId());
            if (record == null) return null;
            if (marriagePartner == null || marriagePartner.getDeclaringClass() != record.getClass()) {
                marriagePartner = record.getClass().getDeclaredMethod("getPartner", UUID.class);
                marriagePartner.setAccessible(true);
            }
            UUID id = (UUID) marriagePartner.invoke(record, player.getUniqueId());
            Player partner = id == null ? null : Bukkit.getPlayer(id);
            return partner != null && partner.isOnline() ? id : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    public boolean isBedrock(UUID uuid) {
        if (floodgateGet != null) {
            try { if (Boolean.TRUE.equals(floodgateIsPlayer.invoke(floodgateGet.invoke(null), uuid))) return true; }
            catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        Player player = Bukkit.getPlayer(uuid);
        String prefix = plugin.getConfig().getString("bedrock-name-prefix", ".");
        return player != null && prefix != null && !prefix.isEmpty() && player.getName().startsWith(prefix);
    }

    public boolean isLocationAllowed(Location location) {
        return !isClaimedByGriefPrevention(location) && !isDeniedByWorldGuard(location);
    }

    private boolean isClaimedByGriefPrevention(Location location) {
        Plugin gp = Bukkit.getPluginManager().getPlugin("GriefPrevention");
        if (gp == null) return false;
        try {
            Object store = gp.getClass().getField("dataStore").get(gp);
            for (Method m : store.getClass().getMethods()) {
                if (!m.getName().equals("getClaimAt") || m.getParameterCount() != 3) continue;
                Object claim = m.invoke(store, location, true, null);
                return claim != null;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return false;
    }

    private boolean isDeniedByWorldGuard(Location location) {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return false;
        try {
            Class<?> worldGuard = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object instance = worldGuard.getMethod("getInstance").invoke(null);
            Object platform = instance.getClass().getMethod("getPlatform").invoke(instance);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query = container.getClass().getMethod("createQuery").invoke(container);
            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object adapted = adapter.getMethod("adapt", Location.class).invoke(null, location);
            Method applicable = Arrays.stream(query.getClass().getMethods()).filter(m -> m.getName().equals("getApplicableRegions") && m.getParameterCount() == 1).findFirst().orElse(null);
            if (applicable == null) return false;
            Object set = applicable.invoke(query, adapted);
            Class<?> flags = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            Object entry = flags.getField("ENTRY").get(null);
            Class<?> stateFlag = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            Object array = java.lang.reflect.Array.newInstance(stateFlag, 1);
            java.lang.reflect.Array.set(array, 0, entry);
            Method queryState = Arrays.stream(set.getClass().getMethods()).filter(m -> m.getName().equals("queryState") && m.getParameterCount() == 2).findFirst().orElse(null);
            if (queryState == null) return false;
            Object state = queryState.invoke(set, null, array);
            return state != null && "DENY".equalsIgnoreCase(state.toString());
        } catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
    }

    public Map<String, Boolean> status() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        result.put("BetterTeams", teamGet != null); result.put("Marriage", marriageGet != null);
        result.put("BetterRTP (optional)", Bukkit.getPluginManager().isPluginEnabled("BetterRTP")); result.put("Floodgate", floodgateGet != null);
        result.put("Citizens", Bukkit.getPluginManager().isPluginEnabled("Citizens"));
        result.put("PlaceholderAPI", Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"));
        return result;
    }
}
