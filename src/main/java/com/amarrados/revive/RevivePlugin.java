package com.amarrados.revive;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class RevivePlugin extends JavaPlugin {

    private Settings settings;
    private DownedManager downed;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        loadSettings();

        if (getServer().getPluginManager().getPlugin("PlayerRevive") != null) {
            getLogger().severe("¡PlayerRevive sigue instalado! Borra su .jar: los dos a la vez se pelean "
                    + "por el daño y pueden dejar jugadores inmortales.");
        }

        downed = new DownedManager(this);
        getServer().getPluginManager().registerEvents(new ReviveListener(this), this);

        ReviveCommand command = new ReviveCommand(this);
        for (String name : new String[]{"revive", "rendirse"}) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(command);
                cmd.setTabCompleter(command);
            }
        }

        getServer().getScheduler().runTaskTimer(this, downed::tick, 1L, 1L);

        // Jugadores ya conectados (por ejemplo tras /reload): recuperar su estado.
        getServer().getOnlinePlayers().forEach(downed::handleJoin);

        getLogger().info("AmarradosRevive activado.");
    }

    @Override
    public void onDisable() {
        if (downed != null) {
            downed.shutdown();
        }
    }

    private void loadSettings() {
        settings = new Settings(getConfig(), getLogger());
        Msg.setPrefix(getConfig().getString("prefijo"));
    }

    public void reload() {
        reloadConfig();
        loadSettings();
    }

    public Settings settings() {
        return settings;
    }

    public DownedManager downed() {
        return downed;
    }
}
