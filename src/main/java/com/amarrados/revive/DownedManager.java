package com.amarrados.revive;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Estado de derribo de todos los jugadores.
 *
 * Para que nadie se quede "inmortal":
 * - Nunca se usa setInvulnerable (se guarda en el jugador y sobrevive a todo). La proteccion
 *   es solo cancelar el daño mientras el jugador esta en el mapa {@link #states}.
 * - Velocidad/salto van con modificadores TRANSITORIOS (no se guardan en disco).
 * - Cada tick se comprueba que el jugador sigue conectado, vivo y en supervivencia; si no, se limpia.
 * - Hay un limite absoluto de tiempo derribado: aunque alguien pause el contador sin parar, muere.
 * - Al morir por cualquier causa, desconectarse o apagar el servidor, se limpia todo.
 */
public final class DownedManager {

    private static final BlockData BARRIER = Material.BARRIER.createBlockData();

    private final RevivePlugin plugin;
    private final ChainHook chain;
    private final Map<UUID, DownedState> states = new HashMap<>();
    /** Mensaje de muerte a poner cuando muere por el plugin (desangrado, rendido...). */
    private final Map<UUID, String> deathMessages = new HashMap<>();
    private final NamespacedKey speedKey;
    private final NamespacedKey jumpKey;
    private final NamespacedKey pdcKey;
    private int tickCounter;

    DownedManager(RevivePlugin plugin) {
        this.plugin = plugin;
        this.speedKey = new NamespacedKey(plugin, "derribado_velocidad");
        this.jumpKey = new NamespacedKey(plugin, "derribado_salto");
        this.pdcKey = new NamespacedKey(plugin, "derribado_ticks");
        this.chain = new ChainHook(plugin.getLogger());
    }

    private Settings s() {
        return plugin.settings();
    }

    public boolean isDowned(Player p) {
        return states.containsKey(p.getUniqueId());
    }

    public boolean isDowned(UUID id) {
        return states.containsKey(id);
    }

    DownedState state(Player p) {
        return states.get(p.getUniqueId());
    }

    Collection<UUID> downedIds() {
        return new ArrayList<>(states.keySet());
    }

    static boolean survivalLike(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
    }

    /** ¿Puede quedar derribado ahora mismo (en vez de morir)? */
    boolean canBeDowned(Player p) {
        return !isDowned(p)
                && survivalLike(p)
                && !p.isDead()
                && !p.hasPermission("amarradosrevive.exento")
                && !s().disabledWorlds.contains(p.getWorld().getName().toLowerCase())
                && !hasTotem(p);
    }

    private static boolean hasTotem(Player p) {
        return p.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
                || p.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }

    // ------------------------------------------------------------------ derribar

    void down(Player p, String attackerName, int ticks, boolean announce) {
        if (isDowned(p)) {
            return;
        }
        Settings st = s();
        DownedState state = new DownedState(p.getUniqueId(), ticks, st.bleedTicks);
        state.attackerName = attackerName;
        states.put(p.getUniqueId(), state);

        if (p.isInsideVehicle()) {
            p.leaveVehicle();
        }
        if (p.isGliding()) {
            p.setGliding(false);
        }
        p.setFireTicks(0);
        p.setAbsorptionAmount(0);
        p.setHealth(Math.min(st.downedHealth, maxHealth(p)));
        p.playHurtAnimation(0f);
        applyModifiers(p);
        if (st.crawl) {
            p.setPose(Pose.SWIMMING, true);
        }
        writeTicks(p, state.ticksLeft);

        // Que los mobs que le estaban atacando le dejen en paz.
        if (st.mobsIgnore) {
            for (Entity e : p.getNearbyEntities(32, 16, 32)) {
                if (e instanceof Mob mob && p.equals(mob.getTarget())) {
                    mob.setTarget(null);
                }
            }
        }

        updateBars(p, state, false);
        p.showBossBar(state.timeBar);
        p.showTitle(Title.title(
                Msg.color(st.msg("derribado-titulo")),
                Msg.color(st.msg("derribado-subtitulo")),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(750))));
        if (st.sounds) {
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.6f);
        }
        if (announce && st.chat) {
            Bukkit.broadcast(Msg.color(Msg.fill(st.msg("chat-derribado"), "jugador", p.getName())));
        }
    }

    // ------------------------------------------------------------------ revivir

    void revive(Player p, Player reviver) {
        DownedState state = states.get(p.getUniqueId());
        if (state == null) {
            return;
        }
        release(p, state);
        Settings st = s();
        if (!p.isDead()) {
            p.setHealth(Math.min(st.reviveHealth, maxHealth(p)));
            p.setFireTicks(0);
            p.setNoDamageTicks(st.protectionTicks);
        }
        String reviverName = reviver == null ? "Un admin" : reviver.getName();
        p.showTitle(Title.title(
                Msg.color(st.msg("revivido-titulo")),
                Msg.color(Msg.fill(st.msg("revivido-subtitulo"), "reviviendo", reviverName)),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(750))));
        if (st.sounds) {
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        }
        if (st.chat) {
            Bukkit.broadcast(Msg.color(Msg.fill(st.msg("chat-revivido"),
                    "jugador", p.getName(), "reviviendo", reviverName)));
        }
    }

    // ------------------------------------------------------------------ morir

    void kill(Player p, String messageKey) {
        kill(p, messageKey, false);
    }

    /**
     * Mata al jugador derribado de verdad. messageKey: clave de mensajes.* para el mensaje de muerte.
     * withTeam: si esta encadenado (AmarradosCadenas), muere tambien todo su equipo.
     */
    void kill(Player p, String messageKey, boolean withTeam) {
        DownedState state = states.get(p.getUniqueId());
        String attacker = state == null ? null : state.attackerName;
        if (state != null) {
            release(p, state);
        }
        if (p.isDead()) {
            return;
        }
        String key = messageKey;
        if ("muerte-desangrado".equals(key) && attacker != null) {
            key = "muerte-desangrado-por";
        }
        deathMessages.put(p.getUniqueId(),
                Msg.fill(s().msg(key), "jugador", p.getName(), "asesino", attacker == null ? "" : attacker));
        try {
            p.setHealth(0.0);
        } finally {
            // Si otro plugin cancelo la muerte, el jugador simplemente sigue vivo (ya no esta derribado).
            deathMessages.remove(p.getUniqueId());
        }
        if (withTeam && s().killChainTeam && p.isDead()) {
            killChainTeam(p);
        }
    }

    /** Mata a todos los encadenados con el (la cadena se mantiene al reaparecer). */
    private void killChainTeam(Player source) {
        List<UUID> members = chain.teamMembers(source.getUniqueId());
        if (members.size() < 2) {
            return;
        }
        List<UUID> tagged = new ArrayList<>();
        for (UUID id : members) {
            Player o = Bukkit.getPlayer(id);
            if (o != null && o != source && !o.isDead()) {
                deathMessages.put(id, Msg.fill(s().msg("muerte-equipo"),
                        "jugador", o.getName(), "companero", source.getName()));
                tagged.add(id);
            }
        }
        if (tagged.isEmpty()) {
            return;
        }
        try {
            chain.killTeam(source);
        } finally {
            tagged.forEach(deathMessages::remove);
        }
    }

    /** Se llama desde PlayerDeathEvent: devuelve el mensaje de muerte personalizado, si lo hay. */
    String takeDeathMessage(Player p) {
        return deathMessages.get(p.getUniqueId());
    }

    // ------------------------------------------------------------------ limpieza

    /** Saca al jugador del estado derribado y quita TODO lo que se le puso. */
    private void release(Player p, DownedState state) {
        states.remove(state.id);
        clearVisuals(p, state);
        p.getPersistentDataContainer().remove(pdcKey);
    }

    private void clearVisuals(Player p, DownedState state) {
        p.hideBossBar(state.timeBar);
        p.hideBossBar(state.reviveBar);
        restoreFakeBlock(p, state);
        removeModifiers(p);
        if (s().crawl) {
            p.setPose(Pose.STANDING, false);
        }
        if (s().glow) {
            PotionEffect glow = p.getPotionEffect(PotionEffectType.GLOWING);
            if (glow != null && glow.getDuration() <= 60) {
                p.removePotionEffect(PotionEffectType.GLOWING);
            }
        }
    }

    void handleQuit(Player p) {
        DownedState state = states.get(p.getUniqueId());
        if (state == null) {
            return;
        }
        if (s().dieOnQuit) {
            kill(p, "muerte-desconectado");
        } else {
            // Se guarda el tiempo que le queda; al volver seguira derribado.
            states.remove(state.id);
            clearVisuals(p, state);
            writeTicks(p, state.ticksLeft);
        }
    }

    void handleJoin(Player p) {
        // Restos de versiones anteriores o de un crash: fuera.
        removeModifiers(p);
        if (s().fixInvulnerable && p.isInvulnerable() && survivalLike(p)) {
            p.setInvulnerable(false);
            plugin.getLogger().info("Quitada la invulnerabilidad bugeada de " + p.getName() + ".");
        }
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        Integer ticks = pdc.get(pdcKey, PersistentDataType.INTEGER);
        if (ticks == null) {
            return;
        }
        pdc.remove(pdcKey);
        // Estaba derribado al salir / al apagarse el servidor: sigue derribado con su tiempo.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !p.isDead() && survivalLike(p) && !isDowned(p)) {
                down(p, null, Math.max(20, ticks), false);
            }
        }, 2L);
    }

    void handleDeath(Player p) {
        DownedState state = states.get(p.getUniqueId());
        if (state != null) {
            release(p, state);
        }
    }

    void shutdown() {
        for (DownedState state : new ArrayList<>(states.values())) {
            Player p = Bukkit.getPlayer(state.id);
            if (p != null) {
                clearVisuals(p, state);
                writeTicks(p, state.ticksLeft);
            }
        }
        states.clear();
    }

    // ------------------------------------------------------------------ tick

    void tick() {
        tickCounter++;
        Settings st = s();
        Set<UUID> busyRevivers = new HashSet<>();
        List<Player> waitingForHelp = new ArrayList<>();

        for (DownedState state : new ArrayList<>(states.values())) {
            Player p = Bukkit.getPlayer(state.id);
            if (p == null || !p.isOnline()) {
                states.remove(state.id);
                continue;
            }
            if (p.isDead()) {
                release(p, state);
                continue;
            }
            if (!survivalLike(p)) {
                // Le pusieron en creativo/espectador: deja de estar derribado.
                release(p, state);
                continue;
            }
            state.ticksDowned++;

            // --- revivir
            Player reviver = findReviver(p, state, busyRevivers);
            if (reviver != null) {
                busyRevivers.add(reviver.getUniqueId());
                if (!reviver.getUniqueId().equals(state.reviver) && st.sounds) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
                }
                state.reviver = reviver.getUniqueId();
                state.progress += 1.0 / st.reviveTicks;
                if (state.progress >= 1.0) {
                    revive(p, reviver);
                    continue;
                }
            } else {
                state.reviver = null;
                state.progress = st.resetProgress ? 0 : Math.max(0, state.progress - 0.5 / st.reviveTicks);
            }

            // --- desangrarse
            boolean paused = reviver != null && st.pauseWhileReviving;
            if (!paused) {
                state.ticksLeft--;
            }
            int hardLimit = state.totalTicks * 2 + st.reviveTicks + 200;
            if (state.ticksLeft <= 0 || state.ticksDowned > hardLimit) {
                kill(p, "muerte-desangrado", true);
                continue;
            }

            // --- estado del jugador
            if (p.getHealth() <= 0) {
                kill(p, "muerte-desangrado", true);
                continue;
            }
            if (p.isInsideVehicle() && st.blockMount) {
                p.leaveVehicle();
            }
            if (st.crawl) {
                p.setPose(Pose.SWIMMING, true);
                updateFakeBlock(p, state);
            }
            if (tickCounter % 20 == 0) {
                applyModifiers(p);
                if (st.glow) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, true, false, false));
                }
            }
            if (tickCounter % 100 == 0) {
                writeTicks(p, state.ticksLeft); // por si el servidor se cae
            }

            // --- pantalla
            updateBars(p, state, paused);
            if (reviver != null) {
                String progress = String.valueOf((int) Math.floor(state.progress * 100));
                if (tickCounter % 10 == 0) {
                    p.sendActionBar(Msg.color(Msg.fill(st.msg("accion-reviviendo-a-ti"),
                            "reviviendo", reviver.getName(), "progreso", progress)));
                }
                if (tickCounter % 5 == 0) {
                    reviver.sendActionBar(Msg.color(Msg.fill(st.msg("accion-revivir-otro"),
                            "jugador", p.getName(), "progreso", progress, "barra", Msg.bar(state.progress))));
                }
            } else {
                waitingForHelp.add(p);
            }
        }

        if (st.hintDistance > 0 && tickCounter % 20 == 0) {
            sendHints(waitingForHelp, busyRevivers);
        }
    }

    private Player findReviver(Player downed, DownedState state, Set<UUID> busy) {
        double maxSq = s().reviveDistance * s().reviveDistance;
        Location loc = downed.getLocation();
        if (state.reviver != null) {
            Player current = Bukkit.getPlayer(state.reviver);
            if (current != null && canRevive(current, downed, busy)
                    && current.getLocation().distanceSquared(loc) <= maxSq) {
                return current;
            }
        }
        Player best = null;
        double bestSq = maxSq;
        for (Player o : downed.getWorld().getPlayers()) {
            if (!canRevive(o, downed, busy)) {
                continue;
            }
            double d = o.getLocation().distanceSquared(loc);
            if (d <= bestSq) {
                bestSq = d;
                best = o;
            }
        }
        return best;
    }

    private boolean canRevive(Player o, Player downed, Set<UUID> busy) {
        return o != downed
                && o.isOnline()
                && !o.isDead()
                && o.getWorld() == downed.getWorld()
                && o.isSneaking()
                && o.getGameMode() != GameMode.SPECTATOR
                && !isDowned(o)
                && !busy.contains(o.getUniqueId())
                && o.hasPermission("amarradosrevive.revivir");
    }

    private void sendHints(List<Player> waiting, Set<UUID> busy) {
        double maxSq = s().hintDistance * s().hintDistance;
        for (Player downed : waiting) {
            DownedState state = states.get(downed.getUniqueId());
            if (state == null) {
                continue;
            }
            Component hint = Msg.color(Msg.fill(s().msg("accion-aviso-cercano"),
                    "jugador", downed.getName(), "tiempo", Msg.time(state.ticksLeft)));
            for (Player o : downed.getWorld().getPlayers()) {
                if (o != downed && !isDowned(o) && !busy.contains(o.getUniqueId())
                        && o.getGameMode() != GameMode.SPECTATOR
                        && o.getLocation().distanceSquared(downed.getLocation()) <= maxSq) {
                    o.sendActionBar(hint);
                }
            }
        }
    }

    private void updateBars(Player p, DownedState state, boolean paused) {
        Settings st = s();
        String time = Msg.time(state.ticksLeft);
        state.timeBar.name(Msg.color(Msg.fill(st.msg(paused ? "barra-tiempo-pausado" : "barra-tiempo"), "tiempo", time)));
        state.timeBar.progress(clamp((float) state.ticksLeft / state.totalTicks));
        state.timeBar.color(paused ? net.kyori.adventure.bossbar.BossBar.Color.YELLOW
                : net.kyori.adventure.bossbar.BossBar.Color.RED);

        if (state.reviver != null) {
            Player r = Bukkit.getPlayer(state.reviver);
            String name = r == null ? "?" : r.getName();
            state.reviveBar.name(Msg.color(Msg.fill(st.msg("barra-reviviendo"),
                    "reviviendo", name, "progreso", String.valueOf((int) Math.floor(state.progress * 100)))));
            state.reviveBar.progress(clamp((float) state.progress));
            if (!state.reviveBarShown) {
                p.showBossBar(state.reviveBar);
                state.reviveBarShown = true;
            }
        } else if (state.reviveBarShown) {
            p.hideBossBar(state.reviveBar);
            state.reviveBarShown = false;
        }
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    // ------------------------------------------------------------------ pose de arrastrarse

    /**
     * Se le envia (solo a el) una barrera invisible encima de la cabeza: el cliente no cabe de pie
     * y se pone a arrastrarse. Solo en el suelo, para que la cuerda de AmarradosCadenas pueda subirle.
     */
    @SuppressWarnings("deprecation")
    private void updateFakeBlock(Player p, DownedState state) {
        Location desired = null;
        if (p.isOnGround() && !p.isInsideVehicle()) {
            Block head = p.getLocation().getBlock().getRelative(BlockFace.UP);
            if (head.getType().isAir()) {
                desired = head.getLocation();
            }
        }
        if (!Objects.equals(desired, state.fakeBlock)) {
            restoreFakeBlock(p, state);
            if (desired != null) {
                p.sendBlockChange(desired, BARRIER);
                state.fakeBlock = desired;
            }
        } else if (desired != null && tickCounter % 20 == 0) {
            p.sendBlockChange(desired, BARRIER);
        }
    }

    private static void restoreFakeBlock(Player p, DownedState state) {
        Location loc = state.fakeBlock;
        state.fakeBlock = null;
        if (loc != null && loc.getWorld() != null && p.isOnline()) {
            p.sendBlockChange(loc, loc.getBlock().getBlockData());
        }
    }

    // ------------------------------------------------------------------ atributos

    private void applyModifiers(Player p) {
        Settings st = s();
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null && !hasModifier(speed, speedKey) && st.speedMultiplier < 1.0) {
            speed.addTransientModifier(new AttributeModifier(speedKey, st.speedMultiplier - 1.0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
        AttributeInstance jump = p.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null && !hasModifier(jump, jumpKey) && !st.canJump) {
            jump.addTransientModifier(new AttributeModifier(jumpKey, -1.0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
    }

    private void removeModifiers(Player p) {
        removeModifier(p.getAttribute(Attribute.MOVEMENT_SPEED), speedKey);
        removeModifier(p.getAttribute(Attribute.JUMP_STRENGTH), jumpKey);
    }

    private static boolean hasModifier(AttributeInstance attr, NamespacedKey key) {
        for (AttributeModifier mod : attr.getModifiers()) {
            if (key.equals(mod.getKey())) {
                return true;
            }
        }
        return false;
    }

    private static void removeModifier(AttributeInstance attr, NamespacedKey key) {
        if (attr == null) {
            return;
        }
        for (AttributeModifier mod : new ArrayList<>(attr.getModifiers())) {
            if (key.equals(mod.getKey())) {
                attr.removeModifier(mod);
            }
        }
    }

    // ------------------------------------------------------------------ util

    private void writeTicks(Player p, int ticks) {
        p.getPersistentDataContainer().set(pdcKey, PersistentDataType.INTEGER, Math.max(1, ticks));
    }

    void addPenalty(Player p, int ticks) {
        DownedState state = states.get(p.getUniqueId());
        if (state != null && ticks > 0) {
            state.ticksLeft -= ticks;
        }
    }

    static double maxHealth(Player p) {
        AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
        return attr == null ? 20.0 : attr.getValue();
    }
}
