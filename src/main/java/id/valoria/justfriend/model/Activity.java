package id.valoria.justfriend.model;

import java.util.Locale;

public enum Activity {
    QUICK, SURVIVAL, MINING, BUILDING, EXPLORING, FARMING, FISHING,
    DUNGEON, HUNTING, SANTAI, BUTUH_BANTUAN, PARTY;

    public static Activity parse(String value) {
        if (value == null || value.isBlank()) return QUICK;
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return QUICK; }
    }

    public String display() {
        return name().replace('_', ' ');
    }
}

