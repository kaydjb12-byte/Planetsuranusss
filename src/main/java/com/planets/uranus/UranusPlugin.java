package com.planets.uranus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class UranusPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private UranusItem uranusItem;
    private FreezeManager freezeManager;
    private FrostArmor frostArmor;
    private CooldownHud cooldownHud;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Add any config keys introduced by updates without touching the ones already set.
        getConfig().options().copyDefaults(true);
        saveConfig();

        uranusItem = new UranusItem(this);
        freezeManager = new FreezeManager(this);
        freezeManager.start();
        getServer().getPluginManager().registerEvents(new FreezeListener(freezeManager), this);

        frostArmor = new FrostArmor(this, uranusItem, freezeManager);
        frostArmor.start();
        getServer().getPluginManager().registerEvents(frostArmor, this);

        cooldownHud = new CooldownHud(this, freezeManager);
        cooldownHud.start();

        Objects.requireNonNull(getCommand("uranus1")).setExecutor(this);
        Objects.requireNonNull(getCommand("uranus")).setExecutor(this);
        Objects.requireNonNull(getCommand("uranus")).setTabCompleter(this);

        getLogger().info("PlanetsUranus enabled.");
    }

    @Override
    public void onDisable() {
        if (cooldownHud != null) {
            cooldownHud.stop();
        }
        if (frostArmor != null) {
            frostArmor.stop();
        }
        if (freezeManager != null) {
            freezeManager.stop();
        }
    }

    /** Reads a MiniMessage string from config.yml. */
    public Component message(String key) {
        return message(key, Map.of());
    }

    public Component message(String key, Map<String, String> placeholders) {
        String raw = getConfig().getString("messages." + key, "");
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return MINI.deserialize(raw);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("uranus1")) {
            return handleUranus1(sender);
        }
        if (name.equals("uranus")) {
            return handleUranus(sender, args);
        }
        return false;
    }

    // ---------------------------------------------------------------- /uranus1

    private boolean handleUranus1(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(message("players-only"));
            return true;
        }

        boolean requireItem = getConfig().getBoolean("require-item", true);
        if (requireItem
                && !uranusItem.isUranus(player.getInventory().getItemInMainHand())
                && !uranusItem.isUranus(player.getInventory().getItemInOffHand())) {
            player.sendMessage(message("need-item"));
            return true;
        }

        long remaining = cooldownHud.remainingMillis(player.getUniqueId());
        if (remaining > 0) {
            // With the HUD on, the timer is already on the action bar - keep chat quiet.
            if (!cooldownHud.enabled()) {
                long secondsLeft = (remaining + 999) / 1000;
                player.sendMessage(message("cooldown", Map.of("seconds", String.valueOf(secondsLeft))));
            }
            return true;
        }
        long cooldownMillis = (long) (getConfig().getDouble("cooldown-seconds", 10.0) * 1000.0);

        freezeManager.cast(player);
        cooldownHud.startCooldown(player, cooldownMillis);
        if (!cooldownHud.enabled()) {
            player.sendActionBar(message("cast"));
        }
        return true;
    }

    // ----------------------------------------------------------------- /uranus

    private boolean handleUranus(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /uranus <give [player]|reload>"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> {
                Player target;
                if (args.length >= 2) {
                    target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        sender.sendMessage(message("no-player"));
                        return true;
                    }
                } else if (sender instanceof Player self) {
                    target = self;
                } else {
                    sender.sendMessage(message("players-only"));
                    return true;
                }

                ItemStack item = uranusItem.create();
                Map<Integer, ItemStack> leftover = target.getInventory().addItem(item);
                for (ItemStack extra : leftover.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), extra);
                }
                target.sendMessage(message("given"));
                if (target != sender) {
                    sender.sendMessage(message("given-other", Map.of("player", target.getName())));
                }
            }
            case "reload" -> {
                reloadConfig();
                sender.sendMessage(message("reloaded"));
            }
            default -> sender.sendMessage(Component.text("Usage: /uranus <give [player]|reload>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!command.getName().equalsIgnoreCase("uranus")) {
            return out;
        }
        if (args.length == 1) {
            for (String option : List.of("give", "reload")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(option);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(online.getName());
                }
            }
        }
        return out;
    }
}
