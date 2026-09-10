package id.valoria.justfriend.model;

import org.bukkit.Location;
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

    public BuddySession(UUID id, UUID first, UUID second, Activity activity, long startedAt, long endsAt) {
        this.id = id;
        this.first = first;
        this.second = second;
        this.activity = activity;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
    }

    public UUID other(UUID id) { return first.equals(id) ? second : first; }
    public boolean contains(UUID id) { return first.equals(id) || second.equals(id); }
    public long disconnectedAt(UUID id) { return first.equals(id) ? firstDisconnectedAt : secondDisconnectedAt; }
    public void disconnectedAt(UUID id, long value) { if (first.equals(id)) firstDisconnectedAt = value; else secondDisconnectedAt = value; }
}

