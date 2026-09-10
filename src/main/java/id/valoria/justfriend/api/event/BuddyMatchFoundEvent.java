package id.valoria.justfriend.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class BuddyMatchFoundEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList(); private final Player first, second; private final int score; private boolean cancelled;
    public BuddyMatchFoundEvent(Player first, Player second, int score) { this.first=first; this.second=second; this.score=score; }
    public Player getFirst(){return first;} public Player getSecond(){return second;} public int getScore(){return score;}
    @Override public boolean isCancelled(){return cancelled;} @Override public void setCancelled(boolean value){cancelled=value;}
    @Override public HandlerList getHandlers(){return HANDLERS;} public static HandlerList getHandlerList(){return HANDLERS;}
}

