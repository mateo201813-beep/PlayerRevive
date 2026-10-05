package com.amarrados.revive;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/** Valores de config.yml ya leidos, para no tocar el YAML en cada tick. */
public final class Settings {

    final int bleedTicks;
    final double downedHealth;
    final double speedMultiplier;
    final boolean crawl;
    final boolean canJump;
    final boolean glow;
    final boolean mobsIgnore;
    final int ticksPerHit;
    final boolean dieOnQuit;
    final double directDeathDamage;
    final Set<DamageCause> directDeathCauses = EnumSet.noneOf(DamageCause.class);
    final Set<String> disabledWorlds = new HashSet<>();
    final Set<String> blockedCommands = new HashSet<>();

    final boolean blockPlace;
    final boolean blockBreak;
    final boolean blockInteract;
    final boolean blockAttack;
    final boolean blockConsume;
    final boolean blockShoot;
    final boolean blockMount;
    final boolean blockGlide;

    final int reviveTicks;
    final double reviveDistance;
    final boolean pauseWhileReviving;
    final boolean resetProgress;
    final double reviveHealth;
    final int protectionTicks;

    final boolean killChainTeam;
    final boolean killChainTeamOnGiveUp;

    final boolean chat;
    final double hintDistance;
    final boolean sounds;
    final boolean fixInvulnerable;

    private final FileConfiguration config;

    Settings(FileConfiguration c, Logger log) {
        this.config = c;
        bleedTicks = Math.max(1, c.getInt("derribado.segundos-para-morir", 60)) * 20;
        downedHealth = Math.max(0.5, c.getDouble("derribado.vida", 1.0));
        speedMultiplier = Math.max(0.0, c.getDouble("derribado.velocidad", 0.6));
        crawl = c.getBoolean("derribado.arrastrarse", true);
        canJump = c.getBoolean("derribado.puede-saltar", false);
        glow = c.getBoolean("derribado.brillar", false);
        mobsIgnore = c.getBoolean("derribado.mobs-ignoran", true);
        ticksPerHit = Math.max(0, c.getInt("derribado.segundos-por-golpe", 0)) * 20;
        dieOnQuit = c.getBoolean("derribado.morir-al-desconectar", true);
        directDeathDamage = c.getDouble("derribado.dano-muerte-directa", 1000.0);
        for (String name : c.getStringList("derribado.causas-muerte-directa")) {
            try {
                directDeathCauses.add(DamageCause.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                log.warning("Causa de daño desconocida en causas-muerte-directa: " + name);
            }
        }
        for (String w : c.getStringList("derribado.mundos-desactivados")) {
            disabledWorlds.add(w.toLowerCase(Locale.ROOT));
        }
        for (String cmd : c.getStringList("derribado.comandos-bloqueados")) {
            blockedCommands.add(cmd.toLowerCase(Locale.ROOT).replaceFirst("^/", ""));
        }

        blockPlace = c.getBoolean("bloquear.poner-bloques", true);
        blockBreak = c.getBoolean("bloquear.romper-bloques", true);
        blockInteract = c.getBoolean("bloquear.interactuar", true);
        blockAttack = c.getBoolean("bloquear.atacar", true);
        blockConsume = c.getBoolean("bloquear.comer-beber", true);
        blockShoot = c.getBoolean("bloquear.disparar", true);
        blockMount = c.getBoolean("bloquear.montar", true);
        blockGlide = c.getBoolean("bloquear.planear-elytra", true);

        reviveTicks = Math.max(1, (int) Math.round(c.getDouble("revivir.segundos", 5) * 20));
        reviveDistance = Math.max(0.5, c.getDouble("revivir.distancia", 2.0));
        pauseWhileReviving = c.getBoolean("revivir.pausar-tiempo", true);
        resetProgress = c.getBoolean("revivir.reiniciar-progreso", true);
        reviveHealth = Math.max(0.5, c.getDouble("revivir.vida", 6.0));
        protectionTicks = Math.max(0, c.getInt("revivir.segundos-proteccion", 2)) * 20;

        killChainTeam = c.getBoolean("cadenas.morir-todo-el-equipo", true);
        killChainTeamOnGiveUp = c.getBoolean("cadenas.tambien-al-rendirse", true);

        chat = c.getBoolean("avisos.chat", true);
        hintDistance = Math.max(0, c.getDouble("avisos.distancia-aviso-cercanos", 12));
        sounds = c.getBoolean("avisos.sonidos", true);
        fixInvulnerable = c.getBoolean("corregir-jugadores-inmortales", true);
    }

    String msg(String key) {
        return config.getString("mensajes." + key, "");
    }

    boolean isCommandBlocked(String label) {
        String l = label.toLowerCase(Locale.ROOT);
        int colon = l.indexOf(':');
        if (colon >= 0) {
            l = l.substring(colon + 1); // essentials:spawn -> spawn
        }
        return blockedCommands.contains(l);
    }
}
