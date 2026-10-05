package com.amarrados.revive;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ReviveCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("revivir", "matar", "derribar", "lista", "recargar");

    private final RevivePlugin plugin;

    public ReviveCommand(RevivePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        DownedManager downed = plugin.downed();

        if (command.getName().equalsIgnoreCase("rendirse")) {
            if (!(sender instanceof Player p)) {
                Msg.send(sender, "Solo jugadores.");
                return true;
            }
            if (!p.hasPermission("amarradosrevive.rendirse")) {
                Msg.send(p, "&cNo tienes permiso.");
                return true;
            }
            if (!downed.isDowned(p)) {
                Msg.send(p, plugin.settings().msg("no-derribado"));
                return true;
            }
            downed.kill(p, "muerte-rendido", plugin.settings().killChainTeamOnGiveUp);
            return true;
        }

        if (!sender.hasPermission("amarradosrevive.admin")) {
            Msg.send(sender, "&cNo tienes permiso.");
            return true;
        }
        String sub = args.length == 0 ? "ayuda" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "revivir", "matar", "derribar" -> {
                if (args.length < 2) {
                    Msg.send(sender, "Uso: /revive " + sub + " <jugador>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    Msg.send(sender, "&cEse jugador no esta conectado.");
                    return true;
                }
                if (sub.equals("derribar")) {
                    if (!downed.canBeDowned(target)) {
                        Msg.send(sender, "&c" + target.getName() + " no puede quedar derribado ahora "
                                + "(ya lo esta, no esta en supervivencia, tiene totem o es exento).");
                        return true;
                    }
                    downed.down(target, null, plugin.settings().bleedTicks, true);
                    Msg.send(sender, "&e" + target.getName() + " &7ha sido derribado.");
                    return true;
                }
                if (!downed.isDowned(target)) {
                    Msg.send(sender, "&c" + target.getName() + " no esta derribado.");
                    return true;
                }
                if (sub.equals("revivir")) {
                    downed.revive(target, sender instanceof Player p ? p : null);
                    Msg.send(sender, "&a" + target.getName() + " ha sido revivido.");
                } else {
                    downed.kill(target, "muerte-desangrado", true);
                    Msg.send(sender, "&c" + target.getName() + " ha muerto.");
                }
            }
            case "lista" -> {
                List<String> names = new ArrayList<>();
                for (UUID id : downed.downedIds()) {
                    Player p = Bukkit.getPlayer(id);
                    DownedState st = p == null ? null : downed.state(p);
                    if (st != null) {
                        names.add("&f" + p.getName() + " &7(" + Msg.time(st.ticksLeft) + ")");
                    }
                }
                Msg.send(sender, names.isEmpty() ? "No hay nadie derribado."
                        : "Derribados: " + String.join("&7, ", names));
            }
            case "recargar" -> {
                plugin.reload();
                Msg.send(sender, "&aConfiguracion recargada.");
            }
            default -> {
                Msg.raw(sender, "&6AmarradosRevive &7- comandos:");
                Msg.raw(sender, "&e/revive revivir <jugador> &7- levanta a un derribado");
                Msg.raw(sender, "&e/revive matar <jugador> &7- mata a un derribado (y a su equipo encadenado)");
                Msg.raw(sender, "&e/revive derribar <jugador> &7- derriba a alguien (para probar)");
                Msg.raw(sender, "&e/revive lista &7- quien esta derribado");
                Msg.raw(sender, "&e/revive recargar &7- recarga config.yml");
                Msg.raw(sender, "&e/rendirse &7- morir si estas derribado");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("rendirse") || !sender.hasPermission("amarradosrevive.admin")) {
            return List.of();
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) {
                if (s.startsWith(last)) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("lista") && !args[0].equalsIgnoreCase("recargar")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
