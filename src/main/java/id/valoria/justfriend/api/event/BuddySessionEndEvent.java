package id.valoria.justfriend.api.event;

import id.valoria.justfriend.model.BuddySession;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class BuddySessionEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList(); private final BuddySession session; private final String reason;
    public BuddySessionEndEvent(BuddySession session,String reason){this.session=session;this.reason=reason;} public BuddySession getSession(){return session;} public String getReason(){return reason;}
    @Override public HandlerList getHandlers(){return HANDLERS;} public static HandlerList getHandlerList(){return HANDLERS;}
}

