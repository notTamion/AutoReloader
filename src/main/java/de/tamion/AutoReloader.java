package de.tamion;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.StandardWatchEventKinds.*;

public final class AutoReloader extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static BukkitTask watcherTask;
    private static AutoReloader plugin;

    @Override
    public void onEnable() {
        plugin = this;
        saveDefaultConfig();
        startWatcherThread();
        getCommand("autoreloader").setExecutor(this);
        getCommand("autoreloader").setTabCompleter(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        switch (args.length) {
            case 1:
                switch (args[0].toLowerCase()) {
                    case "start":
                        if(!sender.hasPermission("autoreloader.start")) {
                            sender.sendMessage("You aren't allowed to execute this command");
                            return false;
                        }
                        if(startWatcherThread()) {
                            sender.sendMessage("Started Watcher Thread");
                            return true;
                        }
                        sender.sendMessage("Watcher Thread already running");
                        return false;
                    case "stop":
                        if(!sender.hasPermission("autoreloader.stop")) {
                            sender.sendMessage("You aren't allowed to execute this command");
                            return false;
                        }
                        if(stopWatcherThread()) {
                            sender.sendMessage("Stopped Watcher Thread");
                            return true;
                        }
                        sender.sendMessage("Watcher Thread not running");
                        return false;
                    case "info":
                        if(!sender.hasPermission("autoreloader.info")) {
                            sender.sendMessage("You aren't allowed to execute this command");
                            return false;
                        }
                        sender.sendMessage("Running: " + (watcherTask != null) + "\nDelay: " + getConfig().getLong("delay"));
                        return true;
                    default:
                        sender.sendMessage("Invalid Arguments");
                }
                return false;
            case 2:
                if(args[0].equalsIgnoreCase("setDelay")) {
                    if(!sender.hasPermission("autoreloader.delay")) {
                        sender.sendMessage("You aren't allowed to execute this command");
                        return false;
                    }
                    try {
                        getConfig().set("delay", Long.parseLong(args[1]));
                        saveConfig();
                        sender.sendMessage("Set delay to " + args[1] + " ticks");
                    } catch (NumberFormatException e) {
                        sender.sendMessage("Invalid Value");
                    }
                    return true;
                }
                sender.sendMessage("Invalid Arguments");
                return false;
            default:
                sender.sendMessage("Invalid Arguments");
        }
        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> options = new ArrayList<>();
        switch (args.length) {
            case 1:
                options.add("start");
                options.add("stop");
                options.add("info");
                options.add("setDelay");
        }
        return options;
    }

    private boolean startWatcherThread() {
        if(watcherTask != null) {
            return false;
        }
        watcherTask = new BukkitRunnable(){
            @Override
            public void run() {
                try {
                    WatchService watchService = FileSystems.getDefault().newWatchService();
                    Paths.get("plugins").register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                    WatchKey key;
                    while((key= watchService.take()) != null) {
                        for (WatchEvent<?> event : key.pollEvents()) {
                            if(event.context().toString().endsWith(".jar")) {
                                Bukkit.getScheduler().scheduleSyncDelayedTask(getPlugin(), Bukkit::reload, getPlugin().getConfig().getLong("delay"));
                                return;
                            }
                        }
                        key.reset();
                    }
                } catch (IOException | InterruptedException ignored) {}
            }
        }.runTaskAsynchronously(getPlugin());
        return true;
    }

    private boolean stopWatcherThread() {
        if(watcherTask == null) {
            return false;
        }
        Bukkit.getScheduler().getActiveWorkers().forEach(worker -> {
            if(worker.getTaskId() == watcherTask.getTaskId()) {
                worker.getThread().interrupt();
                watcherTask = null;
            }
        });
        return true;
    }

    @Override
    public void onDisable() {
        stopWatcherThread();
    }

    private AutoReloader getPlugin() {
        return plugin;
    }
}
