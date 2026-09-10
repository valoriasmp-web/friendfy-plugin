package id.valoria.justfriend.api.event;

import id.valoria.justfriend.model.BuddySession;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class BuddySessionStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList(); private final BuddySession session;
    public BuddySessionStartEvent(BuddySession session){this.session=session;} public BuddySession getSession(){return session;}
    @Override public HandlerList getHandlers(){return HANDLERS;} public static HandlerList getHandlerList(){return HANDLERS;}
}

