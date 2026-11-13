package pl.brat.odlamki;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

import java.util.*;
import java.util.function.Supplier;

public class OdlamkiPlugin extends JavaPlugin implements Listener {

    private NamespacedKey SHARD_KEY, SHOP_KEY, TRIPLE_KEY;

    private static final int PRICE_ARMOR = 20;
    private static final int PRICE_TOOL = 17;
    private static final int PRICE_PICKAXE_PLAIN = 13;

    private record Offer(int price, Supplier<ItemStack> item) {}

    private final Map<Integer, Offer> offers = new LinkedHashMap<>();

    private static class ShopHolder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    @Override
    public void onEnable() {
        SHARD_KEY = new NamespacedKey(this, "odlamek");
        SHOP_KEY = new NamespacedKey(this, "sklep_npc");
        TRIPLE_KEY = new NamespacedKey(this, "kilof_3x3");

        // slot -> oferta
        offers.put(10, new Offer(PRICE_ARMOR, () -> make(Material.NETHERITE_HELMET, "§6§lHelm Brata",
                Enchantment.PROTECTION, 6, Enchantment.UNBREAKING, 5, Enchantment.MENDING, 1,
                Enchantment.RESPIRATION, 3, Enchantment.AQUA_AFFINITY, 1)));
        offers.put(11, new Offer(PRICE_ARMOR, () -> make(Material.NETHERITE_CHESTPLATE, "§6§lKlata Brata",
                Enchantment.PROTECTION, 6, Enchantment.UNBREAKING, 5, Enchantment.MENDING, 1)));
        offers.put(12, new Offer(PRICE_ARMOR, () -> make(Material.NETHERITE_LEGGINGS, "§6§lSpodnie Brata",
                Enchantment.PROTECTION, 5, Enchantment.UNBREAKING, 4, Enchantment.MENDING, 1)));
        offers.put(13, new Offer(PRICE_ARMOR, () -> make(Material.NETHERITE_BOOTS, "§6§lButy Brata",
                Enchantment.PROTECTION, 5, Enchantment.UNBREAKING, 4, Enchantment.MENDING, 1,
                Enchantment.FEATHER_FALLING, 5, Enchantment.DEPTH_STRIDER, 3)));

        offers.put(15, new Offer(PRICE_TOOL, () -> {
            ItemStack i = make(Material.NETHERITE_PICKAXE, "§b§lKilof Brata §7(3x3)",
                    Enchantment.EFFICIENCY, 6, Enchantment.UNBREAKING, 5, Enchantment.MENDING, 1,
                    Enchantment.FORTUNE, 3);
            ItemMeta m = i.getItemMeta();
            m.getPersistentDataContainer().set(TRIPLE_KEY, PersistentDataType.BYTE, (byte) 1);
            m.setLore(List.of("§7Kopanie 3x3"));
            i.setItemMeta(m);
            return i;
        }));
        offers.put(16, new Offer(PRICE_TOOL, () -> make(Material.NETHERITE_AXE, "§b§lSiekiera Brata",
                Enchantment.SHARPNESS, 6, Enchantment.EFFICIENCY, 6, Enchantment.UNBREAKING, 5,
                Enchantment.MENDING, 1)));
        offers.put(14, new Offer(PRICE_TOOL, () -> make(Material.NETHERITE_SWORD, "§b§lMiecz Brata",
                Enchantment.SHARPNESS, 5, Enchantment.LOOTING, 4, Enchantment.SWEEPING_EDGE, 4,
                Enchantment.UNBREAKING, 5, Enchantment.MENDING, 1)));
        offers.put(22, new Offer(PRICE_PICKAXE_PLAIN, () -> make(Material.NETHERITE_PICKAXE, "§b§lKilof Brata",
                Enchantment.EFFICIENCY, 6, Enchantment.UNBREAKING, 5, Enchantment.MENDING, 1)));

        getServer().getPluginManager().registerEvents(this, this);
    }

    // ---------- ITEMY ----------

    private ItemStack make(Material mat, String name, Object... ench) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        for (int i = 0; i < ench.length; i += 2) {
            item.addUnsafeEnchantment((Enchantment) ench[i], (Integer) ench[i + 1]);
        }
        return item;
    }

    private ItemStack shard(int amount) {
        ItemStack item = new ItemStack(Material.PRISMARINE_SHARD, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b§lOdłamek");
        meta.setLore(List.of("§7Wymień u NPC na ekwipunek"));
        meta.getPersistentDataContainer().set(SHARD_KEY, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isShard(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(SHARD_KEY, PersistentDataType.BYTE);
    }

    private int countShards(Player p) {
        int total = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (isShard(it)) total += it.getAmount();
        }
        return total;
    }

    private void removeShards(Player p, int amount) {
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack it = contents[i];
            if (!isShard(it)) continue;
            int take = Math.min(amount, it.getAmount());
            amount -= take;
            if (take == it.getAmount()) contents[i] = null;
            else it.setAmount(it.getAmount() - take);
        }
        p.getInventory().setStorageContents(contents);
    }

    private void giveShards(Player p, int amount) {
        while (amount > 0) {
            int part = Math.min(64, amount);
            amount -= part;
            p.getInventory().addItem(shard(part)).values()
                    .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        }
    }

    // ---------- KOMENDY ----------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                if (args.length == 1 && args[0].equalsIgnoreCase("sklep")) {
            if (sender instanceof Player pl) openShop(pl);
            else sender.sendMessage("Tylko dla gracza.");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("otworz") && sender.hasPermission("odlamki.admin")) {
            Player pl = Bukkit.getPlayerExact(args[1]);
            if (pl != null) openShop(pl);
            return true;
        }
        if (!sender.hasPermission("odlamki.admin")) {
            
            sender.sendMessage("§cBrak uprawnień.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e/odlamki give <gracz> <ilość>\n/odlamki sklep spawn\n/odlamki sklep usun");
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (args.length < 3) { sender.sendMessage("§cUżycie: /odlamki give <gracz> <ilość>"); return true; }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage("§cGracz offline."); return true; }
            int amount;
            try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException e) {
                sender.sendMessage("§cIlość musi być liczbą."); return true;
            }
            if (amount < 1) { sender.sendMessage("§cIlość musi być większa od 0."); return true; }
            giveShards(target, amount);
            sender.sendMessage("§aDano " + amount + " odłamków graczowi " + target.getName());
            target.sendMessage("§bOtrzymałeś §l" + amount + " §bodłamków!");
            return true;
        }

        if (args[0].equalsIgnoreCase("sklep") && args.length >= 2) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Tylko dla gracza."); return true; }
            if (args[1].equalsIgnoreCase("spawn")) {
                p.getWorld().spawn(p.getLocation(), Villager.class, v -> {
                    v.setCustomName("§6§lSklep Odłamków");
                    v.setCustomNameVisible(true);
                    v.setProfession(Villager.Profession.TOOLSMITH);
                    v.setAI(false);
                    v.setInvulnerable(true);
                    v.setSilent(true);
                    v.setPersistent(true);
                    v.setRemoveWhenFarAway(false);
                    v.getPersistentDataContainer().set(SHOP_KEY, PersistentDataType.BYTE, (byte) 1);
                });
                p.sendMessage("§aNPC sklepu utworzony.");
            } else if (args[1].equalsIgnoreCase("usun")) {
                int removed = 0;
                for (Entity e : p.getNearbyEntities(5, 5, 5)) {
                    if (e.getPersistentDataContainer().has(SHOP_KEY, PersistentDataType.BYTE)) {
                        e.remove();
                        removed++;
                    }
                }
                p.sendMessage("§aUsunięto NPC: " + removed);
            }
            return true;
        }
        return false;
    }

    // ---------- NPC + SKLEP ----------

    @EventHandler
    public void onNpcClick(PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getPersistentDataContainer().has(SHOP_KEY, PersistentDataType.BYTE)) return;
        e.setCancelled(true);
        openShop(e.getPlayer());
    }

    private void openShop(Player p) {
        ShopHolder holder = new ShopHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, "§6Sklep Odłamków");
        holder.inv = inv;

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setDisplayName(" ");
        filler.setItemMeta(fm);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        for (Map.Entry<Integer, Offer> en : offers.entrySet()) {
            ItemStack display = en.getValue().item().get();
            ItemMeta m = display.getItemMeta();
            List<String> lore = m.hasLore() ? new ArrayList<>(m.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§7Cena: §b" + en.getValue().price() + " odłamków");
            lore.add("§eKliknij, aby kupić");
            m.setLore(lore);
            display.setItemMeta(m);
            inv.setItem(en.getKey(), display);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onShopClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof ShopHolder)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        Offer offer = offers.get(e.getSlot());
        if (offer == null) return;

        if (countShards(p) < offer.price()) {
            p.sendMessage("§cPotrzebujesz " + offer.price() + " odłamków! Masz: " + countShards(p));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (p.getInventory().firstEmpty() == -1) {
            p.sendMessage("§cMasz pełny ekwipunek!");
            return;
        }
        removeShards(p, offer.price());
        p.getInventory().addItem(offer.item().get());
        p.sendMessage("§aKupiono przedmiot za " + offer.price() + " odłamków!");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    @EventHandler
    public void onShopDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof ShopHolder) e.setCancelled(true);
    }

    // ---------- KILOF 3x3 ----------

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (tool.getType() == Material.AIR || !tool.hasItemMeta()) return;
        if (!tool.getItemMeta().getPersistentDataContainer().has(TRIPLE_KEY, PersistentDataType.BYTE)) return;

        RayTraceResult r = p.rayTraceBlocks(6);
        BlockFace face = (r != null && r.getHitBlockFace() != null) ? r.getHitBlockFace() : BlockFace.UP;
        Block c = e.getBlock();

        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) continue;
                Block t = switch (face) {
                    case UP, DOWN -> c.getRelative(a, 0, b);
                    case NORTH, SOUTH -> c.getRelative(a, b, 0);
                    default -> c.getRelative(0, b, a);
                };
                Material type = t.getType();
                if (type.isAir() || t.isLiquid() || type.getHardness() < 0) continue;
                if (!Tag.MINEABLE_PICKAXE.isTagged(type)) continue;
                if (t.getState() instanceof TileState) continue;
                t.breakNaturally(tool);
            }
        }
    }
}
