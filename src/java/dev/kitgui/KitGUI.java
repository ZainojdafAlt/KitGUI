package dev.kitgui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class KitGUI extends JavaPlugin implements Listener, TabExecutor {

    /** Inventory slots 0-35 main (0-8 hotbar), 36 boots, 37 legs, 38 chest, 39 helmet, 40 offhand. */
    static final int SIZE = 41;

    static final class Kit {
        String name; Material icon; ItemStack[] items;
        Kit(String n, Material i, ItemStack[] it) { name = n; icon = i; items = it; }
    }

    static final class MenuHolder implements InventoryHolder {
        Inventory inv; final Map<Integer, String> slots = new HashMap<>();
        @Override public Inventory getInventory() { return inv; }
    }

    static final class EditorHolder implements InventoryHolder {
        Inventory inv; final String kit; final boolean def;
        EditorHolder(String k, boolean d) { kit = k; def = d; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private File kitsFile, playersFile;
    private YamlConfiguration playersCfg;

    // ---------- lifecycle ----------

    @Override public void onEnable() {
        getDataFolder().mkdirs();
        kitsFile = new File(getDataFolder(), "kits.yml");
        playersFile = new File(getDataFolder(), "playerdata.yml");
        playersCfg = YamlConfiguration.loadConfiguration(playersFile);
        if (kitsFile.exists()) loadKits(); else { createDefaults(); saveKits(); }
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("kit")).setExecutor(this);
    }

    @Override public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers())
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof EditorHolder) p.closeInventory();
    }

    // ---------- storage ----------

    static String enc(ItemStack[] a) { return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(a)); }

    static ItemStack[] dec(String s) {
        ItemStack[] r = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(s));
        ItemStack[] out = new ItemStack[SIZE];
        for (int i = 0; i < Math.min(SIZE, r.length); i++)
            out[i] = (r[i] == null || r[i].getType().isAir()) ? null : r[i];
        return out;
    }

    private void loadKits() {
        YamlConfiguration c = YamlConfiguration.loadConfiguration(kitsFile);
        ConfigurationSection sec = c.getConfigurationSection("kits");
        if (sec == null) return;
        for (String name : sec.getKeys(false)) {
            Material m = Material.matchMaterial(sec.getString(name + ".icon", "CHEST"));
            String items = sec.getString(name + ".items");
            if (m == null || items == null) continue;
            kits.put(name, new Kit(name, m, dec(items)));
        }
    }

    private void saveKits() {
        YamlConfiguration c = new YamlConfiguration();
        for (Kit k : kits.values()) {
            c.set("kits." + k.name + ".icon", k.icon.name());
            c.set("kits." + k.name + ".items", enc(k.items));
        }
        try { c.save(kitsFile); } catch (IOException ex) { getLogger().severe("Could not save kits: " + ex.getMessage()); }
    }

    private void savePlayers() {
        try { playersCfg.save(playersFile); } catch (IOException ex) { getLogger().severe("Could not save playerdata: " + ex.getMessage()); }
    }

    private void wipePersonal(String kit) {
        ConfigurationSection sec = playersCfg.getConfigurationSection("p");
        if (sec != null) for (String u : sec.getKeys(false)) playersCfg.set("p." + u + "." + kit, null);
        savePlayers();
    }

    private ItemStack[] layout(Player p, Kit k) {
        String s = playersCfg.getString("p." + p.getUniqueId() + "." + k.name);
        if (s != null) { try { return dec(s); } catch (Exception ignored) { } }
        return k.items;
    }

    // ---------- default kits ----------

    private static ItemStack it(Material m, int amt, Object... ench) {
        ItemStack s = new ItemStack(m, amt);
        if (ench.length > 0) {
            ItemMeta meta = s.getItemMeta();
            for (int i = 0; i < ench.length; i += 2) meta.addEnchant((Enchantment) ench[i], (Integer) ench[i + 1], true);
            s.setItemMeta(meta);
        }
        return s;
    }

    private void createDefaults() {
        ItemStack[] cp = new ItemStack[SIZE];
        cp[0] = it(Material.NETHERITE_SWORD, 1, Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        cp[1] = it(Material.END_CRYSTAL, 64);
        cp[2] = it(Material.OBSIDIAN, 64);
        cp[3] = it(Material.ENCHANTED_GOLDEN_APPLE, 64);
        cp[4] = it(Material.ENDER_PEARL, 16);
        cp[5] = it(Material.TOTEM_OF_UNDYING, 1);
        cp[6] = it(Material.TOTEM_OF_UNDYING, 1);
        cp[7] = it(Material.EXPERIENCE_BOTTLE, 64);
        cp[8] = it(Material.OBSIDIAN, 64);
        cp[36] = it(Material.NETHERITE_BOOTS, 1, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        cp[37] = it(Material.NETHERITE_LEGGINGS, 1, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        cp[38] = it(Material.NETHERITE_CHESTPLATE, 1, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        cp[39] = it(Material.NETHERITE_HELMET, 1, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        cp[40] = it(Material.TOTEM_OF_UNDYING, 1);
        kits.put("cpvp", new Kit("cpvp", Material.END_CRYSTAL, cp));

        ItemStack[] mc = new ItemStack[SIZE];
        mc[0] = it(Material.MACE, 1, Enchantment.DENSITY, 5, Enchantment.BREACH, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        mc[1] = it(Material.NETHERITE_AXE, 1, Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        mc[2] = it(Material.WIND_CHARGE, 64);
        mc[3] = it(Material.ENCHANTED_GOLDEN_APPLE, 16);
        mc[4] = it(Material.ENDER_PEARL, 16);
        mc[5] = it(Material.COBWEB, 64);
        mc[6] = it(Material.WATER_BUCKET, 1);
        mc[7] = it(Material.TOTEM_OF_UNDYING, 1);
        mc[36] = it(Material.NETHERITE_BOOTS, 1, Enchantment.PROTECTION, 4, Enchantment.FEATHER_FALLING, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        mc[37] = it(Material.NETHERITE_LEGGINGS, 1, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        mc[38] = it(Material.NETHERITE_CHESTPLATE, 1, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        mc[39] = it(Material.NETHERITE_HELMET, 1, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        mc[40] = it(Material.TOTEM_OF_UNDYING, 1);
        kits.put("macepvp", new Kit("macepvp", Material.MACE, mc));
    }

    // ---------- GUIs ----------

    private static Component name(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private void openMenu(Player p) {
        int n = kits.size();
        int rows = Math.min(6, Math.max(1, (n + 8) / 9));
        MenuHolder h = new MenuHolder();
        Inventory inv = Bukkit.createInventory(h, rows * 9, Component.text("Kits"));
        h.inv = inv;
        int i = 0;
        for (Kit k : kits.values()) {
            int slot = n <= 9 ? (9 - n) / 2 + i : i;
            i++;
            if (slot >= rows * 9) break;
            ItemStack icon = new ItemStack(k.icon);
            ItemMeta m = icon.getItemMeta();
            m.displayName(name(k.name, NamedTextColor.GOLD));
            m.lore(List.of(name("Click to get this kit", NamedTextColor.GRAY)));
            icon.setItemMeta(m);
            inv.setItem(slot, icon);
            h.slots.put(slot, k.name);
        }
        p.openInventory(inv);
    }

    /** editor slot -> player inventory slot (-1 = filler) */
    static int toInv(int e) {
        if (e < 27) return e + 9;          // main inventory rows
        if (e < 36) return e - 27;         // hotbar
        if (e < 40) return 39 - (e - 36);  // helmet, chest, legs, boots
        if (e == 40) return 40;            // offhand
        return -1;
    }

    private void openEditor(Player p, Kit k, boolean def) {
        EditorHolder h = new EditorHolder(k.name, def);
        Inventory inv = Bukkit.createInventory(h, 45, Component.text((def ? "Default layout: " : "Your layout: ") + k.name));
        h.inv = inv;
        ItemStack[] items = def ? k.items : layout(p, k);
        for (int e = 0; e <= 40; e++) {
            ItemStack s = items[toInv(e)];
            inv.setItem(e, s == null ? null : s.clone());
        }
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.displayName(Component.space());
        pane.setItemMeta(pm);
        for (int e = 41; e < 45; e++) inv.setItem(e, pane);
        p.openInventory(inv);
        msg(p, "Rearrange the items, then close the menu to save. (Bottom row: boots/legs/chest/helmet reversed order is shown top-to-bottom as helmet..boots, then offhand.)", NamedTextColor.YELLOW);
    }

    // ---------- kit giving ----------

    private void giveKit(Player p, Kit k) {
        ItemStack[] items = layout(p, k);
        PlayerInventory inv = p.getInventory();
        inv.clear();
        for (int i = 0; i < SIZE; i++) inv.setItem(i, items[i] == null ? null : items[i].clone());
        msg(p, "You received the " + k.name + " kit.", NamedTextColor.GREEN);
    }

    private ItemStack[] snapshot(Player p) {
        ItemStack[] out = new ItemStack[SIZE];
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < SIZE; i++) {
            ItemStack s = inv.getItem(i);
            out[i] = (s == null || s.getType().isAir()) ? null : s.clone();
        }
        return out;
    }

    // ---------- events ----------

    private static final Set<InventoryAction> BLOCKED = EnumSet.of(
        InventoryAction.MOVE_TO_OTHER_INVENTORY, InventoryAction.COLLECT_TO_CURSOR,
        InventoryAction.HOTBAR_SWAP, InventoryAction.CLONE_STACK,
        InventoryAction.DROP_ALL_SLOT, InventoryAction.DROP_ONE_SLOT,
        InventoryAction.DROP_ALL_CURSOR, InventoryAction.DROP_ONE_CURSOR,
        InventoryAction.UNKNOWN);

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getHolder() instanceof MenuHolder h) {
            e.setCancelled(true);
            if (e.getClickedInventory() != top) return;
            String kn = h.slots.get(e.getSlot());
            Kit k = kn == null ? null : kits.get(kn);
            if (k == null) return;
            Player p = (Player) e.getWhoClicked();
            Bukkit.getScheduler().runTask(this, () -> { p.closeInventory(); giveKit(p, k); });
        } else if (top.getHolder() instanceof EditorHolder) {
            if (e.getClickedInventory() != top
                || e.getSlot() >= 41
                || BLOCKED.contains(e.getAction())
                || e.getClick() == ClickType.DOUBLE_CLICK
                || e.getClick() == ClickType.SWAP_OFFHAND
                || e.getClick() == ClickType.NUMBER_KEY) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getHolder() instanceof MenuHolder) { e.setCancelled(true); return; }
        if (top.getHolder() instanceof EditorHolder)
            for (int s : e.getRawSlots()) if (s >= 41) { e.setCancelled(true); return; }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof EditorHolder h)) return;
        Player p = (Player) e.getPlayer();
        Inventory inv = e.getInventory();
        ItemStack cur = p.getItemOnCursor();
        if (cur != null && !cur.getType().isAir()) {
            for (int s = 0; s <= 40; s++) {
                ItemStack x = inv.getItem(s);
                if (x == null || x.getType().isAir()) { inv.setItem(s, cur.clone()); break; }
            }
            p.setItemOnCursor(null);
        }
        ItemStack[] out = new ItemStack[SIZE];
        for (int s = 0; s <= 40; s++) {
            ItemStack x = inv.getItem(s);
            out[toInv(s)] = (x == null || x.getType().isAir()) ? null : x.clone();
        }
        Kit k = kits.get(h.kit);
        if (k == null) return;
        if (h.def) {
            k.items = out;
            saveKits();
            wipePersonal(k.name);
            msg(p, "Default layout for " + k.name + " saved (player layouts for this kit were reset).", NamedTextColor.GREEN);
        } else {
            playersCfg.set("p." + p.getUniqueId() + "." + k.name, enc(out));
            savePlayers();
            msg(p, "Your layout for " + k.name + " saved.", NamedTextColor.GREEN);
        }
    }

    // ---------- commands ----------

    private static void msg(CommandSender s, String t, NamedTextColor c) { s.sendMessage(Component.text(t, c)); }

    private boolean admin(Player p) {
        if (p.hasPermission("kitgui.admin")) return true;
        msg(p, "Only operators can do that.", NamedTextColor.RED);
        return false;
    }

    private static Material parseIcon(String s) {
        Material m = Material.matchMaterial(s);
        return (m != null && m.isItem() && !m.isAir()) ? m : null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        if (a.length == 0) { openMenu(p); return true; }
        String sub = a[0].toLowerCase();

        if (sub.equals("save")) {
            if (!admin(p)) return true;
            if (a.length < 2 || !a[1].matches("[A-Za-z0-9_-]+")) {
                msg(p, "Usage: /kit save <name> [icon item]  (saves your current inventory)", NamedTextColor.RED);
                return true;
            }
            String n = a[1].toLowerCase();
            Material icon;
            if (a.length >= 3) {
                icon = parseIcon(a[2]);
                if (icon == null) { msg(p, "Unknown item: " + a[2], NamedTextColor.RED); return true; }
            } else {
                Material hand = p.getInventory().getItemInMainHand().getType();
                icon = hand.isAir() ? Material.CHEST : hand;
            }
            ItemStack[] items = snapshot(p);
            Kit k = kits.get(n);
            if (k == null) kits.put(n, new Kit(n, icon, items));
            else { k.items = items; k.icon = icon; wipePersonal(n); }
            saveKits();
            msg(p, "Kit " + n + " saved with icon " + icon.name().toLowerCase() + ".", NamedTextColor.GREEN);

        } else if (sub.equals("edit")) {
            if (a.length < 2 || !kits.containsKey(a[1].toLowerCase())) {
                msg(p, "Usage: /kit edit <kit>", NamedTextColor.RED); return true;
            }
            openEditor(p, kits.get(a[1].toLowerCase()), false);

        } else if (sub.equals("editdefault")) {
            if (!admin(p)) return true;
            if (a.length < 2 || !kits.containsKey(a[1].toLowerCase())) {
                msg(p, "Usage: /kit editdefault <kit> [icon item]", NamedTextColor.RED); return true;
            }
            Kit k = kits.get(a[1].toLowerCase());
            if (a.length >= 3) {
                Material icon = parseIcon(a[2]);
                if (icon == null) { msg(p, "Unknown item: " + a[2], NamedTextColor.RED); return true; }
                k.icon = icon; saveKits();
            }
            openEditor(p, k, true);

        } else if (sub.equals("reset")) {
            if (a.length < 2 || !kits.containsKey(a[1].toLowerCase())) {
                msg(p, "Usage: /kit reset <kit>", NamedTextColor.RED); return true;
            }
            playersCfg.set("p." + p.getUniqueId() + "." + a[1].toLowerCase(), null);
            savePlayers();
            msg(p, "Your layout was reset to the default.", NamedTextColor.GREEN);

        } else if (sub.equals("delete")) {
            if (!admin(p)) return true;
            if (a.length < 2 || kits.remove(a[1].toLowerCase()) == null) {
                msg(p, "Usage: /kit delete <kit>", NamedTextColor.RED); return true;
            }
            saveKits(); wipePersonal(a[1].toLowerCase());
            msg(p, "Kit deleted.", NamedTextColor.GREEN);

        } else {
            msg(p, "/kit | /kit edit <kit> | /kit reset <kit>" + (p.hasPermission("kitgui.admin")
                ? " | /kit save <name> [icon] | /kit editdefault <kit> [icon] | /kit delete <kit>" : ""), NamedTextColor.YELLOW);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        boolean adm = s.hasPermission("kitgui.admin");
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("edit", "reset"));
            if (adm) out.addAll(List.of("save", "editdefault", "delete"));
        } else if (a.length == 2 && !a[0].equalsIgnoreCase("save")) {
            out.addAll(kits.keySet());
        } else if (a.length == 3 && adm && (a[0].equalsIgnoreCase("save") || a[0].equalsIgnoreCase("editdefault"))) {
            String pre = a[2].toLowerCase();
            for (Material m : Material.values())
                if (m.isItem() && !m.isAir() && !m.name().startsWith("LEGACY") && m.name().toLowerCase().startsWith(pre)) {
                    out.add(m.name().toLowerCase());
                    if (out.size() >= 40) break;
                }
            return out;
        }
        String last = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }
}
