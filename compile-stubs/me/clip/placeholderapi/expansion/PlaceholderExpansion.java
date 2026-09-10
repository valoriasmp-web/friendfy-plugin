package me.clip.placeholderapi.expansion;
import org.bukkit.entity.Player;
public abstract class PlaceholderExpansion {
    public abstract String getIdentifier();
    public abstract String getAuthor();
    public abstract String getVersion();
    public String onPlaceholderRequest(Player player, String identifier) { return null; }
    public boolean persist() { return false; }
    public boolean register() { return true; }
}

