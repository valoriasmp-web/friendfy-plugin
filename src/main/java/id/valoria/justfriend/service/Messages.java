package id.valoria.justfriend.service;

import id.valoria.justfriend.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.Map;

public final class Messages {
    private final File file;
    private YamlConfiguration yaml;

    public Messages(File dataFolder) {
        this.file = new File(dataFolder, "messages.yml");
        reload();
    }

    public void reload() { yaml = YamlConfiguration.loadConfiguration(file); }
    public String raw(String key) { return yaml.getString(key, key); }
    public String get(String key) { return Text.color(raw(key)); }
    public String get(String key, Map<String, ?> values) { return Text.replace(raw(key), values); }

    public void send(CommandSender sender, String key) { send(sender, key, Collections.emptyMap()); }
    public void send(CommandSender sender, String key, Map<String, ?> values) {
        String body = get(key, values);
        String prefix = key.equals("prefix") ? "" : get("prefix");
        for (String line : body.split("\\n")) sender.sendMessage(prefix + line);
    }
}
