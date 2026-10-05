package com.amarrados.revive;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Enganche con AmarradosCadenas (CadenasAPI) por reflexion, para no necesitarlo al compilar.
 * Si no esta instalado (o es una version vieja sin CadenasAPI) no hace nada.
 */
final class ChainHook {

    private final Logger log;
    private boolean warned;

    ChainHook(Logger log) {
        this.log = log;
    }

    private Class<?> api() {
        Plugin cadenas = Bukkit.getPluginManager().getPlugin("AmarradosCadenas");
        if (cadenas == null || !cadenas.isEnabled()) {
            return null;
        }
        try {
            return Class.forName("com.amarrados.cadenas.CadenasAPI", true, cadenas.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            if (!warned) {
                warned = true;
                log.warning("AmarradosCadenas es una version antigua (sin CadenasAPI): actualizalo para que "
                        + "muera todo el equipo cuando un derribado se desangra.");
            }
            return null;
        }
    }

    /** Miembros del equipo encadenado del jugador (incluido el); vacio si no esta encadenado. */
    @SuppressWarnings("unchecked")
    List<UUID> teamMembers(UUID player) {
        Class<?> api = api();
        if (api == null) {
            return List.of();
        }
        try {
            Method m = api.getMethod("getTeamMembers", UUID.class);
            return (List<UUID>) m.invoke(null, player);
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.warning("No se pudo leer el equipo de AmarradosCadenas: " + e);
            return List.of();
        }
    }

    /** Mata a todo el equipo del jugador con setHealth(0). La cadena no se rompe. */
    void killTeam(Player player) {
        Class<?> api = api();
        if (api == null) {
            return;
        }
        try {
            Method m = api.getMethod("killTeam", Player.class);
            m.invoke(null, player);
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.warning("No se pudo matar al equipo de AmarradosCadenas: " + e);
        }
    }
}
