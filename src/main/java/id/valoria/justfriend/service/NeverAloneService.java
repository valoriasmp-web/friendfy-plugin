package id.valoria.justfriend.service;

import id.valoria.justfriend.JustFriendPlugin;
import id.valoria.justfriend.model.PlayerSettings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class NeverAloneService {
    private final JustFriendPlugin plugin;
    private final Map<UUID, Long> joinedAt = new ConcurrentHashMap<>();
    private BukkitTask task;
    public NeverAloneService(JustFriendPlugin plugin){this.plugin=plugin;}
    public void start(){for(Player p:Bukkit.getOnlinePlayers())joinedAt.put(p.getUniqueId(),System.currentTimeMillis());task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1200L,1200L);}
    public void stop(){if(task!=null)task.cancel();joinedAt.clear();}
    public void join(Player p){joinedAt.put(p.getUniqueId(),System.currentTimeMillis());}
    public void quit(Player p){joinedAt.remove(p.getUniqueId());}
    private void tick(){
        if(!plugin.getConfig().getBoolean("never-alone.enabled",true))return;long now=System.currentTimeMillis();
        long delay=plugin.getConfig().getLong("never-alone.notification-delay",600)*1000L,cooldown=plugin.getConfig().getLong("never-alone.notification-cooldown",3600)*1000L;
        for(Player p:Bukkit.getOnlinePlayers()){
            PlayerSettings s=plugin.settings(p.getUniqueId());
            if(!s.neverAlone||s.dnd||now-joinedAt.getOrDefault(p.getUniqueId(),now)<delay||now-s.lastNotice<cooldown)continue;
            if(plugin.getMatchmaking().isQueued(p.getUniqueId())||plugin.getSessions().hasSession(p.getUniqueId())||plugin.getCombat().isInCombat(p.getUniqueId()))continue;
            if(plugin.getConfig().getStringList("disabled-worlds").contains(p.getWorld().getName()))continue;
            if(plugin.getIntegrations().hasOnlineTeamMate(p)||plugin.getIntegrations().getOnlineMarriagePartner(p)!=null)continue;
            p.sendTitle(plugin.getMessages().get("never-alone-title"),plugin.getMessages().get("never-alone-subtitle"),10,80,20);
            s.lastNotice=now;plugin.saveSettings(p.getUniqueId());
        }
    }
}

