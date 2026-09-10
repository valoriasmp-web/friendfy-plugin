package id.valoria.justfriend.integration;

import id.valoria.justfriend.JustFriendPlugin;
import id.valoria.justfriend.model.BuddySession;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class JustFriendExpansion extends PlaceholderExpansion {
    private final JustFriendPlugin plugin;
    public JustFriendExpansion(JustFriendPlugin plugin){this.plugin=plugin;}
    @Override public String getIdentifier(){return "friendfy";}
    @Override public String getAuthor(){return "ValoriaSMP";}
    @Override public String getVersion(){return plugin.getDescription().getVersion();}
    @Override public boolean persist(){return true;}
    @Override public String onPlaceholderRequest(Player player,String id){
        if(id.equals("queue")||id.equals("waiting_players"))return String.valueOf(plugin.getMatchmaking().size());
        if(id.equals("volunteers"))return String.valueOf(Bukkit.getOnlinePlayers().stream().filter(p->plugin.settings(p.getUniqueId()).volunteer).count());
        if(player==null)return "";
        BuddySession s=plugin.getSessions().getSession(player.getUniqueId());
        if(id.equals("buddy")){if(s==null)return "";Player buddy=Bukkit.getPlayer(s.other(player.getUniqueId()));return buddy==null?"Offline":buddy.getName();}
        if(id.equals("buddy_distance")){if(s==null)return "0";Player buddy=Bukkit.getPlayer(s.other(player.getUniqueId()));return buddy!=null&&buddy.getWorld().equals(player.getWorld())?String.valueOf((int)Math.round(player.getLocation().distance(buddy.getLocation()))):"-";}
        if(id.equals("status")){if(s!=null)return "BUDDY";if(plugin.getMatchmaking().isQueued(player.getUniqueId()))return "LOOKING";if(plugin.settings(player.getUniqueId()).dnd)return "DND";return "AVAILABLE";}
        if(id.equals("social_level"))return String.valueOf(plugin.settings(player.getUniqueId()).socialXp/100+1);
        return null;
    }
}
