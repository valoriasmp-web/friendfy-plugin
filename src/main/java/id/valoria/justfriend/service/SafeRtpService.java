package id.valoria.justfriend.service;

import id.valoria.justfriend.JustFriendPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public final class SafeRtpService {
    private final JustFriendPlugin plugin;
    private final Set<Material> unsafe = EnumSet.noneOf(Material.class);
    private final Map<String, Long> failureLogCooldown = new HashMap<>();

    public SafeRtpService(JustFriendPlugin plugin) { this.plugin = plugin; reload(); }

    public void reload() {
        unsafe.clear();
        for (String value : plugin.getConfig().getStringList("safe-rtp.unsafe-blocks")) {
            Material material = Material.matchMaterial(value);
            if (material != null) unsafe.add(material);
        }
    }

    public CompletableFuture<Location[]> findPair(World world) {
        CompletableFuture<Location[]> result = new CompletableFuture<>();
        int attempts = Math.max(1, plugin.getConfig().getInt("safe-rtp.max-attempts", 200));
        Map<String,Integer> rejects = new LinkedHashMap<>();
        Bukkit.getScheduler().runTask(plugin, () -> attempt(world, attempts, attempts, rejects, result));
        return result;
    }

    private void attempt(World world, int remaining, int total, Map<String,Integer> rejects, CompletableFuture<Location[]> result) {
        if (result.isDone()) return;
        if (remaining <= 0) { logFailure(world, rejects); result.complete(null); return; }
        int min = Math.max(0, plugin.getConfig().getInt("safe-rtp.min-radius", 500));
        int max = Math.max(min + 1, plugin.getConfig().getInt("safe-rtp.max-radius", 7000));
        WorldBorder border = world.getWorldBorder();
        double borderRadius = border.getSize() / 2.0 - 16.0;
        max = (int) Math.min(max, Math.max(min + 1, borderRadius));
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);
        double radius = Math.sqrt(ThreadLocalRandom.current().nextDouble(min * (double) min, max * (double) max));
        int x = border.getCenter().getBlockX() + (int) Math.round(Math.cos(angle) * radius);
        int z = border.getCenter().getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
        int cx = x >> 4, cz = z >> 4;
        boolean generated;
        try { generated = world.isChunkGenerated(cx, cz); } catch (RuntimeException e) { generated = false; }
        if (plugin.getConfig().getBoolean("safe-rtp.require-generated-chunk", true) && !generated) {
            reject(rejects,"chunk-belum-generated");
            if (plugin.getConfig().getBoolean("safe-rtp.loaded-chunk-fallback", true) && remaining % 10 == 0) {
                Location[] loadedPair = fromLoadedChunk(world);
                if (loadedPair != null) { result.complete(loadedPair); return; }
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> attempt(world, remaining - 1, total, rejects, result), 1L); return;
        }
        loadExistingChunk(world, cx, cz).whenComplete((loaded, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null || !Boolean.TRUE.equals(loaded)) { reject(rejects,"chunk-gagal-dimuat"); attempt(world, remaining - 1, total, rejects, result); return; }
            Location first = safeSurface(world, x, z);
            Location second = first == null ? null : adjacent(first, plugin.getConfig().getInt("safe-rtp.pair-spacing", 3));
            if (first != null && second != null) result.complete(new Location[]{first, second});
            else { reject(rejects, first == null ? "permukaan-tidak-aman" : "titik-kedua-tidak-aman"); Bukkit.getScheduler().runTaskLater(plugin, () -> attempt(world, remaining - 1, total, rejects, result), 1L); }
        }));
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<Boolean> loadExistingChunk(World world, int x, int z) {
        try {
            Method method = world.getClass().getMethod("getChunkAtAsync", int.class, int.class, boolean.class);
            Object future = method.invoke(world, x, z, false);
            if (future instanceof CompletableFuture<?>) return ((CompletableFuture<Object>) future).handle((chunk, error) -> error == null && chunk != null);
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        try { world.getChunkAt(x, z); return CompletableFuture.completedFuture(true); }
        catch (RuntimeException e) { return CompletableFuture.completedFuture(false); }
    }

    private Location safeSurface(World world, int x, int z) {
        int y;
        try { y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES); } catch (RuntimeException e) { return null; }
        Location location = new Location(world, x + 0.5, y + 1.0, z + 0.5);
        return isSafe(location) ? location : null;
    }

    private Location adjacent(Location origin, int spacing) {
        int[][] offsets = {{spacing,0},{-spacing,0},{0,spacing},{0,-spacing},{spacing,spacing},{-spacing,-spacing}};
        for (int[] offset : offsets) {
            Location candidate = safeSurface(origin.getWorld(), origin.getBlockX() + offset[0], origin.getBlockZ() + offset[1]);
            if (candidate != null && Math.abs(candidate.getY() - origin.getY()) <= 3) return candidate;
        }
        return null;
    }

    private Location[] fromLoadedChunk(World world) {
        Chunk[] chunks = world.getLoadedChunks();
        if (chunks.length == 0) return null;
        for (int i=0;i<Math.min(chunks.length,12);i++) {
            Chunk chunk = chunks[ThreadLocalRandom.current().nextInt(chunks.length)];
            int x = (chunk.getX() << 4) + ThreadLocalRandom.current().nextInt(2,14);
            int z = (chunk.getZ() << 4) + ThreadLocalRandom.current().nextInt(2,14);
            Location first=safeSurface(world,x,z), second=first==null?null:adjacent(first,plugin.getConfig().getInt("safe-rtp.pair-spacing",3));
            if(first!=null&&second!=null)return new Location[]{first,second};
        }
        return null;
    }

    private void reject(Map<String,Integer> rejects,String reason){rejects.merge(reason,1,Integer::sum);}
    private void logFailure(World world,Map<String,Integer> rejects){long now=System.currentTimeMillis();long last=failureLogCooldown.getOrDefault(world.getName(),0L);if(now-last<60000L)return;failureLogCooldown.put(world.getName(),now);plugin.getLogger().warning("SafeRTP gagal di world '"+world.getName()+"': "+rejects+". Cek survival-world, worldborder, chunk pre-generated, claim, dan blok aman.");}

    public boolean isSafe(Location location) {
        if (location == null || location.getWorld() == null) return false;
        World world = location.getWorld();
        if (!world.getWorldBorder().isInside(location)) return false;
        if (plugin.getConfig().getStringList("disabled-worlds").contains(world.getName())) return false;
        Block feet = world.getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        Block head = feet.getRelative(0, 1, 0); Block ground = feet.getRelative(0, -1, 0);
        if (!feet.isPassable() || !head.isPassable() || !ground.getType().isSolid()) return false;
        if (unsafe.contains(feet.getType()) || unsafe.contains(head.getType()) || unsafe.contains(ground.getType())) return false;
        if (!plugin.getConfig().getBoolean("safe-rtp.allow-water", false) && (feet.isLiquid() || head.isLiquid() || ground.isLiquid())) return false;
        return plugin.getIntegrations().isLocationAllowed(location);
    }
}
