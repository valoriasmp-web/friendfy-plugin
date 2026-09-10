package id.valoria.justfriend.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class BuddyMatchAcceptEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList(); private final Player player, partner;
    public BuddyMatchAcceptEvent(Player player, Player partner){this.player=player;this.partner=partner;}
    public Player getPlayer(){return player;} public Player getPartner(){return partner;}
    @Override public HandlerList getHandlers(){return HANDLERS;} public static HandlerList getHandlerList(){return HANDLERS;}
}

