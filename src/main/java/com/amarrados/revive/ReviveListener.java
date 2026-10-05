package com.amarrados.revive;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class ReviveListener implements Listener {

    private final RevivePlugin plugin;
    private final DownedManager downed;

    public ReviveListener(RevivePlugin plugin) {
        this.plugin = plugin;
        this.downed = plugin.downed();
    }

    private Settings s() {
        return plugin.settings();
    }

    // ------------------------------------------------------------------ daño

    /**
     * HIGHEST: va despues del resto de plugins (por ejemplo el anti-PvP de equipo de AmarradosCadenas,
     * que cancela en HIGH). Si el golpe es mortal, se cancela y el jugador queda derribado. Como el
     * evento queda cancelado, la "vida compartida" de AmarradosCadenas (MONITOR, ignoreCancelled)
     * no lo copia al resto del equipo.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        Settings st = s();
        boolean direct = st.directDeathCauses.contains(event.getCause())
                || (st.directDeathDamage > 0 && event.getDamage() >= st.directDeathDamage);

        if (downed.isDowned(p)) {
            if (direct) {
                // /kill, vacio, muerte compartida...: deja de estar derribado y el golpe le mata de verdad.
                downed.handleDeath(p);
                return;
            }
            event.setCancelled(true);
            if (st.ticksPerHit > 0 && event instanceof EntityDamageByEntityEvent) {
                downed.addPenalty(p, st.ticksPerHit);
                p.playHurtAnimation(0f);
            }
            return;
        }

        if (direct || event.getFinalDamage() < p.getHealth() || !downed.canBeDowned(p)) {
            return;
        }
        event.setCancelled(true);
        downed.down(p, attackerName(event), st.bleedTicks, true);
    }

    private static String attackerName(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Entity shooter) {
                damager = shooter;
            }
        } else if (damager instanceof TNTPrimed tnt && tnt.getSource() != null) {
            damager = tnt.getSource();
        }
        return damager instanceof Player pl ? pl.getName() : damager.getName();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!s().blockAttack) {
            return;
        }
        Entity damager = event.getDamager();
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            damager = shooter;
        }
        if (damager instanceof Player p && downed.isDowned(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player p && downed.isDowned(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (s().mobsIgnore && event.getTarget() instanceof Player p && downed.isDowned(p)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ muerte / conexion

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeathMessage(PlayerDeathEvent event) {
        String msg = downed.takeDeathMessage(event.getEntity());
        if (msg != null && !msg.isEmpty()) {
            event.deathMessage(Msg.color(msg));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        // Muera como muera, deja de estar derribado.
        downed.handleDeath(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        downed.handleDeath(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        downed.handleQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        downed.handleJoin(event.getPlayer());
    }

    // ------------------------------------------------------------------ restricciones

    private void deny(Cancellable event, Player p) {
        event.setCancelled(true);
        p.sendActionBar(Msg.color(s().msg("accion-bloqueada")));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (s().blockPlace && downed.isDowned(event.getPlayer())) {
            deny(event, event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (s().blockPlace && downed.isDowned(event.getPlayer())) {
            deny(event, event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (s().blockBreak && downed.isDowned(event.getPlayer())) {
            deny(event, event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player p = event.getPlayer();
        if (p != null && s().blockPlace && downed.isDowned(p)) {
            deny(event, p);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player p = event.getPlayer();
        if (p != null && s().blockPlace && downed.isDowned(p)) {
            deny(event, p);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (s().blockBreak && downed.isDowned(event.getPlayer())) {
            deny(event, event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (!downed.isDowned(p)) {
            return;
        }
        Settings st = s();
        // Siempre se bloquea poner bloques con clic derecho, aunque "interactuar" este en false.
        boolean placing = event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                && event.getItem() != null && event.getItem().getType().isBlock();
        if (st.blockInteract || (st.blockPlace && placing)) {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (s().blockInteract && downed.isDowned(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (s().blockConsume && downed.isDowned(event.getPlayer())) {
            deny(event, event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (s().blockShoot && event.getEntity().getShooter() instanceof Player p && downed.isDowned(p)) {
            deny(event, p);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMount(VehicleEnterEvent event) {
        if (s().blockMount && event.getEntered() instanceof Player p && downed.isDowned(p)) {
            deny(event, p);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (s().blockGlide && event.isGliding() && event.getEntity() instanceof Player p && downed.isDowned(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (!downed.isDowned(p)) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ", 2)[0];
        if (s().isCommandBlocked(label)) {
            event.setCancelled(true);
            Msg.send(p, s().msg("comando-bloqueado"));
        }
    }
}
