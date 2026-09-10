package id.valoria.justfriend.util;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Text {
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private Text() {}

    public static String color(String input) {
        if (input == null) return "";
        Matcher matcher = HEX.matcher(input);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder legacy = new StringBuilder("§x");
            for (char c : hex.toCharArray()) legacy.append('§').append(c);
            matcher.appendReplacement(out, Matcher.quoteReplacement(legacy.toString()));
        }
        matcher.appendTail(out);
        return ChatColor.translateAlternateColorCodes('&', out.toString());
    }

    public static String replace(String input, Map<String, ?> values) {
        String out = input == null ? "" : input;
        for (Map.Entry<String, ?> e : values.entrySet()) out = out.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        return color(out);
    }

    public static void actionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(color(message)));
    }

    public static void send(CommandSender sender, String message) {
        for (String line : color(message).split("\\n")) sender.sendMessage(line);
    }
}
