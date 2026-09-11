package id.valoria.justfriend.model;

import org.bukkit.Location;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BuddySession {
    public final UUID id;
    public final UUID first;
    public final UUID second;
    public final Activity activity;
    public final long startedAt;
    public long endsAt;
    public Location origin;
    public long firstDisconnectedAt;
    public long secondDisconnectedAt;
    public boolean active = true;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Map<UUID, Long> disconnected = new LinkedHashMap<>();
    private UUID leader;

    public BuddySession(UUID id, UUID first, UUID second, Activity activity, long startedAt, long endsAt) {
        this.id = id;
        this.first = first;
        this.second = second;
        this.activity = activity;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
        this.leader = first;
        addMember(first);
        addMember(second);
    }

    public UUID other(UUID id) {
        for (UUID member : members) if (!member.equals(id)) return member;
        return first.equals(id) ? second : first;
    }
    public boolean contains(UUID id) { return members.contains(id); }
    public List<UUID> members() { return Collections.unmodifiableList(new ArrayList<>(members)); }
    public int size() { return members.size(); }
    public UUID leader() { return leader; }
    public boolean isLeader(UUID id) { return leader != null && leader.equals(id); }
    public void leader(UUID id) { if (members.contains(id)) leader = id; }
    public boolean addMember(UUID id) {
        if (id == null || members.size() >= 4 || !members.add(id)) return false;
        disconnected.put(id, 0L);
        return true;
    }
    public boolean removeMember(UUID id) {
        disconnected.remove(id);
        boolean removed = members.remove(id);
        if (removed && id.equals(leader)) leader = members.stream().findFirst().orElse(null);
        return removed;
    }
    public long disconnectedAt(UUID id) {
        if (first.equals(id)) return Math.max(firstDisconnectedAt, disconnected.getOrDefault(id, 0L));
        if (second.equals(id)) return Math.max(secondDisconnectedAt, disconnected.getOrDefault(id, 0L));
        return disconnected.getOrDefault(id, 0L);
    }
    public void disconnectedAt(UUID id, long value) {
        if (!members.contains(id)) return;
        disconnected.put(id, value);
        if (first.equals(id)) firstDisconnectedAt = value;
        if (second.equals(id)) secondDisconnectedAt = value;
    }
    public void restoreMembers(List<UUID> restored, UUID restoredLeader, Map<UUID, Long> disconnectedAt) {
        if (restored == null || restored.size() < 2) return;
        members.clear(); disconnected.clear();
        for (UUID id : restored) {
            if (members.size() >= 4) break;
            members.add(id); disconnected.put(id, disconnectedAt.getOrDefault(id, 0L));
        }
        leader = members.contains(restoredLeader) ? restoredLeader : members.iterator().next();
        firstDisconnectedAt = disconnected.getOrDefault(first, 0L);
        secondDisconnectedAt = disconnected.getOrDefault(second, 0L);
    }
}
