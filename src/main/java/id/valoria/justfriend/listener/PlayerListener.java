package id.valoria.justfriend.listener;

import id.valoria.justfriend.JustFriendPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {
    private final JustFriendPlugin plugin;
    public PlayerListener(JustFriendPlugin plugin){this.plugin=plugin;}
    @EventHandler public void onJoin(PlayerJoinEvent e){plugin.loadPlayer(e.getPlayer());plugin.getNeverAlone().join(e.getPlayer());plugin.getServer().getScheduler().runTaskLater(plugin,()->plugin.getSessions().onJoin(e.getPlayer()),20L);}
    @EventHandler public void onQuit(PlayerQuitEvent e){plugin.getMatchmaking().leave(e.getPlayer(),false);plugin.getSessions().onQuit(e.getPlayer());plugin.getNeverAlone().quit(e.getPlayer());plugin.getGui().cleanup(e.getPlayer().getUniqueId());}
    @EventHandler public void onDeath(PlayerDeathEvent e){plugin.getSessions().onDeath(e.getEntity(),e.getEntity().getLocation());e.getDrops().removeIf(plugin.getSessions()::isBuddyCompass);}
    @EventHandler(priority=EventPriority.HIGHEST) public void onDrop(PlayerDropItemEvent e){if(plugin.getSessions().isBuddyCompass(e.getItemDrop().getItemStack()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void onClick(InventoryClickEvent e){plugin.getGui().handle(e);}
    @EventHandler(priority=EventPriority.HIGHEST) public void onDrag(InventoryDragEvent e){if(plugin.getSessions().isBuddyCompass(e.getOldCursor()))e.setCancelled(true);}
}
