package id.valoria.justfriend.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class MatchOffer {
    public final UUID first;
    public final UUID second;
    public final long expiresAt;
    public final Set<UUID> accepted = new HashSet<>();

    public MatchOffer(UUID first, UUID second, long expiresAt) {
        this.first = first;
        this.second = second;
        this.expiresAt = expiresAt;
    }

    public UUID other(UUID id) { return first.equals(id) ? second : first; }
    public boolean contains(UUID id) { return first.equals(id) || second.equals(id); }
    public boolean complete() { return accepted.contains(first) && accepted.contains(second); }
}

