package id.valoria.justfriend.command;

import id.valoria.justfriend.JustFriendPlugin;
import id.valoria.justfriend.model.BuddySession;
import id.valoria.justfriend.model.PlayerSettings;
import id.valoria.justfriend.model.QueueEntry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

public final class JustFriendCommand implements CommandExecutor, TabCompleter {
    private final JustFriendPlugin plugin;
    public JustFriendCommand(JustFriendPlugin plugin){this.plugin=plugin;}

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if(!(sender instanceof Player)){plugin.getMessages().send(sender,"players-only");return true;} Player p=(Player)sender;
        if(!p.hasPermission("friendfy.use")){plugin.getMessages().send(p,"no-permission");return true;}
        if(args.length==0){plugin.getGui().openMain(p);return true;}
        String sub=args[0].toLowerCase(Locale.ROOT);
        switch(sub){
            case "queue": case "match": plugin.getMatchmaking().enqueue(p,args.length>1?id.valoria.justfriend.model.Activity.parse(args[1]):id.valoria.justfriend.model.Activity.QUICK);break;
            case "cancel": plugin.getMatchmaking().leave(p,true);break;
            case "accept": plugin.getMatchmaking().accept(p);break;
            case "decline": plugin.getMatchmaking().decline(p);break;
            case "settings": if(p.hasPermission("friendfy.settings"))plugin.getGui().openSettings(p);break;
            case "volunteer": toggleVolunteer(p);break;
            case "block": block(p,args,true);break;
            case "unblock": block(p,args,false);break;
            case "tp": if(args.length>1&&args[1].equalsIgnoreCase("accept"))plugin.getSessions().answerTeleport(p,true);else if(args.length>1&&args[1].equalsIgnoreCase("deny"))plugin.getSessions().answerTeleport(p,false);else plugin.getSessions().requestTeleport(p);break;
            case "group": group(p,args);break;
            case "guide": if(p.hasPermission("friendfy.guide"))plugin.getGui().openGuide(p);else plugin.getMessages().send(p,"no-permission");break;
            case "end": plugin.getSessions().leaveGroup(p);break;
            case "later": PlayerSettings later=plugin.settings(p.getUniqueId());later.lastNotice=System.currentTimeMillis();plugin.saveSettings(p.getUniqueId());break;
            case "never": PlayerSettings never=plugin.settings(p.getUniqueId());never.neverAlone=false;plugin.saveSettings(p.getUniqueId());plugin.getMessages().send(p,"settings-saved");break;
            case "admin": admin(p,args);break;
            case "reload": if(p.hasPermission("friendfy.admin.reload")){plugin.reloadAll();plugin.getMessages().send(p,"reloaded");}else plugin.getMessages().send(p,"no-permission");break;
            default: plugin.getGui().openMain(p);
        }
        return true;
    }

    private void toggleVolunteer(Player p){if(!p.hasPermission("friendfy.volunteer")){plugin.getMessages().send(p,"no-permission");return;}PlayerSettings s=plugin.settings(p.getUniqueId());s.volunteer=!s.volunteer;plugin.saveSettings(p.getUniqueId());p.sendMessage(plugin.getMessages().get("prefix")+"Volunteer Buddy: "+(s.volunteer?"§aAKTIF":"§cNONAKTIF"));}
    private void block(Player p,String[] args,boolean value){
        if(!p.hasPermission("friendfy.block")){plugin.getMessages().send(p,"no-permission");return;}if(args.length<2){p.sendMessage("§cGunakan /fr "+(value?"block":"unblock")+" <player>");return;}
        OfflinePlayer target=Bukkit.getOfflinePlayer(args[1]);if(target.getUniqueId().equals(p.getUniqueId()))return;
        plugin.setBlocked(p.getUniqueId(),target.getUniqueId(),value);plugin.getMessages().send(p,value?"blocked":"unblocked",Map.of("player",target.getName()==null?args[1]:target.getName()));
    }

    private void group(Player p,String[] args){
        if(!p.hasPermission("friendfy.group")){plugin.getMessages().send(p,"no-permission");return;}
        if(args.length==1){plugin.getGui().openGroup(p);return;}
        switch(args[1].toLowerCase(Locale.ROOT)){
            case "invite": if(args.length<3){plugin.getGui().openGroupCandidates(p,0);break;}Player target=Bukkit.getPlayerExact(args[2]);plugin.getSessions().inviteToGroup(p,target);break;
            case "accept": plugin.getSessions().answerGroupInvite(p,true);break;
            case "decline": plugin.getSessions().answerGroupInvite(p,false);break;
            case "leave": plugin.getSessions().leaveGroup(p);break;
            case "kick": if(args.length>2){Player kicked=Bukkit.getPlayerExact(args[2]);if(kicked!=null)plugin.getSessions().removeGroupMember(p,kicked.getUniqueId());}break;
            default: plugin.getGui().openGroup(p);
        }
    }

    private void admin(Player p,String[] args){
        if(!p.hasPermission("friendfy.admin")){plugin.getMessages().send(p,"no-permission");return;}if(args.length==1){plugin.getGui().openAdmin(p);return;}
        switch(args[1].toLowerCase(Locale.ROOT)){
            case "status": p.sendMessage("§dFriendfy §fQueue: §d"+plugin.getMatchmaking().size()+" §fSessions: §d"+plugin.getSessions().count()+" §fDB: "+(plugin.getDatabase().isReady()?"§aOK":"§cOFF"));p.sendMessage("§7Target RTP otomatis: §f"+plugin.getSessions().getResolvedWorldName(p));plugin.getIntegrations().status().forEach((k,v)->p.sendMessage((v?"§a✔ ":"§c✘ ")+k));break;
            case "queue": if(!p.hasPermission("friendfy.admin.queue"))break;for(QueueEntry e:plugin.getMatchmaking().entries()){OfflinePlayer op=Bukkit.getOfflinePlayer(e.playerId);p.sendMessage("§7- §f"+(op.getName()==null?e.playerId:op.getName())+" §d"+e.activity.display());}break;
            case "sessions": if(!p.hasPermission("friendfy.admin.sessions"))break;for(BuddySession s:plugin.getSessions().sessions())p.sendMessage("§7- §f"+String.join(" §d+ §f",s.members().stream().map(this::name).toList())+" §7("+s.activity.display()+")");break;
            case "end": if(p.hasPermission("friendfy.admin.end")&&args.length>2){Player target=Bukkit.getPlayerExact(args[2]);if(target!=null)plugin.getSessions().endByPlayer(target.getUniqueId(),"admin-ended");}break;
            case "debug": if(p.hasPermission("friendfy.admin.debug")&&args.length>2){Player target=Bukkit.getPlayerExact(args[2]);if(target!=null)debug(p,target);}break;
            case "reload": if(p.hasPermission("friendfy.admin.reload")){plugin.reloadAll();plugin.getMessages().send(p,"reloaded");}break;
            default: plugin.getGui().openAdmin(p);
        }
    }
    private void debug(Player admin,Player target){admin.sendMessage("§dFriendfy debug: §f"+target.getName());admin.sendMessage("§7Queued: §f"+plugin.getMatchmaking().isQueued(target.getUniqueId()));admin.sendMessage("§7Session: §f"+plugin.getSessions().hasSession(target.getUniqueId()));admin.sendMessage("§7DND: §f"+plugin.settings(target.getUniqueId()).dnd);admin.sendMessage("§7BetterTeams member online: §f"+plugin.getIntegrations().hasOnlineTeamMate(target));admin.sendMessage("§7Marriage partner online: §f"+(plugin.getIntegrations().getOnlineMarriagePartner(target)!=null));admin.sendMessage("§7Combat: §f"+plugin.getCombat().isInCombat(target.getUniqueId()));}
    private String name(UUID id){String n=Bukkit.getOfflinePlayer(id).getName();return n==null?id.toString():n;}

    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length==1)return partial(args[0],List.of("queue","cancel","accept","decline","settings","volunteer","block","unblock","tp","group","guide","end","admin"));
        if(args.length==2&&args[0].equalsIgnoreCase("queue"))return partial(args[1],Arrays.stream(id.valoria.justfriend.model.Activity.values()).map(Enum::name).toList());
        if(args.length==2&&args[0].equalsIgnoreCase("tp"))return partial(args[1],List.of("accept","deny"));
        if(args.length==2&&args[0].equalsIgnoreCase("group"))return partial(args[1],List.of("invite","accept","decline","leave","kick"));
        if(args.length==3&&args[0].equalsIgnoreCase("group")&&(args[1].equalsIgnoreCase("invite")||args[1].equalsIgnoreCase("kick")))return partial(args[2],Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        if(args.length==2&&args[0].equalsIgnoreCase("admin"))return partial(args[1],List.of("status","queue","sessions","end","debug","reload"));
        return Collections.emptyList();
    }
    private List<String> partial(String value,List<String> values){String q=value.toLowerCase(Locale.ROOT);return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(q)).toList();}
}
