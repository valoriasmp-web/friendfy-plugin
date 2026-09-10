package id.valoria.justfriend.api.event;

import id.valoria.justfriend.model.Activity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class BuddyQueueJoinEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player; private final Activity activity; private boolean cancelled;
    public BuddyQueueJoinEvent(Player player, Activity activity) { this.player = player; this.activity = activity; }
    public Player getPlayer() { return player; } public Activity getActivity() { return activity; }
    @Override public boolean isCancelled() { return cancelled; } @Override public void setCancelled(boolean value) { cancelled = value; }
    @Override public HandlerList getHandlers() { return HANDLERS; } public static HandlerList getHandlerList() { return HANDLERS; }
}

