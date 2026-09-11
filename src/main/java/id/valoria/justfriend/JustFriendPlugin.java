package id.valoria.justfriend;

import id.valoria.justfriend.api.JustFriendAPI;
import id.valoria.justfriend.command.JustFriendCommand;
import id.valoria.justfriend.gui.GuiManager;
import id.valoria.justfriend.integration.IntegrationManager;
import id.valoria.justfriend.integration.JustFriendExpansion;
import id.valoria.justfriend.listener.PlayerListener;
import id.valoria.justfriend.model.Activity;
import id.valoria.justfriend.model.BuddySession;
import id.valoria.justfriend.model.PlayerSettings;
import id.valoria.justfriend.service.*;
import id.valoria.justfriend.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class JustFriendPlugin extends JavaPlugin implements JustFriendAPI {
    private static JustFriendPlugin instance;
    private final Map<UUID,PlayerSettings> settings=new ConcurrentHashMap<>();
    private final Map<UUID,Set<UUID>> blocks=new ConcurrentHashMap<>();
    private Messages messages; private YamlConfiguration guiConfig,matchConfig; private Database database;
    private IntegrationManager integrations; private CombatTracker combat; private SafeRtpService safeRtp; private SessionService sessions; private MatchmakingService matchmaking; private NeverAloneService neverAlone; private GuiManager gui;

    @Override public void onEnable(){
        instance=this;saveDefaultConfig();getConfig().options().copyDefaults(true);saveConfig();for(String file:List.of("messages.yml","gui.yml","matchmaking.yml","storage.yml")){if(!new File(getDataFolder(),file).exists())saveResource(file,false);mergeResourceDefaults(file);}loadExtraConfigs();
        messages=new Messages(getDataFolder());database=new Database(this);integrations=new IntegrationManager(this);combat=new CombatTracker(this);safeRtp=new SafeRtpService(this);sessions=new SessionService(this,safeRtp);matchmaking=new MatchmakingService(this);neverAlone=new NeverAloneService(this);gui=new GuiManager(this);
        getServer().getPluginManager().registerEvents(combat,this);getServer().getPluginManager().registerEvents(new PlayerListener(this),this);
        PluginCommand command=getCommand("friendfy");if(command==null){getLogger().severe("Command friendfy tidak terdaftar.");getServer().getPluginManager().disablePlugin(this);return;}JustFriendCommand handler=new JustFriendCommand(this);command.setExecutor(handler);command.setTabCompleter(handler);
        integrations.initialize();sessions.start();matchmaking.start();neverAlone.start();
        if(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")){try{new JustFriendExpansion(this).register();}catch(LinkageError|RuntimeException e){getLogger().warning("PlaceholderAPI hook gagal: "+e.getMessage());}}
        database.initialize().thenCompose(v->database.loadActiveSessions()).thenAccept(loaded->Bukkit.getScheduler().runTask(this,()->{sessions.restore(loaded);for(Player p:Bukkit.getOnlinePlayers())loadPlayer(p);}));
        getServer().getServicesManager().register(JustFriendAPI.class,this,this,org.bukkit.plugin.ServicePriority.Normal);
        getLogger().info("Friendfy aktif | /fr | movement tidak membatalkan RTP countdown");
    }

    @Override public void onDisable(){if(matchmaking!=null)matchmaking.stop();if(neverAlone!=null)neverAlone.stop();if(sessions!=null)sessions.stop();for(Map.Entry<UUID,PlayerSettings> e:settings.entrySet())if(database!=null)database.saveSettings(e.getKey(),e.getValue());if(database!=null)database.close();getServer().getServicesManager().unregisterAll(this);instance=null;}
    private void mergeResourceDefaults(String name){
        File target=new File(getDataFolder(),name);InputStream stream=getResource(name);if(stream==null)return;
        try(InputStreamReader reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){
            YamlConfiguration current=YamlConfiguration.loadConfiguration(target),defaults=YamlConfiguration.loadConfiguration(reader);boolean changed=false;
            for(String key:defaults.getKeys(true))if(!defaults.isConfigurationSection(key)&&!current.contains(key)){current.set(key,defaults.get(key));changed=true;}
            if(changed)current.save(target);
        }catch(IOException e){getLogger().warning("Gagal memperbarui default "+name+": "+e.getMessage());}
    }
    private void loadExtraConfigs(){
        File guiFile=new File(getDataFolder(),"gui.yml");guiConfig=YamlConfiguration.loadConfiguration(guiFile);
        if(guiConfig.getInt("main.group-slot",9)==10){guiConfig.set("main.group-slot",9);try{guiConfig.save(guiFile);}catch(IOException e){getLogger().warning("Gagal memigrasikan slot Grup: "+e.getMessage());}}
        matchConfig=YamlConfiguration.loadConfiguration(new File(getDataFolder(),"matchmaking.yml"));
    }
    public void reloadAll(){reloadConfig();loadExtraConfigs();messages.reload();safeRtp.reload();sessions.resetWorldDetection();}
    public void loadPlayer(Player player){UUID id=player.getUniqueId();settings.putIfAbsent(id,new PlayerSettings());blocks.putIfAbsent(id,ConcurrentHashMap.newKeySet());database.loadSettings(id).thenAccept(s->Bukkit.getScheduler().runTask(this,()->settings.put(id,s)));database.loadBlocks(id).thenAccept(b->Bukkit.getScheduler().runTask(this,()->{Set<UUID> set=ConcurrentHashMap.newKeySet();set.addAll(b);blocks.put(id,set);}));}
    public PlayerSettings settings(UUID id){return settings.computeIfAbsent(id,k->new PlayerSettings());}
    public Set<UUID> blocked(UUID id){return blocks.computeIfAbsent(id,k->ConcurrentHashMap.newKeySet());}
    public void setBlocked(UUID owner,UUID target,boolean value){if(value)blocked(owner).add(target);else blocked(owner).remove(target);database.setBlocked(owner,target,value);}
    public void saveSettings(UUID id){database.saveSettings(id,settings(id));}

    public static JustFriendPlugin getInstance(){return instance;} public static JustFriendAPI api(){return instance;}
    public Messages getMessages(){return messages;} public YamlConfiguration getGuiConfig(){return guiConfig;} public YamlConfiguration getMatchConfig(){return matchConfig;} public Database getDatabase(){return database;} public IntegrationManager getIntegrations(){return integrations;} public CombatTracker getCombat(){return combat;} public SessionService getSessions(){return sessions;} public MatchmakingService getMatchmaking(){return matchmaking;} public NeverAloneService getNeverAlone(){return neverAlone;} public GuiManager getGui(){return gui;}
    @Override public Optional<UUID> getBuddy(UUID player){BuddySession s=sessions.getSession(player);return s==null?Optional.empty():Optional.of(s.other(player));}
    @Override public Optional<BuddySession> getSession(UUID player){return Optional.ofNullable(sessions.getSession(player));}
    @Override public boolean isQueued(UUID player){return matchmaking.isQueued(player);}
    @Override public boolean joinQueue(UUID player,Activity activity){return matchmaking.enqueue(player,activity);}
    @Override public boolean leaveQueue(UUID player){return matchmaking.leave(player);}
    @Override public int getSocialXp(UUID player){return settings(player).socialXp;}
    @Override public boolean isDnd(UUID player){return settings(player).dnd;}
}
