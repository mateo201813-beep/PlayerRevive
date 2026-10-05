package com.amarrados.revive;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

public final class Msg {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static String prefix = "&8[&cRevive&8] &7";

    private Msg() {
    }

    static void setPrefix(String newPrefix) {
        prefix = newPrefix == null ? "" : newPrefix;
    }

    public static Component color(String text) {
        return LEGACY.deserialize(text);
    }

    /** Sustituye {clave} por su valor: fill("{a} y {b}", "a", "1", "b", "2"). */
    public static String fill(String text, String... pairs) {
        String out = text == null ? "" : text;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out = out.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return out;
    }

    public static void send(CommandSender to, String text) {
        to.sendMessage(color(prefix + text));
    }

    public static void raw(CommandSender to, String text) {
        to.sendMessage(color(text));
    }

    /** Segundos restantes como m:ss. */
    public static String time(int ticks) {
        int seconds = Math.max(0, (ticks + 19) / 20);
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    /** Barra de progreso de 10 bloques. */
    public static String bar(double progress) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, progress)) * 10);
        return "&a" + "▮".repeat(filled) + "&8" + "▮".repeat(10 - filled);
    }
}
