package id.valoria.justfriend.model;

import java.util.UUID;

public final class QueueEntry {
    public final UUID playerId;
    public final Activity activity;
    public final long joinedAt;
    public final long playTicks;
    public final String world;

    public QueueEntry(UUID playerId, Activity activity, long joinedAt, long playTicks, String world) {
        this.playerId = playerId;
        this.activity = activity;
        this.joinedAt = joinedAt;
        this.playTicks = playTicks;
        this.world = world;
    }
}

