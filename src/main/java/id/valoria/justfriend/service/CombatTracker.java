package id.valoria.justfriend.service;

import id.valoria.justfriend.JustFriendPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatTracker implements Listener {
    private final JustFriendPlugin plugin;
    private final Map<UUID, Long> combat = new ConcurrentHashMap<>();
    public CombatTracker(JustFriendPlugin plugin) { this.plugin = plugin; }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) combat.put(event.getEntity().getUniqueId(), System.currentTimeMillis());
        if (event.getDamager() instanceof Player) combat.put(event.getDamager().getUniqueId(), System.currentTimeMillis());
    }

    public boolean isInCombat(UUID id) {
        long seconds = plugin.getConfig().getLong("buddy.combat-seconds", 15);
        return System.currentTimeMillis() - combat.getOrDefault(id, 0L) < seconds * 1000L;
    }
}
