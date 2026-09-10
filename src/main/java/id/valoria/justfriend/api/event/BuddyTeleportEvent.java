package id.valoria.justfriend.api.event;

import id.valoria.justfriend.model.BuddySession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class BuddyTeleportEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList(); private final BuddySession session; private final Player player; private Location destination; private boolean cancelled;
    public BuddyTeleportEvent(BuddySession session,Player player,Location destination){this.session=session;this.player=player;this.destination=destination;}
    public BuddySession getSession(){return session;} public Player getPlayer(){return player;} public Location getDestination(){return destination;} public void setDestination(Location value){destination=value;}
    @Override public boolean isCancelled(){return cancelled;} @Override public void setCancelled(boolean value){cancelled=value;}
    @Override public HandlerList getHandlers(){return HANDLERS;} public static HandlerList getHandlerList(){return HANDLERS;}
}
