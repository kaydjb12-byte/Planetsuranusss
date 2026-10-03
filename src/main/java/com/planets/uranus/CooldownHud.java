package com.planets.uranus;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Owns the /uranus1 cooldown and shows it on the action bar (just above the XP bar / hearts):
 *
 *   [icon] Uranus Freeze  ██████░░░░ 4.2s
 *
 * The icon is a glyph from the resource pack (font planets:hud). Turn it off in config
 * ("hud.use-icon: false") for players without the pack.
 */
public final class CooldownHud {

    private static final Key FONT = Key.key("planets", "hud");
    /** Glyphs defined in the resource pack's font/hud.json. */
    private static final String ICON_READY = "";
    private static final String ICON_COOLDOWN = "";

    private static final int REFRESH_TICKS = 2;
    private static final int READY_FLASH_TICKS = 30;

    private record Cooldown(long start, long end) {
    }

    private final UranusPlugin plugin;
    private final FreezeManager freeze;
    private final Map<UUID, Cooldown> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> readyFlash = new HashMap<>();
    private BukkitTask task;

    public CooldownHud(UranusPlugin plugin, FreezeManager freeze) {
        this.plugin = plugin;
        this.freeze = freeze;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, REFRESH_TICKS, REFRESH_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        cooldowns.clear();
        readyFlash.clear();
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("hud.enabled", true);
    }

    // ----------------------------------------------------------------- cooldown

    public long remainingMillis(UUID id) {
        Cooldown c = cooldowns.get(id);
        if (c == null) {
            return 0L;
        }
        return Math.max(0L, c.end() - System.currentTimeMillis());
    }

    public void startCooldown(Player player, long millis) {
        long now = System.currentTimeMillis();
        cooldowns.put(player.getUniqueId(), new Cooldown(now, now + Math.max(1L, millis)));
        readyFlash.remove(player.getUniqueId());
        if (enabled() && !freeze.isFrozen(player.getUniqueId())) {
            player.sendActionBar(cooldownComponent(millis, millis));
        }
    }

    // --------------------------------------------------------------------- tick

    private void tick() {
        boolean hud = enabled();
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<UUID, Cooldown>> it = cooldowns.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Cooldown> entry = it.next();
            Cooldown c = entry.getValue();
            Player player = Bukkit.getPlayer(entry.getKey());
            long remaining = c.end() - now;

            if (remaining <= 0) {
                it.remove();
                if (hud && player != null) {
                    readyFlash.put(entry.getKey(), READY_FLASH_TICKS);
                    if (plugin.getConfig().getBoolean("hud.ready-sound", true)) {
                        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.4f);
                    }
                }
                continue;
            }
            // A frozen player sees the "frozen" message instead.
            if (hud && player != null && !freeze.isFrozen(entry.getKey())) {
                player.sendActionBar(cooldownComponent(remaining, c.end() - c.start()));
            }
        }

        Iterator<Map.Entry<UUID, Integer>> flash = readyFlash.entrySet().iterator();
        while (flash.hasNext()) {
            Map.Entry<UUID, Integer> entry = flash.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            int left = entry.getValue() - REFRESH_TICKS;
            if (player == null || left <= 0 || cooldowns.containsKey(entry.getKey())) {
                flash.remove();
                continue;
            }
            entry.setValue(left);
            if (hud && !freeze.isFrozen(entry.getKey())) {
                player.sendActionBar(readyComponent());
            }
        }
    }

    // -------------------------------------------------------------- components

    private String abilityName() {
        return plugin.getConfig().getString("hud.ability-name", "Uranus Freeze");
    }

    private Component icon(boolean cooling) {
        FileConfiguration cfg = plugin.getConfig();
        if (cfg.getBoolean("hud.use-icon", true)) {
            return Component.text(cooling ? ICON_COOLDOWN : ICON_READY, NamedTextColor.WHITE)
                    .font(FONT);
        }
        return Component.text("❄", cooling ? NamedTextColor.DARK_GRAY : NamedTextColor.AQUA);
    }

    private Component cooldownComponent(long remainingMillis, long totalMillis) {
        int length = Math.max(4, Math.min(30, plugin.getConfig().getInt("hud.bar-length", 10)));
        double elapsed = totalMillis <= 0 ? 1.0 : 1.0 - ((double) remainingMillis / (double) totalMillis);
        int filled = (int) Math.round(Math.max(0.0, Math.min(1.0, elapsed)) * length);
        String seconds = String.format(Locale.ROOT, "%.1fs", remainingMillis / 1000.0);

        return Component.text()
                .append(icon(true))
                .append(Component.text(" " + abilityName() + "  ", NamedTextColor.GRAY))
                .append(Component.text("█".repeat(filled), NamedTextColor.AQUA))
                .append(Component.text("█".repeat(length - filled), NamedTextColor.DARK_GRAY))
                .append(Component.text(" " + seconds, NamedTextColor.WHITE))
                .build();
    }

    private Component readyComponent() {
        return Component.text()
                .append(icon(false))
                .append(Component.text(" " + abilityName() + "  ", NamedTextColor.AQUA))
                .append(Component.text("READY", NamedTextColor.GREEN, TextDecoration.BOLD))
                .build();
    }
}
