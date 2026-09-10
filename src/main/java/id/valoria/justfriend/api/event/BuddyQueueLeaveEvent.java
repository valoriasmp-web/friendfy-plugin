package id.valoria.justfriend.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class BuddyQueueLeaveEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList(); private final Player player;
    public BuddyQueueLeaveEvent(Player player) { this.player = player; } public Player getPlayer() { return player; }
    @Override public HandlerList getHandlers() { return HANDLERS; } public static HandlerList getHandlerList() { return HANDLERS; }
}

