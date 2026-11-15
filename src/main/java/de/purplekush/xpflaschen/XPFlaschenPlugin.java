package de.purplekush.xpflaschen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class XPFlaschenPlugin extends JavaPlugin implements Listener {

    private NamespacedKey restKey;
    private int abLevel;
    private int xpProFlasche;
    private boolean geworfeneGebenXp;
    private String meldung;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        restKey = new NamespacedKey(this, "xp_rest");
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void loadSettings() {
        reloadConfig();
        abLevel = Math.max(1, getConfig().getInt("ab-level", 30));
        xpProFlasche = Math.max(1, getConfig().getInt("xp-pro-flasche", 7));
        geworfeneGebenXp = getConfig().getBoolean("geworfene-flaschen-geben-xp", true);
        meldung = getConfig().getString("meldung", "");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExpChange(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        int amount = event.getAmount();
        if (amount <= 0 || player.hasPermission("purplekush.xpflaschen.bypass")) {
            return;
        }

        // Erfahrung aus geworfenen Flaschen ggf. normal gutschreiben
        if (geworfeneGebenXp && event.getSource() instanceof ExperienceOrb orb
                && orb.getSpawnReason().name().equals("EXP_BOTTLE")) {
            return;
        }

        int level = player.getLevel();
        int umwandeln;

        if (level >= abLevel) {
            umwandeln = amount;
        } else {
            // Wie viel Erfahrung fehlt noch bis zum Ziel-Level?
            int aktuell = gesamtErfahrung(player);
            int fehlt = totalFuerLevel(abLevel) - aktuell;
            if (amount <= fehlt) {
                return; // erreicht Ziel-Level noch nicht -> normal leveln
            }
            umwandeln = amount - fehlt; // Überschuss wird zu Flaschen
            event.setAmount(fehlt);
        }

        if (level >= abLevel) {
            event.setAmount(0);
        }

        int gesamt = umwandeln + player.getPersistentDataContainer()
                .getOrDefault(restKey, PersistentDataType.INTEGER, 0);
        int flaschen = gesamt / xpProFlasche;
        int rest = gesamt % xpProFlasche;
        player.getPersistentDataContainer().set(restKey, PersistentDataType.INTEGER, rest);

        if (flaschen > 0) {
            dropFlaschen(player, flaschen);
            if (meldung != null && !meldung.isEmpty()) {
                Component text = LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(meldung.replace("%anzahl%", String.valueOf(flaschen)));
                player.sendActionBar(text);
            }
        }
    }

    private void dropFlaschen(Player player, int anzahl) {
        Location loc = player.getLocation().add(0, 0.5, 0);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int verbleibend = anzahl;
        while (verbleibend > 0) {
            int stack = Math.min(verbleibend, Material.EXPERIENCE_BOTTLE.getMaxStackSize());
            verbleibend -= stack;
            Item item = player.getWorld().dropItem(loc, new ItemStack(Material.EXPERIENCE_BOTTLE, stack));
            item.setVelocity(new Vector(
                    (rnd.nextDouble() - 0.5) * 0.2,
                    0.2,
                    (rnd.nextDouble() - 0.5) * 0.2));
            item.setPickupDelay(10);
        }
    }

    // --- Vanilla-Erfahrungsformeln ---

    private static int erfahrungFuerNaechstesLevel(int level) {
        if (level >= 31) return 9 * level - 158;
        if (level >= 16) return 5 * level - 38;
        return 2 * level + 7;
    }

    private static int totalFuerLevel(int level) {
        if (level >= 32) return (int) (4.5 * level * level - 162.5 * level + 2220);
        if (level >= 17) return (int) (2.5 * level * level - 40.5 * level + 360);
        return level * level + 6 * level;
    }

    private static int gesamtErfahrung(Player player) {
        int level = player.getLevel();
        return totalFuerLevel(level)
                + Math.round(player.getExp() * erfahrungFuerNaechstesLevel(level));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("purplekush.xpflaschen.admin")) {
            sender.sendMessage(nachricht("nachrichten.keine-rechte"));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            loadSettings();
            sender.sendMessage(nachricht("nachrichten.reload"));
            return true;
        }
        sender.sendMessage(Component.text("/xpflaschen reload"));
        return true;
    }

    private Component nachricht(String pfad) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(getConfig().getString(pfad, ""));
    }
}
