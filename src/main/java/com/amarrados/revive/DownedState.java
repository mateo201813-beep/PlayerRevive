package com.amarrados.revive;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;

import java.util.UUID;

/** Datos de un jugador derribado. Solo existe mientras esta en el mapa de DownedManager. */
final class DownedState {

    final UUID id;
    /** Ticks que le quedan antes de morir. */
    int ticksLeft;
    final int totalTicks;
    /** Ticks totales que lleva derribado (limite absoluto anti-bugs). */
    int ticksDowned;
    /** Progreso de revivir, de 0 a 1. */
    double progress;
    UUID reviver;
    String attackerName;
    /** Bloque falso (barrera) que se le envia encima de la cabeza para que se arrastre. */
    Location fakeBlock;

    final BossBar timeBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_20);
    final BossBar reviveBar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
    boolean reviveBarShown;

    DownedState(UUID id, int ticksLeft, int totalTicks) {
        this.id = id;
        this.ticksLeft = ticksLeft;
        this.totalTicks = Math.max(ticksLeft, totalTicks);
    }
}
