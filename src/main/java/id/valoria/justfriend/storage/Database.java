package id.valoria.justfriend.storage;

import id.valoria.justfriend.JustFriendPlugin;
import id.valoria.justfriend.model.Activity;
import id.valoria.justfriend.model.BuddySession;
import id.valoria.justfriend.model.PlayerSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

public final class Database implements AutoCloseable {
    private final JustFriendPlugin plugin;
    private final ExecutorService executor;
    private final String url;
    private final String username;
    private final String password;
    private volatile boolean ready;

    public Database(JustFriendPlugin plugin) {
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "JustFriend-Database");
            thread.setDaemon(true);
            return thread;
        });
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "storage.yml"));
        if ("mysql".equalsIgnoreCase(cfg.getString("type", "sqlite"))) {
            this.url = "jdbc:mariadb://" + cfg.getString("mysql.host", "localhost") + ":" + cfg.getInt("mysql.port", 3306)
                    + "/" + cfg.getString("mysql.database", "justfriend") + "?" + cfg.getString("mysql.parameters", "useSSL=false");
            this.username = cfg.getString("mysql.username", "");
            this.password = cfg.getString("mysql.password", "");
        } else {
            this.url = "jdbc:sqlite:" + new File(plugin.getDataFolder(), cfg.getString("sqlite.file", "justfriend.db")).getAbsolutePath();
            this.username = null;
            this.password = null;
        }
    }

    private Connection connection() throws SQLException {
        return username == null ? DriverManager.getConnection(url) : DriverManager.getConnection(url, username, password);
    }

    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = connection(); Statement s = c.createStatement()) {
                s.executeUpdate("CREATE TABLE IF NOT EXISTS jf_players (uuid VARCHAR(36) PRIMARY KEY, never_alone INT NOT NULL, requests INT NOT NULL, volunteer INT NOT NULL, tracker INT NOT NULL, compass INT NOT NULL, buddy_tp INT NOT NULL, sound INT NOT NULL, dnd INT NOT NULL, social_xp INT NOT NULL, reputation INT NOT NULL, last_notice BIGINT NOT NULL)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS jf_blocks (owner_uuid VARCHAR(36) NOT NULL, target_uuid VARCHAR(36) NOT NULL, PRIMARY KEY(owner_uuid,target_uuid))");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS jf_sessions (session_id VARCHAR(36) PRIMARY KEY, first_uuid VARCHAR(36) NOT NULL, second_uuid VARCHAR(36) NOT NULL, activity VARCHAR(32) NOT NULL, started_at BIGINT NOT NULL, ends_at BIGINT NOT NULL, world VARCHAR(128), x DOUBLE, y DOUBLE, z DOUBLE, first_disconnected BIGINT NOT NULL, second_disconnected BIGINT NOT NULL, active INT NOT NULL)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS jf_session_members (session_id VARCHAR(36) NOT NULL, member_uuid VARCHAR(36) NOT NULL, joined_at BIGINT NOT NULL, disconnected_at BIGINT NOT NULL, member_order INT NOT NULL, is_leader INT NOT NULL, PRIMARY KEY(session_id,member_uuid))");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS jf_history (session_id VARCHAR(36) PRIMARY KEY, first_uuid VARCHAR(36) NOT NULL, second_uuid VARCHAR(36) NOT NULL, started_at BIGINT NOT NULL, ended_at BIGINT NOT NULL, activity VARCHAR(32), reason VARCHAR(64))");
                ready = true;
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor).whenComplete((v, error) -> {
            if (error != null) plugin.getLogger().log(Level.SEVERE, "Database gagal diinisialisasi", error);
        });
    }

    public boolean isReady() { return ready; }

    public CompletableFuture<PlayerSettings> loadSettings(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerSettings settings = new PlayerSettings();
            if (!ready) return settings;
            try (Connection c = connection(); PreparedStatement p = c.prepareStatement("SELECT * FROM jf_players WHERE uuid=?")) {
                p.setString(1, uuid.toString());
                try (ResultSet r = p.executeQuery()) {
                    if (r.next()) {
                        settings.neverAlone = r.getInt("never_alone") != 0;
                        settings.requests = r.getInt("requests") != 0;
                        settings.volunteer = r.getInt("volunteer") != 0;
                        settings.tracker = r.getInt("tracker") != 0;
                        settings.compass = r.getInt("compass") != 0;
                        settings.buddyTp = r.getInt("buddy_tp") != 0;
                        settings.sound = r.getInt("sound") != 0;
                        settings.dnd = r.getInt("dnd") != 0;
                        settings.socialXp = r.getInt("social_xp");
                        settings.reputation = r.getInt("reputation");
                        settings.lastNotice = r.getLong("last_notice");
                    }
                }
            } catch (SQLException e) { log("load settings", e); }
            return settings;
        }, executor);
    }

    public void saveSettings(UUID uuid, PlayerSettings s) {
        execute(() -> {
            String sql = "INSERT INTO jf_players(uuid,never_alone,requests,volunteer,tracker,compass,buddy_tp,sound,dnd,social_xp,reputation,last_notice) VALUES(?,?,?,?,?,?,?,?,?,?,?,?) "
                    + "ON CONFLICT(uuid) DO UPDATE SET never_alone=?,requests=?,volunteer=?,tracker=?,compass=?,buddy_tp=?,sound=?,dnd=?,social_xp=?,reputation=?,last_notice=?";
            if (url.startsWith("jdbc:mariadb")) sql = "INSERT INTO jf_players(uuid,never_alone,requests,volunteer,tracker,compass,buddy_tp,sound,dnd,social_xp,reputation,last_notice) VALUES(?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE never_alone=VALUES(never_alone),requests=VALUES(requests),volunteer=VALUES(volunteer),tracker=VALUES(tracker),compass=VALUES(compass),buddy_tp=VALUES(buddy_tp),sound=VALUES(sound),dnd=VALUES(dnd),social_xp=VALUES(social_xp),reputation=VALUES(reputation),last_notice=VALUES(last_notice)";
            try (Connection c = connection(); PreparedStatement p = c.prepareStatement(sql)) {
                int i = 1;
                p.setString(i++, uuid.toString());
                i = bindSettings(p, i, s);
                if (!url.startsWith("jdbc:mariadb")) bindSettings(p, i, s);
                p.executeUpdate();
            }
        });
    }

    private int bindSettings(PreparedStatement p, int i, PlayerSettings s) throws SQLException {
        p.setInt(i++, s.neverAlone ? 1 : 0); p.setInt(i++, s.requests ? 1 : 0);
        p.setInt(i++, s.volunteer ? 1 : 0); p.setInt(i++, s.tracker ? 1 : 0);
        p.setInt(i++, s.compass ? 1 : 0); p.setInt(i++, s.buddyTp ? 1 : 0);
        p.setInt(i++, s.sound ? 1 : 0); p.setInt(i++, s.dnd ? 1 : 0);
        p.setInt(i++, s.socialXp); p.setInt(i++, s.reputation); p.setLong(i++, s.lastNotice);
        return i;
    }

    public CompletableFuture<Set<UUID>> loadBlocks(UUID owner) {
        return CompletableFuture.supplyAsync(() -> {
            Set<UUID> result = new HashSet<>();
            if (!ready) return result;
            try (Connection c = connection(); PreparedStatement p = c.prepareStatement("SELECT target_uuid FROM jf_blocks WHERE owner_uuid=?")) {
                p.setString(1, owner.toString());
                try (ResultSet r = p.executeQuery()) { while (r.next()) result.add(UUID.fromString(r.getString(1))); }
            } catch (SQLException | IllegalArgumentException e) { log("load blocks", e); }
            return result;
        }, executor);
    }

    public void setBlocked(UUID owner, UUID target, boolean blocked) {
        execute(() -> {
            String sql = blocked ? (url.startsWith("jdbc:mariadb") ? "INSERT IGNORE INTO jf_blocks(owner_uuid,target_uuid) VALUES(?,?)" : "INSERT OR IGNORE INTO jf_blocks(owner_uuid,target_uuid) VALUES(?,?)") : "DELETE FROM jf_blocks WHERE owner_uuid=? AND target_uuid=?";
            try (Connection c = connection(); PreparedStatement p = c.prepareStatement(sql)) {
                p.setString(1, owner.toString()); p.setString(2, target.toString()); p.executeUpdate();
            }
        });
    }

    public void saveSession(BuddySession s) {
        execute(() -> {
            String sql = url.startsWith("jdbc:mariadb")
                    ? "REPLACE INTO jf_sessions(session_id,first_uuid,second_uuid,activity,started_at,ends_at,world,x,y,z,first_disconnected,second_disconnected,active) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)"
                    : "INSERT OR REPLACE INTO jf_sessions(session_id,first_uuid,second_uuid,activity,started_at,ends_at,world,x,y,z,first_disconnected,second_disconnected,active) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)";
            try (Connection c = connection()) {
                c.setAutoCommit(false);
                try (PreparedStatement p = c.prepareStatement(sql)) {
                    p.setString(1, s.id.toString()); p.setString(2, s.first.toString()); p.setString(3, s.second.toString());
                    p.setString(4, s.activity.name()); p.setLong(5, s.startedAt); p.setLong(6, s.endsAt);
                    Location l = s.origin; p.setString(7, l == null || l.getWorld() == null ? null : l.getWorld().getName());
                    p.setDouble(8, l == null ? 0 : l.getX()); p.setDouble(9, l == null ? 0 : l.getY()); p.setDouble(10, l == null ? 0 : l.getZ());
                    p.setLong(11, s.disconnectedAt(s.first)); p.setLong(12, s.disconnectedAt(s.second)); p.setInt(13, s.active ? 1 : 0); p.executeUpdate();
                }
                try (PreparedStatement p = c.prepareStatement("DELETE FROM jf_session_members WHERE session_id=?")) {
                    p.setString(1, s.id.toString()); p.executeUpdate();
                }
                try (PreparedStatement p = c.prepareStatement("INSERT INTO jf_session_members(session_id,member_uuid,joined_at,disconnected_at,member_order,is_leader) VALUES(?,?,?,?,?,?)")) {
                    int order = 0;
                    for (UUID member : s.members()) {
                        p.setString(1, s.id.toString()); p.setString(2, member.toString()); p.setLong(3, s.startedAt);
                        p.setLong(4, s.disconnectedAt(member)); p.setInt(5, order++); p.setInt(6, s.isLeader(member) ? 1 : 0); p.addBatch();
                    }
                    p.executeBatch();
                }
                c.commit();
            }
        });
    }

    public CompletableFuture<List<BuddySession>> loadActiveSessions() {
        return CompletableFuture.supplyAsync(() -> {
            List<BuddySession> result = new ArrayList<>();
            if (!ready) return result;
            try (Connection c = connection()) {
                try (PreparedStatement p = c.prepareStatement("SELECT * FROM jf_sessions WHERE active=1"); ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        BuddySession s = new BuddySession(UUID.fromString(r.getString("session_id")), UUID.fromString(r.getString("first_uuid")), UUID.fromString(r.getString("second_uuid")), Activity.parse(r.getString("activity")), r.getLong("started_at"), r.getLong("ends_at"));
                        World world = worldSync(r.getString("world"));
                        if (world != null) s.origin = new Location(world, r.getDouble("x"), r.getDouble("y"), r.getDouble("z"));
                        s.disconnectedAt(s.first, r.getLong("first_disconnected")); s.disconnectedAt(s.second, r.getLong("second_disconnected"));
                        result.add(s);
                    }
                }
                try (PreparedStatement p = c.prepareStatement("SELECT member_uuid,disconnected_at,is_leader FROM jf_session_members WHERE session_id=? ORDER BY member_order")) {
                    for (BuddySession session : result) {
                        List<UUID> members = new ArrayList<>(); Map<UUID,Long> disconnected = new LinkedHashMap<>(); UUID leader = null;
                        p.setString(1, session.id.toString());
                        try (ResultSet r = p.executeQuery()) {
                            while (r.next()) {
                                UUID member = UUID.fromString(r.getString("member_uuid")); members.add(member);
                                disconnected.put(member, r.getLong("disconnected_at")); if (r.getInt("is_leader") != 0) leader = member;
                            }
                        }
                        if (members.size() >= 2) session.restoreMembers(members, leader, disconnected);
                    }
                }
            } catch (SQLException | IllegalArgumentException e) { log("load sessions", e); }
            return result;
        }, executor);
    }

    public void endSession(BuddySession s, String reason) {
        execute(() -> {
            try (Connection c = connection()) {
                c.setAutoCommit(false);
                try (PreparedStatement p = c.prepareStatement("UPDATE jf_sessions SET active=0 WHERE session_id=?")) { p.setString(1, s.id.toString()); p.executeUpdate(); }
                String sql = url.startsWith("jdbc:mariadb") ? "REPLACE INTO jf_history(session_id,first_uuid,second_uuid,started_at,ended_at,activity,reason) VALUES(?,?,?,?,?,?,?)" : "INSERT OR REPLACE INTO jf_history(session_id,first_uuid,second_uuid,started_at,ended_at,activity,reason) VALUES(?,?,?,?,?,?,?)";
                try (PreparedStatement p = c.prepareStatement(sql)) {
                    p.setString(1, s.id.toString()); p.setString(2, s.first.toString()); p.setString(3, s.second.toString()); p.setLong(4, s.startedAt); p.setLong(5, System.currentTimeMillis()); p.setString(6, s.activity.name()); p.setString(7, reason); p.executeUpdate();
                }
                c.commit();
            }
        });
    }

    private void execute(SqlTask task) {
        if (!ready) return;
        executor.execute(() -> { try { task.run(); } catch (SQLException e) { log("write", e); } });
    }

    private World worldSync(String name) {
        if (name == null) return null;
        try { return Bukkit.getScheduler().callSyncMethod(plugin, () -> Bukkit.getWorld(name)).get(10, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
        catch (ExecutionException | TimeoutException e) { return null; }
    }

    private void log(String action, Exception e) { plugin.getLogger().log(Level.WARNING, "Database " + action + " gagal: " + e.getMessage()); }
    @Override public void close() { executor.shutdown(); try { executor.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
    @FunctionalInterface private interface SqlTask { void run() throws SQLException; }
}
