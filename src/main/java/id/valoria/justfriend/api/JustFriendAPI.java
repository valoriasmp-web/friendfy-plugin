package id.valoria.justfriend.api;

import id.valoria.justfriend.model.Activity;
import id.valoria.justfriend.model.BuddySession;
import java.util.Optional;
import java.util.UUID;

public interface JustFriendAPI {
    Optional<UUID> getBuddy(UUID player);
    Optional<BuddySession> getSession(UUID player);
    boolean isQueued(UUID player);
    boolean joinQueue(UUID player, Activity activity);
    boolean leaveQueue(UUID player);
    int getSocialXp(UUID player);
    boolean isDnd(UUID player);
}

