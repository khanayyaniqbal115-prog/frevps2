package com.mythicarsenal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MythicArsenalPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private NamespacedKey itemKey;
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Set<UUID> blackholeArrows = new HashSet<>();
    private final Set<UUID> witherArrows = new HashSet<>();
    private final Map<UUID, UUID> guards = new HashMap<>();      // guard -> owner
    private final Map<UUID, Integer> souls = new HashMap<>();    // soul reaper stacks
    private final Map<UUID, Location> rifts = new HashMap<>();   // rift eye marks
    private final Set<UUID> thunderArrows = new HashSet<>();
    private final Set<UUID> frostArrows = new HashSet<>();
    private static final Set<String> ACTIVE_ITEMS = Set.of(
        "zeus_sword", "ender_blade", "fireball_staff", "frost_wand", "vampire_dagger", "earth_shaker",
        "time_clock", "meteor_staff", "thors_hammer", "hollow_purple", "soul_reaper", "gravity_gauntlet",
        "laser_railgun", "necromancer_tome", "tempest_lance", "solar_blade", "lunar_scythe", "magma_maul",
        "dragon_charge", "rift_eye", "life_staff", "twin_fangs", "desert_scimitar", "tidal_heart", "gale_fan",
        "spirit_lantern", "blood_chalice", "arcane_wand", "hellfire_whip", "executioner_axe",
        "rocket_launcher", "venom_fang", "eclipse_orb");
    private final Component GUI_TITLE = Component.text("MythicArsenal v2.2 (50 Items)", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD);

    // =====================================================================
    //  ENABLE / PASSIVES
    // =====================================================================
    @Override
    public void onEnable() {
        itemKey = new NamespacedKey(this, "mythic_item_id");
        getServer().getPluginManager().registerEvents(this, this);
        var cmd = Objects.requireNonNull(getCommand("mythic"));
        cmd.setExecutor(this);
        cmd.setTabCompleter(this);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if ("hermes_boots".equals(getItemId(p.getInventory().getBoots()))) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 1, true, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 30, 1, true, false));
                        if (p.isSprinting()) {
                            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.1, 0), 4, 0.2, 0.05, 0.2, 0.01);
                        }
                    }
                    String chest = getItemId(p.getInventory().getChestplate());
                    if ("shadow_cloak".equals(chest) && p.isSneaking()) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 30, 0, true, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 1, true, false));
                        p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1, 0), 6, 0.3, 0.5, 0.3, 0.01);
                    }
                    if ("dragon_wings".equals(chest)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 30, 0, true, false));
                        if (p.isGliding()) {
                            p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation(), 6, 0.3, 0.3, 0.3, 0.02);
                        }
                    }
                    if ("titan_chestplate".equals(chest)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 0, true, false));
                    }
                    String helm = getItemId(p.getInventory().getHelmet());
                    if ("halo_helm".equals(helm)) {
                        ring(p.getLocation().add(0, 2.4, 0), 0.4, Particle.END_ROD, 8);
                        double mh = Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue();
                        if (p.getHealth() < mh / 2) {
                            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, true, false));
                        }
                    }
                    if ("wraith_hood".equals(helm)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 260, 0, true, false));
                        if (p.isSneaking()) {
                            for (LivingEntity le : enemiesNear(p, p.getLocation(), 24)) {
                                le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 30, 0, true, false));
                            }
                        }
                    }
                    if ("glacier_greaves".equals(getItemId(p.getInventory().getLeggings()))) {
                        p.setFreezeTicks(0);
                        ring(p.getLocation().add(0, 0.2, 0), 1.0, Particle.SNOWFLAKE, 6);
                        for (LivingEntity le : enemiesNear(p, p.getLocation(), 4)) {
                            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0, true, false));
                        }
                    }
                    if ("midas_crown".equals(getItemId(p.getInventory().getHelmet()))) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 30, 2, true, false));
                        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 2.1, 0), 1, 0.25, 0.05, 0.25, 0.0);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 10L);

        getLogger().info("MythicArsenal v2.2.0 enabled with 50 3D Animated Custom Items!");
    }

    // =====================================================================
    //  ITEMS
    // =====================================================================
    private ItemStack buildItem(Material mat, String id, String name, NamedTextColor color, String loreText) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color, TextDecoration.BOLD));
        meta.lore(List.of(
            Component.text(loreText, NamedTextColor.GRAY),
            Component.text("MythicArsenal v2.2 Relic", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)
        ));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        meta.setItemModel(new NamespacedKey("mythicarsenal", id));
        item.setItemMeta(meta);
        return item;
    }

    public List<ItemStack> getAllCustomItems() {
        return List.of(
            buildItem(Material.DIAMOND_SWORD, "zeus_sword", "1. Zeus's Wrath", NamedTextColor.GOLD, "Right-Click: Call lightning that chains to 3 more enemies (5s CD)"),
            buildItem(Material.NETHERITE_SWORD, "ender_blade", "2. Ender Blade", NamedTextColor.DARK_PURPLE, "Right-Click: Blink 10 blocks, slashing everything on the way (3s CD)"),
            buildItem(Material.BLAZE_ROD, "fireball_staff", "3. Fireball Staff", NamedTextColor.RED, "Right-Click: Fireball | Sneak: Inferno fan of 5 fireballs (4s/7s CD)"),
            buildItem(Material.STICK, "frost_wand", "4. Frost Wand", NamedTextColor.AQUA, "Right-Click: Frost nova - freezes & damages nearby enemies (8s CD)"),
            buildItem(Material.IRON_SWORD, "vampire_dagger", "5. Vampire Dagger", NamedTextColor.DARK_RED, "Hit: Steal life | Right-Click: Blood Frenzy (20s CD)"),
            buildItem(Material.FISHING_ROD, "grappling_hook", "6. Grappling Hook", NamedTextColor.GREEN, "Cast & reel: Swing yourself to the hook, no fall damage (3s CD)"),
            buildItem(Material.GOLDEN_BOOTS, "hermes_boots", "7. Hermes Boots", NamedTextColor.YELLOW, "Passive: Speed II, Jump II & no fall damage"),
            buildItem(Material.BOW, "wither_bow", "8. Wither Bow", NamedTextColor.DARK_GRAY, "Shoot: Fast arrows that rot enemies with Wither (2s CD)"),
            buildItem(Material.NETHERITE_AXE, "earth_shaker", "9. Earth Shaker", NamedTextColor.GOLD, "Right-Click: Ground slam shockwave (7s CD)"),
            buildItem(Material.TOTEM_OF_UNDYING, "phoenix_totem", "10. Phoenix Totem", NamedTextColor.LIGHT_PURPLE, "Right-Click: Heal + flame burst | Passive: Rebirth when you would die (90s)"),
            buildItem(Material.BOW, "blackhole_bow", "11. Black Hole Bow", NamedTextColor.DARK_PURPLE, "Shoot: Arrow opens a black hole that crushes enemies (8s CD)"),
            buildItem(Material.CLOCK, "time_clock", "12. Time Freeze Clock", NamedTextColor.AQUA, "Right-Click: Freeze everyone nearby for 5s (14s CD)"),
            buildItem(Material.NETHERITE_PICKAXE, "tnt_pickaxe", "13. 3x3 Vein Pickaxe", NamedTextColor.RED, "Passive: Mines a 3x3 wall facing you"),
            buildItem(Material.LEATHER_CHESTPLATE, "shadow_cloak", "14. Shadow Cloak", NamedTextColor.DARK_GRAY, "Sneak: Invisible + Speed | Hit while invisible: 1.75x backstab"),
            buildItem(Material.BLAZE_ROD, "meteor_staff", "15. Meteor Staff", NamedTextColor.GOLD, "Right-Click: Marks an area, then rains meteors (14s CD)"),
            buildItem(Material.ELYTRA, "dragon_wings", "16. Dragon Wings", NamedTextColor.LIGHT_PURPLE, "Right-Click gliding: Boost | Sneak+Right-Click on ground: Launch (4s CD)"),
            buildItem(Material.NETHERITE_HOE, "poison_scythe", "17. Poison Scythe", NamedTextColor.GREEN, "Hit: Toxic cloud with Poison, Wither & Nausea (4s CD)"),
            buildItem(Material.SHIELD, "reflection_shield", "18. Shield of Reflection", NamedTextColor.WHITE, "Blocking: Reflects arrows & fireballs, thorns on melee"),
            buildItem(Material.IRON_AXE, "thors_hammer", "19. Thor's Stormbreaker", NamedTextColor.AQUA, "Right-Click: Lightning beam | Sneak: Thunderstorm (6s/16s CD)"),
            buildItem(Material.AMETHYST_SHARD, "hollow_purple", "20. Hollow Purple", NamedTextColor.DARK_PURPLE, "Right-Click: Charge Blue + Red, then fire annihilation beam (18s CD)"),
            buildItem(Material.NETHERITE_SWORD, "soul_reaper", "21. Soul Reaper Blade", NamedTextColor.AQUA, "Kill: Collect souls (Strength per soul) | Right-Click: Release soul wave"),
            buildItem(Material.GOLD_INGOT, "gravity_gauntlet", "22. Gravity Gauntlet", NamedTextColor.GOLD, "Right-Click: Lift & slam | Sneak: Gravity pull (8s CD)"),
            buildItem(Material.REDSTONE_TORCH, "laser_railgun", "23. Plasma Railgun", NamedTextColor.RED, "Right-Click: Piercing plasma beam with recoil (5s CD)"),
            buildItem(Material.GOLDEN_HELMET, "midas_crown", "24. Crown of Midas", NamedTextColor.YELLOW, "Passive: Luck III, mobs drop extra gold & 1.5x XP"),
            buildItem(Material.BOOK, "necromancer_tome", "25. Necromancer Tome", NamedTextColor.DARK_GREEN, "Right-Click: Summon 3 skeleton guards that fight for you (15s CD)"),
            buildItem(Material.END_ROD, "tempest_lance", "26. Tempest Lance", NamedTextColor.AQUA, "Right-Click: Lightning dash that pierces enemies (6s CD)"),
            buildItem(Material.GOLDEN_SWORD, "solar_blade", "27. Solar Blade", NamedTextColor.GOLD, "Right-Click: Solar flare beam, stronger in daylight (7s CD)"),
            buildItem(Material.IRON_HOE, "lunar_scythe", "28. Lunar Scythe", NamedTextColor.BLUE, "Right-Click: Crescent moon wave that pierces (6s CD)"),
            buildItem(Material.CROSSBOW, "thunder_crossbow", "29. Thunder Crossbow", NamedTextColor.YELLOW, "Shoot: Bolts call lightning on impact (4s CD)"),
            buildItem(Material.GOLDEN_AXE, "magma_maul", "30. Magma Maul", NamedTextColor.RED, "Right-Click: Lava fissure erupts in a line (8s CD)"),
            buildItem(Material.FIRE_CHARGE, "dragon_charge", "31. Dragon Charge", NamedTextColor.DARK_RED, "Right-Click: Launch a lingering dragon fireball (8s CD)"),
            buildItem(Material.ENDER_EYE, "rift_eye", "32. Rift Eye", NamedTextColor.DARK_PURPLE, "Right-Click: Mark a rift | Sneak + Right-Click: Jump back to it"),
            buildItem(Material.STICK, "life_staff", "33. Staff of Life", NamedTextColor.GREEN, "Right-Click: Heal yourself and nearby players (12s CD)"),
            buildItem(Material.NETHERITE_CHESTPLATE, "titan_chestplate", "34. Titan's Aegis", NamedTextColor.DARK_GRAY, "Passive: Resistance I and 20% less damage"),
            buildItem(Material.IRON_HELMET, "halo_helm", "35. Halo of Dawn", NamedTextColor.WHITE, "Passive: Regenerate when below half health, glowing halo"),
            buildItem(Material.DIAMOND_LEGGINGS, "glacier_greaves", "36. Glacier Greaves", NamedTextColor.AQUA, "Passive: Freeze aura slows enemies within 4 blocks"),
            buildItem(Material.STONE_SWORD, "twin_fangs", "37. Twin Fangs", NamedTextColor.DARK_PURPLE, "Right-Click: Teleport behind the nearest enemy and double-stab (6s CD)"),
            buildItem(Material.IRON_SWORD, "desert_scimitar", "38. Desert Scimitar", NamedTextColor.GOLD, "Right-Click: Sandstorm that pulls and blinds enemies (14s CD)"),
            buildItem(Material.HEART_OF_THE_SEA, "tidal_heart", "39. Tidal Heart", NamedTextColor.BLUE, "Right-Click: Tidal wave knockback, water powers for you (9s CD)"),
            buildItem(Material.FEATHER, "gale_fan", "40. Gale Fan", NamedTextColor.WHITE, "Right-Click: Wind blast | Sneak: Updraft launch (5s CD)"),
            buildItem(Material.PRISMARINE_CRYSTALS, "spirit_lantern", "41. Spirit Lantern", NamedTextColor.AQUA, "Right-Click: Release 3 homing spirit wisps (10s CD)"),
            buildItem(Material.NAUTILUS_SHELL, "blood_chalice", "42. Blood Chalice", NamedTextColor.DARK_RED, "Right-Click: Pay 2 hearts for Strength III + Speed II (25s CD)"),
            buildItem(Material.BLAZE_ROD, "arcane_wand", "43. Arcane Wand", NamedTextColor.LIGHT_PURPLE, "Right-Click: Barrage of 5 arcane missiles (6s CD)"),
            buildItem(Material.LEAD, "hellfire_whip", "44. Hellfire Whip", NamedTextColor.RED, "Right-Click: Fiery lash that pulls enemies to you (3s CD)"),
            buildItem(Material.BOW, "frost_bow", "45. Frost Bow", NamedTextColor.AQUA, "Shoot: Arrows freeze and shatter on impact (3s CD)"),
            buildItem(Material.DIAMOND_AXE, "executioner_axe", "46. Executioner", NamedTextColor.DARK_RED, "Hit: Finish enemies under 30% HP | Right-Click: Whirlwind (10s CD)"),
            buildItem(Material.LEATHER_HELMET, "wraith_hood", "47. Wraith Hood", NamedTextColor.DARK_GRAY, "Passive: Night Vision | Sneak: Reveal nearby enemies"),
            buildItem(Material.FIREWORK_ROCKET, "rocket_launcher", "48. Rocket Launcher", NamedTextColor.RED, "Right-Click: Explosive rocket (rocket-jump!) (5s CD)"),
            buildItem(Material.FERMENTED_SPIDER_EYE, "venom_fang", "49. Venom Fang", NamedTextColor.GREEN, "Right-Click: Cone of venom spit, Poison II (5s CD)"),
            buildItem(Material.NETHER_STAR, "eclipse_orb", "50. Eclipse Orb", NamedTextColor.DARK_PURPLE, "Right-Click: ULTIMATE - Darkness, Wither & buffs (60s CD)")
        );
    }

    private String getItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    // =====================================================================
    //  HELPERS
    // =====================================================================
    private boolean checkCooldown(Player p, String id, int seconds) {
        String key = p.getUniqueId() + ":" + id;
        long now = System.currentTimeMillis();
        long expire = cooldowns.getOrDefault(key, 0L);
        if (expire > now) {
            long left = (expire - now) / 1000 + 1;
            p.sendActionBar(Component.text("Cooldown: " + left + "s", NamedTextColor.RED));
            return false;
        }
        cooldowns.put(key, now + (seconds * 1000L));
        return true;
    }

    private void refund(Player p, String id) {
        cooldowns.remove(p.getUniqueId() + ":" + id);
    }

    private void cast(Player p, String name, NamedTextColor color) {
        p.sendActionBar(Component.text(name, color, TextDecoration.BOLD));
    }

    private boolean isEnemy(Player p, Entity ent) {
        if (!(ent instanceof LivingEntity) || ent.equals(p) || ent instanceof ArmorStand) return false;
        if (ent instanceof Player o && (o.getGameMode() == GameMode.CREATIVE || o.getGameMode() == GameMode.SPECTATOR)) return false;
        if (ent instanceof Tameable t && t.getOwner() != null && t.getOwner().getUniqueId().equals(p.getUniqueId())) return false;
        UUID owner = guards.get(ent.getUniqueId());
        return owner == null || !owner.equals(p.getUniqueId());
    }

    private List<LivingEntity> enemiesNear(Player p, Location c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : c.getWorld().getNearbyEntities(c, r, r, r)) {
            if (isEnemy(p, e)) out.add((LivingEntity) e);
        }
        return out;
    }

    private void ring(Location c, double r, Particle pt, int n) {
        World w = c.getWorld();
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            w.spawnParticle(pt, c.getX() + Math.cos(a) * r, c.getY(), c.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    private void dustRing(Location c, double r, Color color, float size, int n) {
        World w = c.getWorld();
        Particle.DustOptions d = new Particle.DustOptions(color, size);
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            w.spawnParticle(Particle.DUST, new Location(w, c.getX() + Math.cos(a) * r, c.getY(), c.getZ() + Math.sin(a) * r), 1, 0, 0, 0, 0, d);
        }
    }

    private void line(Location a, Location b, Particle pt) {
        Vector d = b.toVector().subtract(a.toVector());
        double len = d.length();
        if (len < 0.1) return;
        d.normalize();
        for (double i = 0; i < len; i += 0.5) {
            a.getWorld().spawnParticle(pt, a.clone().add(d.clone().multiply(i)), 1, 0, 0, 0, 0);
        }
    }

    /** distance the player's crosshair travels before hitting a block (or max). */
    private double wallDistance(Player p, double max) {
        Location eye = p.getEyeLocation();
        RayTraceResult r = p.getWorld().rayTraceBlocks(eye, eye.getDirection(), max, FluidCollisionMode.NEVER, true);
        return r == null ? max : r.getHitPosition().distance(eye.toVector());
    }

    /** where the player is aiming: block hit position, or a point on the ground ahead. */
    private Location aimPoint(Player p, double max) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        RayTraceResult r = w.rayTraceBlocks(eye, eye.getDirection(), max, FluidCollisionMode.NEVER, true);
        if (r != null) return r.getHitPosition().toLocation(w);
        Location far = eye.clone().add(eye.getDirection().multiply(Math.min(max, 25)));
        return w.getHighestBlockAt(far).getLocation().add(0.5, 1, 0.5);
    }

    // =====================================================================
    //  GUI
    // =====================================================================
    public void openGui(Player p) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);
        List<ItemStack> items = getAllCustomItems();
        for (int i = 0; i < items.size(); i++) {
            gui.setItem(i, items.get(i));
        }
        p.openInventory(gui);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent e) {
        if (e.getView().title().equals(GUI_TITLE)) {
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getView().getTopInventory()) return;
            if (e.getCurrentItem() != null && e.getWhoClicked() instanceof Player p) {
                p.getInventory().addItem(e.getCurrentItem().clone());
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            }
        }
    }

    @EventHandler
    public void onGuiDrag(InventoryDragEvent e) {
        if (e.getView().title().equals(GUI_TITLE)) e.setCancelled(true);
    }

    // =====================================================================
    //  COMMAND
    // =====================================================================
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) return true;
        List<ItemStack> items = getAllCustomItems();

        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            openGui(p);
            return true;
        }

        if (args[0].equalsIgnoreCase("all")) {
            items.forEach(item -> p.getInventory().addItem(item));
            p.sendMessage(Component.text("[MythicArsenal] Given all 50 custom items!", NamedTextColor.GREEN));
            return true;
        }

        try {
            int index = Integer.parseInt(args[0]) - 1;
            if (index >= 0 && index < items.size()) {
                p.getInventory().addItem(items.get(index));
                p.sendMessage(Component.text("[MythicArsenal] Given custom item #" + (index + 1), NamedTextColor.GREEN));
            } else {
                p.sendMessage(Component.text("Pick a number between 1 and 50.", NamedTextColor.RED));
            }
        } catch (NumberFormatException e) {
            p.sendMessage(Component.text("Usage: /mythic [gui|1-50|all]", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("gui", "all", "1", "10", "20", "30", "40", "50");
        }
        return List.of();
    }

    // =====================================================================
    //  RIGHT-CLICK ABILITIES
    // =====================================================================
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        String id = getItemId(e.getItem());
        if (id == null) {
            id = getItemId(p.getInventory().getChestplate());
            if (!"dragon_wings".equals(id)) return;
        }

        if (ACTIVE_ITEMS.contains(id)) e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);

        switch (id) {
            case "zeus_sword" -> zeus(p);
            case "ender_blade" -> enderBlink(p);
            case "fireball_staff" -> fireballStaff(p);
            case "frost_wand" -> frostNova(p);
            case "vampire_dagger" -> bloodFrenzy(p);
            case "wither_bow", "blackhole_bow" -> { /* handled on arrow launch */ }
            case "earth_shaker" -> earthSlam(p);
            case "phoenix_totem" -> {
                e.setCancelled(true);
                phoenixBurst(p);
            }
            case "time_clock" -> timeFreeze(p);
            case "meteor_staff" -> meteorShower(p);
            case "dragon_wings" -> dragonBoost(p);
            case "thors_hammer" -> thor(p);
            case "hollow_purple" -> hollowPurple(p);
            case "soul_reaper" -> soulWave(p);
            case "gravity_gauntlet" -> gravityGauntlet(p);
            case "laser_railgun" -> railgun(p);
            case "necromancer_tome" -> necromancer(p);
            case "tempest_lance" -> tempestLance(p);
            case "solar_blade" -> solarFlare(p);
            case "lunar_scythe" -> lunarSlash(p);
            case "magma_maul" -> magmaFissure(p);
            case "dragon_charge" -> dragonCharge(p);
            case "rift_eye" -> riftEye(p);
            case "life_staff" -> lifeStaff(p);
            case "twin_fangs" -> twinFangs(p);
            case "desert_scimitar" -> sandstorm(p);
            case "tidal_heart" -> tidalWave(p);
            case "gale_fan" -> galeFan(p);
            case "spirit_lantern" -> spiritWisps(p);
            case "blood_chalice" -> bloodChalice(p);
            case "arcane_wand" -> arcaneMissiles(p);
            case "hellfire_whip" -> hellWhip(p);
            case "executioner_axe" -> whirlwind(p);
            case "rocket_launcher" -> rocket(p);
            case "venom_fang" -> venomSpit(p);
            case "eclipse_orb" -> eclipse(p);
            default -> { }
        }
    }

    // ---- 1. Zeus --------------------------------------------------------
    private void zeus(Player p) {
        if (!checkCooldown(p, "zeus_sword", 5)) return;
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        RayTraceResult r = w.rayTrace(eye, eye.getDirection(), 45, FluidCollisionMode.NEVER, true, 0.6, en -> isEnemy(p, en));
        final Location target;
        if (r == null) {
            Location far = eye.clone().add(eye.getDirection().multiply(35));
            target = w.getHighestBlockAt(far).getLocation().add(0.5, 1, 0.5);
        } else if (r.getHitEntity() != null) {
            target = r.getHitEntity().getLocation();
        } else {
            target = r.getHitPosition().toLocation(w);
        }
        cast(p, "Zeus's Wrath!", NamedTextColor.GOLD);
        w.strikeLightningEffect(target);
        w.playSound(target, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 1f);
        w.spawnParticle(Particle.ELECTRIC_SPARK, target.clone().add(0, 1, 0), 60, 1.2, 1.2, 1.2, 0.3);
        Set<UUID> hit = new HashSet<>();
        for (LivingEntity le : enemiesNear(p, target, 3)) {
            le.damage(10, p);
            le.setFireTicks(60);
            hit.add(le.getUniqueId());
        }
        List<LivingEntity> near = enemiesNear(p, target, 9);
        near.sort(Comparator.comparingDouble(en -> en.getLocation().distanceSquared(target)));
        Location from = target;
        int chained = 0;
        for (LivingEntity le : near) {
            if (hit.contains(le.getUniqueId())) continue;
            if (chained++ >= 3) break;
            line(from.clone().add(0, 1, 0), le.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK);
            le.damage(6, p);
            le.setFireTicks(40);
            w.playSound(le.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.4f);
            from = le.getLocation();
        }
    }

    // ---- 2. Ender blade -------------------------------------------------
    private void enderBlink(Player p) {
        if (!checkCooldown(p, "ender_blade", 3)) return;
        World w = p.getWorld();
        Location start = p.getLocation();
        Vector dir = p.getEyeLocation().getDirection().normalize();
        double dist = Math.max(0, wallDistance(p, 10) - 0.8);
        Location dest = start.clone().add(dir.clone().multiply(dist));
        while (dist > 0.5 && (dest.getBlock().getType().isSolid() || dest.clone().add(0, 1, 0).getBlock().getType().isSolid())) {
            dist -= 0.5;
            dest = start.clone().add(dir.clone().multiply(dist));
        }
        if (dist < 1) {
            refund(p, "ender_blade");
            p.sendActionBar(Component.text("Blocked!", NamedTextColor.RED));
            return;
        }
        Set<UUID> hit = new HashSet<>();
        for (double d = 0; d <= dist; d += 1) {
            Location pt = start.clone().add(dir.clone().multiply(d)).add(0, 1, 0);
            w.spawnParticle(Particle.PORTAL, pt, 12, 0.3, 0.4, 0.3, 0.4);
            w.spawnParticle(Particle.SWEEP_ATTACK, pt, 1, 0, 0, 0, 0);
            for (LivingEntity le : enemiesNear(p, pt, 1.6)) {
                if (hit.add(le.getUniqueId())) le.damage(7, p);
            }
        }
        w.playSound(start, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.3f);
        p.teleport(dest);
        p.setFallDistance(0f);
        w.playSound(dest, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
        w.spawnParticle(Particle.REVERSE_PORTAL, dest.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.1);
    }

    // ---- 3. Fireball staff ----------------------------------------------
    private void fireballStaff(Player p) {
        boolean fan = p.isSneaking();
        if (!checkCooldown(p, "fireball_staff", fan ? 7 : 4)) return;
        Vector dir = p.getEyeLocation().getDirection().normalize();
        p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.8f);
        if (!fan) {
            cast(p, "Fireball!", NamedTextColor.RED);
            Fireball fb = p.launchProjectile(Fireball.class, dir.clone().multiply(1.4));
            fb.setYield(2.5f);
            fb.setIsIncendiary(false);
            trail(fb, Particle.FLAME);
        } else {
            cast(p, "Inferno!", NamedTextColor.GOLD);
            for (int i = -2; i <= 2; i++) {
                Vector v = dir.clone().rotateAroundY(Math.toRadians(i * 9)).multiply(1.5);
                SmallFireball sf = p.launchProjectile(SmallFireball.class, v);
                sf.setIsIncendiary(true);
                trail(sf, Particle.FLAME);
            }
        }
    }

    private void trail(Entity ent, Particle pt) {
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!ent.isValid() || t++ > 100) {
                    cancel();
                    return;
                }
                ent.getWorld().spawnParticle(pt, ent.getLocation(), 6, 0.15, 0.15, 0.15, 0.02);
                ent.getWorld().spawnParticle(Particle.SMOKE, ent.getLocation(), 2, 0.1, 0.1, 0.1, 0.01);
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    // ---- 4. Frost wand --------------------------------------------------
    private void frostNova(Player p) {
        if (!checkCooldown(p, "frost_wand", 8)) return;
        Location c = p.getLocation().add(0, 0.2, 0);
        cast(p, "Frost Nova!", NamedTextColor.AQUA);
        p.playSound(c, Sound.BLOCK_GLASS_BREAK, 1f, 0.5f);
        p.playSound(c, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.6f);
        for (LivingEntity le : enemiesNear(p, c, 7)) {
            le.damage(5, p);
            le.setFreezeTicks(240);
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 3));
            le.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 2));
            le.getWorld().spawnParticle(Particle.SNOWFLAKE, le.getLocation().add(0, 1, 0), 25, 0.4, 0.8, 0.4, 0.02);
        }
        new BukkitRunnable() {
            double r = 0.8;
            @Override
            public void run() {
                ring(c, r, Particle.SNOWFLAKE, (int) (r * 14));
                ring(c.clone().add(0, 0.5, 0), r, Particle.END_ROD, (int) (r * 5));
                r += 0.9;
                if (r > 7.5) cancel();
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    // ---- 5. Vampire dagger (active) --------------------------------------
    private void bloodFrenzy(Player p) {
        if (!checkCooldown(p, "vampire_dagger", 20)) return;
        cast(p, "Blood Frenzy!", NamedTextColor.DARK_RED);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 140, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 140, 0));
        p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_BITE, 1f, 0.6f);
        p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0, new Particle.DustOptions(Color.fromRGB(150, 0, 20), 1.6f));
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!p.isOnline() || t++ >= 7) {
                    cancel();
                    return;
                }
                p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 1, 0.3, 0.1, 0.3, 0);
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    // ---- 9. Earth shaker ------------------------------------------------
    private void earthSlam(Player p) {
        if (!checkCooldown(p, "earth_shaker", 7)) return;
        World w = p.getWorld();
        Location c = p.getLocation();
        cast(p, "Earthquake!", NamedTextColor.GOLD);
        Material under = c.getBlock().getRelative(BlockFace.DOWN).getType();
        if (under.isAir()) under = Material.STONE;
        final org.bukkit.block.data.BlockData bd = under.createBlockData();
        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
        w.playSound(c, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
        for (LivingEntity le : enemiesNear(p, c, 6.5)) {
            double d = le.getLocation().distance(c);
            le.damage(Math.max(4, 10 - d), p);
            Vector push = le.getLocation().toVector().subtract(c.toVector());
            push.setY(0);
            if (push.lengthSquared() > 0.01) push.normalize().multiply(0.6);
            push.setY(0.9);
            le.setVelocity(push);
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
        }
        new BukkitRunnable() {
            double r = 1;
            @Override
            public void run() {
                World ww = c.getWorld();
                for (int i = 0; i < r * 10; i++) {
                    double a = 2 * Math.PI * i / (r * 10);
                    Location pt = new Location(ww, c.getX() + Math.cos(a) * r, c.getY() + 0.2, c.getZ() + Math.sin(a) * r);
                    ww.spawnParticle(Particle.BLOCK, pt, 3, 0.2, 0.3, 0.2, 0.1, bd);
                }
                r += 0.9;
                if (r > 7) cancel();
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    // ---- 10. Phoenix totem ----------------------------------------------
    private void phoenixBurst(Player p) {
        if (!checkCooldown(p, "phoenix_totem", 30)) return;
        World w = p.getWorld();
        cast(p, "Phoenix Rising!", NamedTextColor.GOLD);
        double max = Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue();
        p.setHealth(Math.min(max, p.getHealth() + 8));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 2));
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 400, 2));
        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 600, 0));
        p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
        flameBurst(p, 5);
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.6, 1, 0.6, 0.4);
    }

    private void flameBurst(Player p, double radius) {
        Location c = p.getLocation();
        for (LivingEntity le : enemiesNear(p, c, radius)) {
            le.damage(5, p);
            le.setFireTicks(120);
            Vector v = le.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (v.lengthSquared() > 0.01) le.setVelocity(v.normalize().multiply(0.9).setY(0.4));
        }
        new BukkitRunnable() {
            double r = 0.5;
            @Override
            public void run() {
                ring(c.clone().add(0, 0.3, 0), r, Particle.FLAME, (int) (r * 12));
                r += 0.7;
                if (r > radius) cancel();
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    // ---- 12. Time clock -------------------------------------------------
    private void timeFreeze(Player p) {
        if (!checkCooldown(p, "time_clock", 14)) return;
        Location c = p.getLocation();
        cast(p, "ZA WARUDO! Time stops...", NamedTextColor.AQUA);
        p.playSound(c, Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 0.5f);
        p.playSound(c, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1.5f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
        List<LivingEntity> frozen = enemiesNear(p, c, 12);
        for (LivingEntity le : frozen) {
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 254));
            le.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 200));
            le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
            if (le instanceof Mob mob) {
                mob.setAware(false);
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (mob.isValid()) mob.setAware(true);
                }, 100L);
            }
        }
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t > 12 || !p.isOnline()) {
                    cancel();
                    return;
                }
                ring(c.clone().add(0, 0.2 + t * 0.15, 0), 1 + t, Particle.END_ROD, 6 + t * 5);
                t++;
            }
        }.runTaskTimer(this, 0L, 1L);
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t++ >= 5) {
                    cancel();
                    return;
                }
                for (LivingEntity le : frozen) {
                    if (le.isValid()) le.getWorld().spawnParticle(Particle.SNOWFLAKE, le.getLocation().add(0, 1, 0), 8, 0.3, 0.6, 0.3, 0.01);
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    // ---- 15. Meteor staff -----------------------------------------------
    private void meteorShower(Player p) {
        if (!checkCooldown(p, "meteor_staff", 14)) return;
        Location center = aimPoint(p, 50);
        World w = center.getWorld();
        cast(p, "Meteor Shower incoming!", NamedTextColor.GOLD);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.8f);
        // telegraph
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t++ > 20) {
                    cancel();
                    return;
                }
                dustRing(center.clone().add(0, 0.2, 0), 5, Color.RED, 1.4f, 40);
                dustRing(center.clone().add(0, 0.2, 0), 2.5, Color.ORANGE, 1.2f, 20);
            }
        }.runTaskTimer(this, 0L, 2L);
        // meteors
        for (int i = 0; i < 7; i++) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                Location spawn = center.clone().add(rnd.nextDouble(-4, 4), 28, rnd.nextDouble(-4, 4));
                LargeFireball m = (LargeFireball) w.spawnEntity(spawn, EntityType.FIREBALL);
                m.setShooter(p);
                m.setDirection(new Vector(0, -1, 0));
                m.setVelocity(new Vector(rnd.nextDouble(-0.05, 0.05), -1.7, rnd.nextDouble(-0.05, 0.05)));
                m.setYield(2.5f);
                m.setIsIncendiary(false);
                trail(m, Particle.FLAME);
            }, 30L + i * 5L);
        }
    }

    // ---- 16. Dragon wings -----------------------------------------------
    private void dragonBoost(Player p) {
        if (p.isGliding()) {
            if (!checkCooldown(p, "dragon_wings", 4)) return;
            cast(p, "Dragon Boost!", NamedTextColor.LIGHT_PURPLE);
            p.setVelocity(p.getLocation().getDirection().multiply(2.0));
        } else if (p.isSneaking() && p.isOnGround()) {
            if (!checkCooldown(p, "dragon_wings", 4)) return;
            cast(p, "Wing Launch!", NamedTextColor.LIGHT_PURPLE);
            p.setVelocity(new Vector(0, 1.5, 0).add(p.getLocation().getDirection().multiply(0.6)));
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline() && !p.isOnGround()) p.setGliding(true);
            }, 8L);
        } else {
            return;
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 1.2f);
        p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.8f);
        for (LivingEntity le : enemiesNear(p, p.getLocation(), 4)) {
            le.damage(4, p);
            le.setFireTicks(60);
        }
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!p.isOnline() || t++ > 15) {
                    cancel();
                    return;
                }
                Location l = p.getLocation();
                l.getWorld().spawnParticle(Particle.REVERSE_PORTAL, l, 15, 0.4, 0.4, 0.4, 0.05);
                l.getWorld().spawnParticle(Particle.FLAME, l, 10, 0.3, 0.3, 0.3, 0.05);
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    // ---- 19. Thor ---------------------------------------------------------
    private void thor(Player p) {
        World w = p.getWorld();
        if (p.isSneaking()) {
            if (!checkCooldown(p, "thors_hammer_storm", 16)) return;
            cast(p, "THUNDERSTORM!", NamedTextColor.AQUA);
            Location c = p.getLocation();
            p.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.8f);
            for (int i = 0; i < 6; i++) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    List<LivingEntity> es = enemiesNear(p, c, 9);
                    Location t;
                    if (es.isEmpty()) {
                        ThreadLocalRandom r = ThreadLocalRandom.current();
                        t = c.clone().add(r.nextDouble(-8, 8), 0, r.nextDouble(-8, 8));
                        t = w.getHighestBlockAt(t).getLocation().add(0.5, 1, 0.5);
                    } else {
                        t = es.get(ThreadLocalRandom.current().nextInt(es.size())).getLocation();
                    }
                    w.strikeLightningEffect(t);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, t.clone().add(0, 1, 0), 40, 1, 1, 1, 0.3);
                    for (LivingEntity le : enemiesNear(p, t, 3)) {
                        le.damage(8, p);
                        le.setFireTicks(40);
                    }
                }, i * 6L);
            }
            return;
        }
        if (!checkCooldown(p, "thors_hammer", 6)) return;
        cast(p, "Stormbreaker!", NamedTextColor.AQUA);
        Location start = p.getEyeLocation();
        Vector dir = start.getDirection().normalize();
        p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 0.7f);
        new BukkitRunnable() {
            int step = 0;
            final Set<UUID> hit = new HashSet<>();
            @Override
            public void run() {
                step++;
                Location point = start.clone().add(dir.clone().multiply(step * 1.6));
                boolean end = step >= 14 || point.getBlock().getType().isSolid();
                w.spawnParticle(Particle.ELECTRIC_SPARK, point, 18, 0.35, 0.35, 0.35, 0.05);
                w.spawnParticle(Particle.CLOUD, point, 2, 0.1, 0.1, 0.1, 0.01);
                for (LivingEntity le : enemiesNear(p, point, 1.6)) {
                    if (hit.add(le.getUniqueId())) {
                        le.damage(9, p);
                        w.strikeLightningEffect(le.getLocation());
                        le.setVelocity(dir.clone().multiply(0.8).setY(0.3));
                    }
                }
                if (end) {
                    w.strikeLightningEffect(point);
                    w.playSound(point, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.2f, 1f);
                    for (LivingEntity le : enemiesNear(p, point, 3)) {
                        if (hit.add(le.getUniqueId())) le.damage(6, p);
                    }
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    // ---- 20. Hollow Purple ----------------------------------------------
    private void hollowPurple(Player p) {
        if (!checkCooldown(p, "hollow_purple", 18)) return;
        World w = p.getWorld();
        cast(p, "Hollow Purple... charging", NamedTextColor.DARK_PURPLE);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 36, 3));
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.5f);
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                Location eye = p.getEyeLocation();
                Vector dir = eye.getDirection().normalize();
                Vector cr = dir.clone().crossProduct(new Vector(0, 1, 0));
                Vector right = cr.lengthSquared() < 1e-6 ? new Vector(1, 0, 0) : cr.normalize();
                Location mid = eye.clone().add(dir.clone().multiply(3)).add(0, -0.2, 0);
                double sep = 1.8 * (1 - t / 30.0);
                Location blue = mid.clone().add(right.clone().multiply(-sep));
                Location red = mid.clone().add(right.clone().multiply(sep));
                w.spawnParticle(Particle.DUST, blue, 12, 0.2, 0.2, 0.2, 0, new Particle.DustOptions(Color.fromRGB(40, 90, 255), 1.6f));
                w.spawnParticle(Particle.DUST, red, 12, 0.2, 0.2, 0.2, 0, new Particle.DustOptions(Color.fromRGB(255, 40, 40), 1.6f));
                if (t % 5 == 0) w.playSound(mid, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.5f + t / 30f);
                t++;
                if (t > 30) {
                    cancel();
                    fireHollowPurple(p);
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void fireHollowPurple(Player p) {
        World w = p.getWorld();
        Location start = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(2));
        Vector dir = p.getEyeLocation().getDirection().normalize();
        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 0.8f);
        cast(p, "HOLLOW PURPLE!", NamedTextColor.LIGHT_PURPLE);
        Vector kick = dir.clone().multiply(-0.5).setY(0.1);
        p.setVelocity(kick);
        new BukkitRunnable() {
            int step = 0;
            final Set<UUID> hit = new HashSet<>();
            @Override
            public void run() {
                step++;
                Location point = start.clone().add(dir.clone().multiply(step * 2.0));
                boolean end = step >= 22 || point.getBlock().getType().isSolid();
                w.spawnParticle(Particle.REVERSE_PORTAL, point, 40, 1.0, 1.0, 1.0, 0.02);
                w.spawnParticle(Particle.WITCH, point, 25, 0.9, 0.9, 0.9, 0.02);
                w.spawnParticle(Particle.DUST, point, 20, 0.8, 0.8, 0.8, 0, new Particle.DustOptions(Color.fromRGB(150, 40, 255), 2.2f));
                for (LivingEntity le : enemiesNear(p, point, 2.8)) {
                    if (hit.add(le.getUniqueId())) {
                        le.damage(22, p);
                        le.setVelocity(dir.clone().multiply(1.4).setY(0.4));
                    }
                }
                if (end) {
                    w.spawnParticle(Particle.EXPLOSION_EMITTER, point, 3, 1, 1, 1, 0);
                    w.playSound(point, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
                    w.createExplosion(point, 3.0f, false, false, p);
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    // ---- 21. Soul reaper (active) -------------------------------------------
    private void soulWave(Player p) {
        int stacks = souls.getOrDefault(p.getUniqueId(), 0);
        if (stacks <= 0) {
            p.sendActionBar(Component.text("No souls collected yet - kill something!", NamedTextColor.GRAY));
            return;
        }
        if (!checkCooldown(p, "soul_reaper", 8)) return;
        souls.put(p.getUniqueId(), 0);
        p.removePotionEffect(PotionEffectType.STRENGTH);
        cast(p, "Soul Release x" + stacks, NamedTextColor.AQUA);
        p.playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.5f, 0.7f);
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.8f, 1.5f);
        World w = p.getWorld();
        Location start = p.getEyeLocation();
        Vector dir = start.getDirection().normalize();
        final double dmg = 3.0 * stacks + 2;
        new BukkitRunnable() {
            int step = 0;
            final Set<UUID> hit = new HashSet<>();
            @Override
            public void run() {
                step++;
                Location pt = start.clone().add(dir.clone().multiply(step * 1.5));
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, pt, 14, 0.5, 0.5, 0.5, 0.03);
                w.spawnParticle(Particle.SOUL, pt, 4, 0.4, 0.4, 0.4, 0.02);
                for (LivingEntity le : enemiesNear(p, pt, 1.8)) {
                    if (hit.add(le.getUniqueId())) le.damage(dmg, p);
                }
                if (step >= 18 || pt.getBlock().getType().isSolid()) cancel();
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    // ---- 22. Gravity gauntlet ---------------------------------------------
    private void gravityGauntlet(Player p) {
        if (!checkCooldown(p, "gravity_gauntlet", 8)) return;
        World w = p.getWorld();
        Location c = p.getLocation();
        List<LivingEntity> targets = enemiesNear(p, c, 8);
        if (p.isSneaking()) {
            cast(p, "Gravity Pull!", NamedTextColor.GOLD);
            p.playSound(c, Sound.ENTITY_SHULKER_TELEPORT, 1.2f, 0.4f);
            for (LivingEntity le : targets) {
                Vector v = c.toVector().subtract(le.getLocation().toVector());
                if (v.lengthSquared() > 0.5) le.setVelocity(v.normalize().multiply(1.3).setY(0.35));
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
            }
            new BukkitRunnable() {
                double r = 8;
                @Override
                public void run() {
                    ring(c.clone().add(0, 0.3, 0), r, Particle.REVERSE_PORTAL, (int) (r * 6));
                    r -= 1.0;
                    if (r < 1) cancel();
                }
            }.runTaskTimer(this, 0L, 1L);
            return;
        }
        cast(p, "Gravity Slam!", NamedTextColor.GOLD);
        p.playSound(c, Sound.ENTITY_SHULKER_TELEPORT, 1.2f, 0.6f);
        for (LivingEntity le : targets) {
            le.setVelocity(new Vector(0, 1.3, 0));
            le.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 24, 1));
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!le.isValid()) return;
                le.setVelocity(new Vector(0, -2.2, 0));
                le.damage(8, p);
                w.spawnParticle(Particle.EXPLOSION, le.getLocation(), 1, 0, 0, 0, 0);
                w.playSound(le.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);
            }, 26L);
        }
        dustRing(c.clone().add(0, 0.3, 0), 6, Color.fromRGB(255, 200, 40), 1.6f, 50);
    }

    // ---- 23. Railgun --------------------------------------------------------
    private void railgun(Player p) {
        if (!checkCooldown(p, "laser_railgun", 5)) return;
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        cast(p, "PLASMA!", NamedTextColor.RED);
        p.playSound(p.getLocation(), Sound.ENTITY_GUARDIAN_ATTACK, 1f, 1.8f);
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 2f);
        Particle.DustOptions red = new Particle.DustOptions(Color.RED, 1.5f);
        double max = wallDistance(p, 40);
        Set<UUID> hit = new HashSet<>();
        for (double d = 1; d <= max; d += 0.5) {
            Location pt = eye.clone().add(dir.clone().multiply(d));
            w.spawnParticle(Particle.DUST, pt, 3, 0.05, 0.05, 0.05, 0, red);
            if (((int) (d * 2)) % 4 == 0) w.spawnParticle(Particle.END_ROD, pt, 1, 0, 0, 0, 0);
            for (LivingEntity le : enemiesNear(p, pt, 0.8)) {
                if (hit.add(le.getUniqueId())) {
                    le.damage(11, p);
                    w.spawnParticle(Particle.CRIT, le.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.3);
                }
            }
        }
        Location end = eye.clone().add(dir.clone().multiply(max));
        w.spawnParticle(Particle.EXPLOSION, end, 1, 0, 0, 0, 0);
        p.setVelocity(p.getVelocity().add(dir.clone().multiply(-0.4)));
    }

    // ---- 25. Necromancer tome ------------------------------------------------
    private void necromancer(Player p) {
        if (!checkCooldown(p, "necromancer_tome", 15)) return;
        World w = p.getWorld();
        cast(p, "Rise, my guards!", NamedTextColor.DARK_GREEN);
        p.playSound(p.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1f, 0.8f);
        for (int i = 0; i < 3; i++) {
            Skeleton guard = (Skeleton) w.spawnEntity(p.getLocation().add(i - 1, 0, 1), EntityType.SKELETON);
            guards.put(guard.getUniqueId(), p.getUniqueId());
            guard.customName(Component.text(p.getName() + "'s Undead Guard", NamedTextColor.GREEN));
            guard.setCustomNameVisible(true);
            guard.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
            guard.getEquipment().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
            guard.getEquipment().setHelmetDropChance(0f);
            guard.getEquipment().setChestplateDropChance(0f);
            guard.getEquipment().setItemInMainHandDropChance(0f);
            guard.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 400, 0, true, false));
            w.spawnParticle(Particle.SOUL, guard.getLocation().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.05);
            new BukkitRunnable() {
                int t = 0;
                @Override
                public void run() {
                    if (!guard.isValid() || t >= 40) {
                        if (guard.isValid()) {
                            guard.getWorld().spawnParticle(Particle.SOUL, guard.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.05);
                            guard.remove();
                        }
                        guards.remove(guard.getUniqueId());
                        cancel();
                        return;
                    }
                    t++;
                    LivingEntity cur = guard.getTarget();
                    if (cur == null || !cur.isValid()) {
                        LivingEntity best = null;
                        double bestD = Double.MAX_VALUE;
                        for (Entity ent : guard.getNearbyEntities(14, 8, 14)) {
                            if (ent instanceof Monster || ent instanceof Player) {
                                if (!isEnemy(p, ent) || guards.containsKey(ent.getUniqueId())) continue;
                                double d = ent.getLocation().distanceSquared(guard.getLocation());
                                if (d < bestD) {
                                    bestD = d;
                                    best = (LivingEntity) ent;
                                }
                            }
                        }
                        guard.setTarget(best);
                    }
                }
            }.runTaskTimer(this, 10L, 10L);
        }
    }

    // =====================================================================
    //  ITEMS 26-50
    // =====================================================================
    private Vector flat(Player p) {
        Vector v = p.getLocation().getDirection().setY(0);
        if (v.lengthSquared() < 0.01) return new Vector(0, 0, 1);
        return v.normalize();
    }

    private Vector rightOf(Vector dir) {
        Vector cr = dir.clone().crossProduct(new Vector(0, 1, 0));
        return cr.lengthSquared() < 1e-6 ? new Vector(1, 0, 0) : cr.normalize();
    }

    private void healPlayer(Player o, double amount) {
        double max = Objects.requireNonNull(o.getAttribute(Attribute.MAX_HEALTH)).getValue();
        o.setHealth(Math.min(max, o.getHealth() + amount));
    }

    /** true if the entity is inside a forward cone of the player. */
    private boolean inCone(Player p, Entity ent, double range, double minDot) {
        Location eye = p.getEyeLocation();
        Vector to = ent.getLocation().add(0, 1, 0).toVector().subtract(eye.toVector());
        double len = to.length();
        if (len > range || len < 0.01) return len < 0.01;
        return to.normalize().dot(eye.getDirection().normalize()) > minDot;
    }

    // ---- 26. Tempest Lance --------------------------------------------------
    private void tempestLance(Player p) {
        if (!checkCooldown(p, "tempest_lance", 6)) return;
        World w = p.getWorld();
        Vector dir = p.getEyeLocation().getDirection().normalize();
        cast(p, "Tempest Rush!", NamedTextColor.AQUA);
        p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1.6f);
        Vector v = dir.clone().multiply(2.3);
        if (v.getY() < 0.15) v.setY(0.15);
        p.setVelocity(v);
        new BukkitRunnable() {
            int t = 0;
            final Set<UUID> hit = new HashSet<>();
            @Override
            public void run() {
                if (!p.isOnline() || t++ > 8) {
                    Location l = p.getLocation();
                    w.strikeLightningEffect(l);
                    for (LivingEntity le : enemiesNear(p, l, 3.5)) le.damage(5, p);
                    cancel();
                    return;
                }
                Location l = p.getLocation().add(0, 1, 0);
                w.spawnParticle(Particle.ELECTRIC_SPARK, l, 20, 0.5, 0.5, 0.5, 0.2);
                w.spawnParticle(Particle.CLOUD, l, 3, 0.2, 0.2, 0.2, 0.01);
                for (LivingEntity le : enemiesNear(p, l, 2.2)) {
                    if (hit.add(le.getUniqueId())) {
                        le.damage(9, p);
                        w.strikeLightningEffect(le.getLocation());
                    }
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    // ---- 27. Solar Blade ------------------------------------------------------
    private void solarFlare(Player p) {
        if (!checkCooldown(p, "solar_blade", 7)) return;
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        boolean day = w.getEnvironment() == World.Environment.NORMAL && w.getTime() < 13000;
        double dmg = day ? 13 : 9;
        cast(p, day ? "Solar Flare (daylight empowered)!" : "Solar Flare!", NamedTextColor.GOLD);
        p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.6f);
        Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 200, 40), 1.8f);
        double max = wallDistance(p, 14);
        Set<UUID> hit = new HashSet<>();
        for (double d = 1; d <= max; d += 0.5) {
            Location pt = eye.clone().add(dir.clone().multiply(d));
            double spread = d * 0.12;
            w.spawnParticle(Particle.DUST, pt, 4, spread, spread, spread, 0, gold);
            w.spawnParticle(Particle.FLAME, pt, 2, spread, spread, spread, 0.01);
            for (LivingEntity le : enemiesNear(p, pt, 1.0 + spread)) {
                if (hit.add(le.getUniqueId())) {
                    le.damage(dmg, p);
                    le.setFireTicks(100);
                    le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
                }
            }
        }
    }

    // ---- 28. Lunar Scythe -----------------------------------------------------
    private void lunarSlash(Player p) {
        if (!checkCooldown(p, "lunar_scythe", 6)) return;
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Vector right = rightOf(dir);
        cast(p, "Crescent Wave!", NamedTextColor.AQUA);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
        Particle.DustOptions moon = new Particle.DustOptions(Color.fromRGB(190, 230, 255), 1.6f);
        new BukkitRunnable() {
            int step = 0;
            final Set<UUID> hit = new HashSet<>();
            @Override
            public void run() {
                step++;
                Location c = eye.clone().add(dir.clone().multiply(step * 1.5));
                for (int a = -60; a <= 60; a += 15) {
                    double ang = Math.toRadians(a);
                    Vector off = right.clone().multiply(Math.sin(ang) * 2.6)
                            .add(dir.clone().multiply(-(1 - Math.cos(ang)) * 2.0));
                    w.spawnParticle(Particle.DUST, c.clone().add(off), 2, 0.05, 0.05, 0.05, 0, moon);
                }
                w.spawnParticle(Particle.END_ROD, c, 1, 0.2, 0.2, 0.2, 0.01);
                for (LivingEntity le : enemiesNear(p, c, 2.6)) {
                    if (hit.add(le.getUniqueId())) {
                        le.damage(10, p);
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                    }
                }
                if (step >= 12 || c.getBlock().getType().isSolid()) cancel();
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    // ---- 30. Magma Maul -------------------------------------------------------
    private void magmaFissure(Player p) {
        if (!checkCooldown(p, "magma_maul", 8)) return;
        World w = p.getWorld();
        final Vector d = flat(p);
        final Location base = p.getLocation();
        final Set<UUID> hit = new HashSet<>();
        cast(p, "Magma Fissure!", NamedTextColor.RED);
        p.playSound(base, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
        for (int i = 1; i <= 10; i++) {
            final int k = i;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Location pt = base.clone().add(d.clone().multiply(k * 1.5));
                pt.setY(base.getY());
                w.spawnParticle(Particle.LAVA, pt, 8, 0.4, 0.2, 0.4, 0);
                w.spawnParticle(Particle.FLAME, pt, 20, 0.5, 0.6, 0.5, 0.05);
                w.spawnParticle(Particle.EXPLOSION, pt, 1, 0, 0, 0, 0);
                w.playSound(pt, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.3f);
                for (LivingEntity le : enemiesNear(p, pt, 1.8)) {
                    if (hit.add(le.getUniqueId())) {
                        le.damage(7, p);
                        le.setFireTicks(100);
                        le.setVelocity(new Vector(0, 0.8, 0));
                    }
                }
            }, k * 2L);
        }
    }

    // ---- 31. Dragon Charge ----------------------------------------------------
    private void dragonCharge(Player p) {
        if (!checkCooldown(p, "dragon_charge", 8)) return;
        cast(p, "Dragon Breath!", NamedTextColor.DARK_RED);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.4f);
        DragonFireball df = p.launchProjectile(DragonFireball.class, p.getEyeLocation().getDirection().multiply(1.2));
        trail(df, Particle.WITCH);
    }

    // ---- 32. Rift Eye ---------------------------------------------------------
    private void riftEye(Player p) {
        World w = p.getWorld();
        if (p.isSneaking()) {
            Location mark = rifts.get(p.getUniqueId());
            if (mark == null) {
                p.sendActionBar(Component.text("No rift marked - right-click (not sneaking) to mark", NamedTextColor.GRAY));
                return;
            }
            if (!checkCooldown(p, "rift_tp", 10)) return;
            Location from = p.getLocation();
            w.spawnParticle(Particle.REVERSE_PORTAL, from.clone().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.2);
            p.teleport(mark);
            p.setFallDistance(0f);
            mark.getWorld().spawnParticle(Particle.REVERSE_PORTAL, mark.clone().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.2);
            p.playSound(mark, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);
            cast(p, "Rift Jump!", NamedTextColor.DARK_PURPLE);
        } else {
            if (!checkCooldown(p, "rift_mark", 2)) return;
            rifts.put(p.getUniqueId(), p.getLocation().clone());
            dustRing(p.getLocation().add(0, 0.2, 0), 1.4, Color.fromRGB(160, 90, 255), 1.4f, 24);
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.5f);
            cast(p, "Rift marked! Sneak + Right-Click to return.", NamedTextColor.LIGHT_PURPLE);
        }
    }

    // ---- 33. Staff of Life ----------------------------------------------------
    private void lifeStaff(Player p) {
        if (!checkCooldown(p, "life_staff", 12)) return;
        Location c = p.getLocation();
        cast(p, "Blessing of Life!", NamedTextColor.GREEN);
        p.playSound(c, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
        for (Player o : c.getWorld().getPlayers()) {
            if (o.getGameMode() == GameMode.SPECTATOR) continue;
            if (o.getLocation().distanceSquared(c) > 49) continue;
            healPlayer(o, 6);
            o.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
            o.getWorld().spawnParticle(Particle.HEART, o.getLocation().add(0, 2, 0), 6, 0.4, 0.3, 0.4, 0);
        }
        new BukkitRunnable() {
            double r = 0.5;
            @Override
            public void run() {
                dustRing(c.clone().add(0, 0.3, 0), r, Color.fromRGB(80, 230, 90), 1.4f, (int) (r * 8) + 6);
                r += 0.8;
                if (r > 7) cancel();
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    // ---- 37. Twin Fangs -------------------------------------------------------
    private void twinFangs(Player p) {
        if (!checkCooldown(p, "twin_fangs", 6)) return;
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity le : enemiesNear(p, p.getLocation(), 10)) {
            double d = le.getLocation().distanceSquared(p.getLocation());
            if (d < bd) {
                bd = d;
                best = le;
            }
        }
        if (best == null) {
            refund(p, "twin_fangs");
            p.sendActionBar(Component.text("No target in range", NamedTextColor.GRAY));
            return;
        }
        final LivingEntity target = best;
        World w = p.getWorld();
        Vector face = target.getLocation().getDirection().setY(0);
        if (face.lengthSquared() < 0.01) face = new Vector(0, 0, 1);
        Location dest = target.getLocation().add(face.normalize().multiply(-1.6));
        if (dest.getBlock().getType().isSolid() || dest.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
            dest = target.getLocation().clone();
        }
        w.spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.05);
        dest.setDirection(target.getLocation().toVector().subtract(dest.toVector()));
        p.teleport(dest);
        cast(p, "Shadow Fangs!", NamedTextColor.DARK_PURPLE);
        p.playSound(dest, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.4f);
        target.damage(6, p);
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (target.isValid() && p.isOnline()) {
                target.damage(6, p);
                w.spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0);
                w.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.2f);
            }
        }, 5L);
    }

    // ---- 38. Desert Scimitar --------------------------------------------------
    private void sandstorm(Player p) {
        if (!checkCooldown(p, "desert_scimitar", 14)) return;
        World w = p.getWorld();
        final Location center = p.getLocation().add(flat(p).multiply(7));
        final org.bukkit.block.data.BlockData sand = Material.SAND.createBlockData();
        cast(p, "Sandstorm!", NamedTextColor.GOLD);
        p.playSound(center, Sound.ENTITY_BREEZE_WIND_BURST, 1.2f, 0.7f);
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t++ >= 40 || !p.isOnline()) {
                    cancel();
                    return;
                }
                for (int k = 0; k < 4; k++) {
                    double a = t * 0.5 + k * Math.PI / 2;
                    for (int h = 0; h < 5; h++) {
                        double r = 1 + h * 0.6;
                        Location l = center.clone().add(Math.cos(a + h * 0.4) * r, h * 0.7, Math.sin(a + h * 0.4) * r);
                        w.spawnParticle(Particle.BLOCK, l, 2, 0.1, 0.1, 0.1, 0.05, sand);
                    }
                }
                for (LivingEntity le : enemiesNear(p, center, 5)) {
                    Vector pull = center.toVector().subtract(le.getLocation().toVector());
                    if (pull.lengthSquared() > 0.5) le.setVelocity(le.getVelocity().multiply(0.5).add(pull.normalize().multiply(0.3)));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0));
                    if (t % 10 == 0) le.damage(3, p);
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    // ---- 39. Tidal Heart ------------------------------------------------------
    private void tidalWave(Player p) {
        if (!checkCooldown(p, "tidal_heart", 9)) return;
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        cast(p, "Tidal Wave!", NamedTextColor.BLUE);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.5f, 0.7f);
        p.setFireTicks(0);
        p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 400, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 200, 0));
        for (double d = 1; d <= 9; d += 0.7) {
            Location pt = eye.clone().add(dir.clone().multiply(d));
            w.spawnParticle(Particle.SPLASH, pt, 25, d * 0.15, d * 0.15, d * 0.15, 0.1);
            w.spawnParticle(Particle.CLOUD, pt, 2, d * 0.1, d * 0.1, d * 0.1, 0.02);
        }
        for (LivingEntity le : enemiesNear(p, p.getLocation(), 10)) {
            if (!inCone(p, le, 9, 0.7)) continue;
            le.damage(5, p);
            le.setFireTicks(0);
            le.setVelocity(dir.clone().multiply(1.6).setY(0.35));
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
        }
    }

    // ---- 40. Gale Fan ---------------------------------------------------------
    private void galeFan(Player p) {
        if (!checkCooldown(p, "gale_fan", 5)) return;
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.5f, 1.0f);
        if (p.isSneaking()) {
            cast(p, "Updraft!", NamedTextColor.WHITE);
            p.setVelocity(new Vector(0, 1.4, 0).add(dir.clone().setY(0).multiply(0.4)));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 120, 0));
            w.spawnParticle(Particle.CLOUD, p.getLocation(), 40, 0.4, 0.1, 0.4, 0.15);
            return;
        }
        cast(p, "Gale Force!", NamedTextColor.WHITE);
        for (double d = 1; d <= 8; d += 0.8) {
            Location pt = eye.clone().add(dir.clone().multiply(d));
            w.spawnParticle(Particle.CLOUD, pt, 8, d * 0.12, d * 0.12, d * 0.12, 0.08);
        }
        for (LivingEntity le : enemiesNear(p, p.getLocation(), 9)) {
            if (!inCone(p, le, 8, 0.6)) continue;
            le.damage(2, p);
            le.setVelocity(dir.clone().multiply(2.0).setY(0.5));
        }
    }

    // ---- 41. Spirit Lantern ---------------------------------------------------
    private void spiritWisps(Player p) {
        if (!checkCooldown(p, "spirit_lantern", 10)) return;
        World w = p.getWorld();
        cast(p, "Wisps, hunt!", NamedTextColor.AQUA);
        p.playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.5f, 1.2f);
        List<Location> wisps = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            double a = i * 2 * Math.PI / 3;
            wisps.add(p.getEyeLocation().add(Math.cos(a), 0.2, Math.sin(a)));
        }
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t++ > 60 || !p.isOnline() || wisps.isEmpty()) {
                    cancel();
                    return;
                }
                Iterator<Location> it = wisps.iterator();
                while (it.hasNext()) {
                    Location l = it.next();
                    LivingEntity best = null;
                    double bd = Double.MAX_VALUE;
                    for (LivingEntity le : enemiesNear(p, l, 20)) {
                        double d = le.getLocation().distanceSquared(l);
                        if (d < bd) {
                            bd = d;
                            best = le;
                        }
                    }
                    if (best == null) {
                        Vector toOwner = p.getEyeLocation().toVector().subtract(l.toVector());
                        if (toOwner.lengthSquared() > 4) l.add(toOwner.normalize().multiply(0.4));
                    } else {
                        Vector v = best.getEyeLocation().toVector().subtract(l.toVector());
                        if (v.length() < 1.3) {
                            best.damage(7, p);
                            w.spawnParticle(Particle.SOUL, l, 25, 0.3, 0.3, 0.3, 0.1);
                            w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.8f);
                            it.remove();
                            continue;
                        }
                        l.add(v.normalize().multiply(0.9));
                    }
                    w.spawnParticle(Particle.SOUL_FIRE_FLAME, l, 6, 0.1, 0.1, 0.1, 0.01);
                    w.spawnParticle(Particle.END_ROD, l, 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    // ---- 42. Blood Chalice ----------------------------------------------------
    private void bloodChalice(Player p) {
        if (p.getHealth() <= 6) {
            p.sendActionBar(Component.text("Too weak to drink (need more than 3 hearts)", NamedTextColor.RED));
            return;
        }
        if (!checkCooldown(p, "blood_chalice", 25)) return;
        p.setHealth(p.getHealth() - 4);
        cast(p, "Blood Pact!", NamedTextColor.DARK_RED);
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 2));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 0));
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 0.6f);
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.8f, 1.5f);
        p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 60, 0.5, 0.9, 0.5, 0, new Particle.DustOptions(Color.fromRGB(170, 10, 25), 1.8f));
    }

    // ---- 43. Arcane Wand ------------------------------------------------------
    private void arcaneMissiles(Player p) {
        if (!checkCooldown(p, "arcane_wand", 6)) return;
        cast(p, "Arcane Barrage!", NamedTextColor.LIGHT_PURPLE);
        for (int i = 0; i < 5; i++) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!p.isOnline()) return;
                World w = p.getWorld();
                ThreadLocalRandom r = ThreadLocalRandom.current();
                Location eye = p.getEyeLocation();
                Vector d = eye.getDirection().add(new Vector(r.nextDouble(-0.05, 0.05), r.nextDouble(-0.05, 0.05), r.nextDouble(-0.05, 0.05))).normalize();
                RayTraceResult res = w.rayTrace(eye, d, 30, FluidCollisionMode.NEVER, true, 0.6, en -> isEnemy(p, en));
                Location end = res == null ? eye.clone().add(d.clone().multiply(30)) : res.getHitPosition().toLocation(w);
                line(eye.clone().add(0, -0.3, 0), end, Particle.WITCH);
                line(eye.clone().add(0, -0.3, 0), end, Particle.ENCHANT);
                w.playSound(p.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.8f, 1.6f);
                if (res != null && res.getHitEntity() instanceof LivingEntity le) {
                    le.damage(4, p);
                    w.spawnParticle(Particle.WITCH, end, 12, 0.2, 0.2, 0.2, 0.1);
                }
            }, i * 3L);
        }
    }

    // ---- 44. Hellfire Whip ----------------------------------------------------
    private void hellWhip(Player p) {
        if (!checkCooldown(p, "hellfire_whip", 3)) return;
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        RayTraceResult res = w.rayTrace(eye, dir, 9, FluidCollisionMode.NEVER, true, 1.0, en -> isEnemy(p, en));
        Location end = res == null ? eye.clone().add(dir.clone().multiply(9)) : res.getHitPosition().toLocation(w);
        Location hand = eye.clone().add(0, -0.4, 0).add(dir.clone().multiply(0.6));
        line(hand, end, Particle.FLAME);
        line(hand, end, Particle.CRIT);
        w.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.6f);
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
        cast(p, "Hellfire Lash!", NamedTextColor.RED);
        if (res != null && res.getHitEntity() instanceof LivingEntity le) {
            le.damage(8, p);
            le.setFireTicks(100);
            Vector pull = p.getLocation().toVector().subtract(le.getLocation().toVector());
            if (pull.lengthSquared() > 0.5) le.setVelocity(pull.normalize().multiply(1.2).setY(0.3));
            w.spawnParticle(Particle.LAVA, le.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0);
        }
    }

    // ---- 46. Executioner whirlwind -------------------------------------------
    private void whirlwind(Player p) {
        if (!checkCooldown(p, "executioner_axe", 10)) return;
        World w = p.getWorld();
        cast(p, "Whirlwind!", NamedTextColor.DARK_RED);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 0.5f);
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!p.isOnline() || t++ >= 5) {
                    cancel();
                    return;
                }
                Location c = p.getLocation().add(0, 1, 0);
                ring(c, 3.2, Particle.SWEEP_ATTACK, 8);
                ring(c.clone().add(0, 0.4, 0), 3.2, Particle.CRIT, 14);
                w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1f + t * 0.1f);
                for (LivingEntity le : enemiesNear(p, c, 3.5)) {
                    le.damage(3, p);
                    Vector out = le.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                    if (out.lengthSquared() > 0.01) le.setVelocity(out.normalize().multiply(0.6).setY(0.25));
                }
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    // ---- 48. Rocket Launcher --------------------------------------------------
    private void rocket(Player p) {
        if (!checkCooldown(p, "rocket_launcher", 5)) return;
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        cast(p, "ROCKET!", NamedTextColor.RED);
        p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.5f, 0.6f);
        final Location pos = eye.clone().add(dir.clone().multiply(1.5));
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                boolean boom = t++ > 40;
                pos.add(dir.clone().multiply(1.6));
                w.spawnParticle(Particle.FLAME, pos, 8, 0.1, 0.1, 0.1, 0.02);
                w.spawnParticle(Particle.SMOKE, pos, 4, 0.15, 0.15, 0.15, 0.01);
                if (pos.getBlock().getType().isSolid()) boom = true;
                if (!boom && !enemiesNear(p, pos, 1.4).isEmpty()) boom = true;
                if (boom) {
                    w.spawnParticle(Particle.EXPLOSION_EMITTER, pos, 2, 0.5, 0.5, 0.5, 0);
                    w.playSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.8f);
                    for (LivingEntity le : enemiesNear(p, pos, 4.5)) {
                        double d = le.getLocation().distance(pos);
                        le.damage(Math.max(5, 14 - d * 2), p);
                        Vector out = le.getLocation().toVector().subtract(pos.toVector());
                        if (out.lengthSquared() > 0.01) le.setVelocity(out.normalize().multiply(1.0).setY(0.5));
                    }
                    if (p.isOnline() && p.getLocation().distance(pos) < 4.5) {
                        Vector out = p.getLocation().toVector().subtract(pos.toVector());
                        if (out.lengthSquared() > 0.01) p.setVelocity(out.normalize().multiply(1.1).setY(0.6));
                        p.setFallDistance(0f);
                    }
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    // ---- 49. Venom Fang -------------------------------------------------------
    private void venomSpit(Player p) {
        if (!checkCooldown(p, "venom_fang", 5)) return;
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector base = eye.getDirection().normalize();
        cast(p, "Venom Spit!", NamedTextColor.GREEN);
        p.playSound(p.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1f, 0.5f);
        p.playSound(p.getLocation(), Sound.ENTITY_LLAMA_SPIT, 1f, 0.7f);
        Particle.DustOptions green = new Particle.DustOptions(Color.fromRGB(70, 220, 70), 1.4f);
        Set<UUID> hit = new HashSet<>();
        for (int i = -3; i <= 3; i++) {
            Vector d = base.clone().rotateAroundY(Math.toRadians(i * 8));
            RayTraceResult res = w.rayTrace(eye, d, 10, FluidCollisionMode.NEVER, true, 0.6, en -> isEnemy(p, en));
            Location end = res == null ? eye.clone().add(d.clone().multiply(10)) : res.getHitPosition().toLocation(w);
            for (double k = 1; k <= eye.distance(end); k += 0.6) {
                w.spawnParticle(Particle.DUST, eye.clone().add(d.clone().multiply(k)), 1, 0.05, 0.05, 0.05, 0, green);
            }
            if (res != null && res.getHitEntity() instanceof LivingEntity le) {
                le.damage(2.5, p);
                if (hit.add(le.getUniqueId())) {
                    le.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0));
                }
            }
        }
    }

    // ---- 50. Eclipse Orb ------------------------------------------------------
    private void eclipse(Player p) {
        if (!checkCooldown(p, "eclipse_orb", 60)) return;
        World w = p.getWorld();
        Location c = p.getLocation();
        cast(p, "E C L I P S E", NamedTextColor.DARK_PURPLE);
        p.playSound(c, Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.6f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 400, 2));
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
        for (LivingEntity le : enemiesNear(p, c, 14)) {
            le.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 160, 0));
            le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
            le.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
            le.damage(6, p);
        }
        new BukkitRunnable() {
            double r = 1;
            @Override
            public void run() {
                ring(c.clone().add(0, 0.3, 0), r, Particle.LARGE_SMOKE, (int) (r * 6));
                ring(c.clone().add(0, 1.5, 0), r, Particle.SMOKE, (int) (r * 6));
                dustRing(c.clone().add(0, 0.5, 0), r, Color.fromRGB(255, 220, 120), 1.5f, (int) (r * 3) + 6);
                r += 0.8;
                if (r > 14) cancel();
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    // =====================================================================
    //  PASSIVES / EVENTS
    // =====================================================================
    @EventHandler
    public void onGuardTarget(EntityTargetLivingEntityEvent e) {
        UUID owner = guards.get(e.getEntity().getUniqueId());
        if (owner == null || e.getTarget() == null) return;
        if (e.getTarget().getUniqueId().equals(owner) || guards.containsKey(e.getTarget().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        guards.remove(e.getEntity().getUniqueId());
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        if ("soul_reaper".equals(getItemId(killer.getInventory().getItemInMainHand()))) {
            int s = Math.min(5, souls.getOrDefault(killer.getUniqueId(), 0) + 1);
            souls.put(killer.getUniqueId(), s);
            killer.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 400, Math.min(3, s - 1)));
            killer.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 1));
            killer.getWorld().spawnParticle(Particle.SOUL, e.getEntity().getLocation().add(0, 1, 0), 25, 0.5, 0.5, 0.5, 0.05);
            killer.playSound(killer.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1f, 1f);
            killer.sendActionBar(Component.text("Souls: " + s + "/5", NamedTextColor.AQUA));
        }
        if ("midas_crown".equals(getItemId(killer.getInventory().getHelmet()))
                && !(e.getEntity() instanceof Player)) {
            e.getDrops().add(new ItemStack(Material.GOLD_INGOT, 1 + ThreadLocalRandom.current().nextInt(3)));
            e.setDroppedExp((int) (e.getDroppedExp() * 1.5));
            killer.getWorld().spawnParticle(Particle.END_ROD, e.getEntity().getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
        }
    }

    // arrows ---------------------------------------------------------------
    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        if (e.getEntity() instanceof Arrow arrow && arrow.getShooter() instanceof Player p) {
            String id = getItemId(p.getInventory().getItemInMainHand());
            if ("blackhole_bow".equals(id) && checkCooldown(p, id, 8)) {
                blackholeArrows.add(arrow.getUniqueId());
                arrow.setVelocity(arrow.getVelocity().multiply(1.2));
                trail(arrow, Particle.PORTAL);
            } else if ("thunder_crossbow".equals(id) && checkCooldown(p, id, 4)) {
                thunderArrows.add(arrow.getUniqueId());
                arrow.setVelocity(arrow.getVelocity().multiply(1.3));
                trail(arrow, Particle.ELECTRIC_SPARK);
            } else if ("frost_bow".equals(id) && checkCooldown(p, id, 3)) {
                frostArrows.add(arrow.getUniqueId());
                arrow.setVelocity(arrow.getVelocity().multiply(1.3));
                trail(arrow, Particle.SNOWFLAKE);
            } else if ("wither_bow".equals(id) && checkCooldown(p, id, 2)) {
                witherArrows.add(arrow.getUniqueId());
                arrow.setVelocity(arrow.getVelocity().multiply(1.4));
                arrow.setCritical(true);
                trail(arrow, Particle.SMOKE);
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        UUID id = e.getEntity().getUniqueId();
        if (thunderArrows.remove(id)) {
            Location loc = e.getEntity().getLocation();
            Player owner = e.getEntity().getShooter() instanceof Player sp ? sp : null;
            loc.getWorld().strikeLightningEffect(loc);
            loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.2f, 1.2f);
            loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 40, 1, 1, 1, 0.3);
            for (Entity ent : loc.getWorld().getNearbyEntities(loc, 3, 3, 3)) {
                if (ent instanceof LivingEntity le && !le.equals(owner) && !(le instanceof ArmorStand)) {
                    le.damage(8, owner);
                    le.setFireTicks(40);
                }
            }
        }
        if (frostArrows.remove(id)) {
            Location loc = e.getEntity().getLocation();
            Player owner = e.getEntity().getShooter() instanceof Player sp ? sp : null;
            loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.8f);
            loc.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 50, 1, 1, 1, 0.1);
            for (Entity ent : loc.getWorld().getNearbyEntities(loc, 3, 3, 3)) {
                if (ent instanceof LivingEntity le && !le.equals(owner) && !(le instanceof ArmorStand)) {
                    le.damage(4, owner);
                    le.setFreezeTicks(200);
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2));
                }
            }
        }
        if (witherArrows.remove(id)) {
            Location loc = e.getEntity().getLocation();
            loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 30, 0.5, 0.5, 0.5, 0.05);
            loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_SHOOT, 0.8f, 1.5f);
            if (e.getHitEntity() instanceof LivingEntity le) {
                le.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 120, 1));
            }
            for (Entity ent : loc.getWorld().getNearbyEntities(loc, 3, 3, 3)) {
                if (ent instanceof LivingEntity le && !(e.getEntity().getShooter() instanceof Entity s && s.equals(le))) {
                    le.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
                }
            }
        }
        if (blackholeArrows.remove(id)) {
            Location loc = e.getEntity().getLocation().clone();
            Player owner = e.getEntity().getShooter() instanceof Player sp ? sp : null;
            e.getEntity().remove();
            blackHole(loc, owner);
        }
    }

    private void blackHole(Location loc, Player owner) {
        World w = loc.getWorld();
        w.playSound(loc, Sound.ENTITY_ENDERMAN_SCREAM, 1.5f, 0.4f);
        w.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.5f);
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t++ >= 60) {
                    w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1, 0, 0, 0, 0);
                    w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
                    for (Entity ent : w.getNearbyEntities(loc, 6, 6, 6)) {
                        if (ent instanceof LivingEntity le && !le.equals(owner) && !(le instanceof ArmorStand)) {
                            le.damage(8, owner);
                            le.setVelocity(le.getLocation().toVector().subtract(loc.toVector()).setY(0.3).normalize().multiply(1.2));
                        }
                    }
                    cancel();
                    return;
                }
                w.spawnParticle(Particle.PORTAL, loc, 40, 2, 2, 2, 0.5);
                w.spawnParticle(Particle.SMOKE, loc, 15, 0.4, 0.4, 0.4, 0.02);
                ring(loc.clone().add(0, -0.5 + (t % 20) * 0.1, 0), 3 - (t % 20) * 0.1, Particle.REVERSE_PORTAL, 16);
                for (Entity ent : w.getNearbyEntities(loc, 9, 9, 9)) {
                    if (!(ent instanceof LivingEntity le) || le.equals(owner) || ent instanceof ArmorStand) continue;
                    if (ent instanceof Player op && (op.getGameMode() == GameMode.CREATIVE || op.getGameMode() == GameMode.SPECTATOR)) continue;
                    Vector pull = loc.toVector().subtract(le.getLocation().toVector());
                    if (pull.lengthSquared() < 0.5) continue;
                    le.setVelocity(le.getVelocity().multiply(0.5).add(pull.normalize().multiply(0.45)));
                    if (t % 10 == 0) le.damage(2, owner);
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    // melee / damage -------------------------------------------------------------
    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            String id = getItemId(p.getInventory().getItemInMainHand());
            if ("vampire_dagger".equals(id) && e.getEntity() instanceof LivingEntity) {
                double heal = Math.max(1.0, e.getFinalDamage() * 0.5);
                double maxHp = Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue();
                p.setHealth(Math.min(maxHp, p.getHealth() + heal));
                p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 3);
                p.getWorld().spawnParticle(Particle.DUST, e.getEntity().getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0, new Particle.DustOptions(Color.fromRGB(160, 0, 20), 1.3f));
            } else if ("poison_scythe".equals(id) && e.getEntity() instanceof LivingEntity target && checkCooldown(p, id, 4)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 0));
                target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0));
                AreaEffectCloud cloud = (AreaEffectCloud) target.getWorld().spawnEntity(target.getLocation(), EntityType.AREA_EFFECT_CLOUD);
                cloud.setRadius(2.8f);
                cloud.setDuration(100);
                cloud.setColor(Color.fromRGB(70, 210, 70));
                cloud.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 60, 1), true);
                cloud.setSource(p);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_SPLASH_POTION_BREAK, 1f, 0.6f);
            }
            if ("executioner_axe".equals(id) && e.getEntity() instanceof LivingEntity tgt) {
                var ma = tgt.getAttribute(Attribute.MAX_HEALTH);
                double mx = ma == null ? 20 : ma.getValue();
                if (tgt.getHealth() - e.getFinalDamage() <= mx * 0.3) {
                    e.setDamage(e.getDamage() * 1.6);
                    tgt.getWorld().spawnParticle(Particle.CRIT, tgt.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.5);
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.5f);
                }
            }
            // shadow cloak backstab
            if ("shadow_cloak".equals(getItemId(p.getInventory().getChestplate())) && p.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                e.setDamage(e.getDamage() * 1.75);
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
                p.getWorld().spawnParticle(Particle.CRIT, e.getEntity().getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.4);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.7f);
            }
            // guards join the fight
            for (Map.Entry<UUID, UUID> g : guards.entrySet()) {
                if (g.getValue().equals(p.getUniqueId())) {
                    Entity ge = Bukkit.getEntity(g.getKey());
                    if (ge instanceof Mob m && e.getEntity() instanceof LivingEntity le && isEnemy(p, le)) m.setTarget(le);
                }
            }
        }
        // shield of reflection
        if (e.getEntity() instanceof Player victim && victim.isBlocking()) {
            boolean hasShield = "reflection_shield".equals(getItemId(victim.getInventory().getItemInOffHand()))
                    || "reflection_shield".equals(getItemId(victim.getInventory().getItemInMainHand()));
            if (!hasShield) return;
            if (e.getDamager() instanceof Projectile proj) {
                e.setCancelled(true);
                proj.setShooter(victim);
                proj.setVelocity(victim.getEyeLocation().getDirection().multiply(2.0));
                victim.getWorld().spawnParticle(Particle.END_ROD, victim.getEyeLocation().add(victim.getEyeLocation().getDirection()), 15, 0.3, 0.3, 0.3, 0.1);
                victim.playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1.5f);
            } else if (e.getDamager() instanceof LivingEntity attacker && e.getDamage() >= 1.0) {
                attacker.damage(e.getDamage() * 0.5, victim);
                victim.getWorld().spawnParticle(Particle.CRIT, attacker.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.2);
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        // Hermes boots: no fall damage
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL
                && "hermes_boots".equals(getItemId(p.getInventory().getBoots()))) {
            e.setCancelled(true);
            return;
        }
        // Titan's Aegis: 20% damage reduction
        if ("titan_chestplate".equals(getItemId(p.getInventory().getChestplate()))
                && e.getCause() != EntityDamageEvent.DamageCause.VOID) {
            e.setDamage(e.getDamage() * 0.8);
        }
        // Phoenix rebirth
        if (e.getFinalDamage() >= p.getHealth()
                && ("phoenix_totem".equals(getItemId(p.getInventory().getItemInMainHand()))
                || "phoenix_totem".equals(getItemId(p.getInventory().getItemInOffHand())))
                && checkCooldown(p, "phoenix_rebirth", 90)) {
            e.setCancelled(true);
            double max = Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue();
            p.setHealth(max / 2);
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 2));
            p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 400, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 2));
            p.setFireTicks(0);
            p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1.5f, 0.8f);
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 80, 0.6, 1, 0.6, 0.5);
            p.sendMessage(Component.text("[MythicArsenal] The Phoenix Totem brings you back from death!", NamedTextColor.GOLD));
            flameBurst(p, 6);
        }
    }

    // 3x3 pickaxe -----------------------------------------------------------------------
    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (!"tnt_pickaxe".equals(getItemId(tool))) return;
        BlockFace face = p.getTargetBlockFace(8);
        if (face == null) return;
        Block center = e.getBlock();
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) continue;
                int dx, dy, dz;
                if (face.getModY() != 0) { dx = a; dy = 0; dz = b; }
                else if (face.getModX() != 0) { dx = 0; dy = a; dz = b; }
                else { dx = a; dy = b; dz = 0; }
                Block rel = center.getRelative(dx, dy, dz);
                Material m = rel.getType();
                if (m.isAir() || rel.isLiquid() || m.getHardness() < 0 || m.getHardness() > 50) continue;
                if (rel.getState() instanceof Container) continue;
                rel.breakNaturally(tool);
            }
        }
        center.getWorld().playSound(center.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.6f);
        center.getWorld().spawnParticle(Particle.EXPLOSION, center.getLocation().add(0.5, 0.5, 0.5), 1, 0, 0, 0, 0);
    }

    // grappling hook -----------------------------------------------------------------
    @EventHandler
    public void onGrapple(PlayerFishEvent e) {
        Player p = e.getPlayer();
        String id = getItemId(p.getInventory().getItemInMainHand());
        if (!"grappling_hook".equals(id)) return;
        PlayerFishEvent.State s = e.getState();
        if (s == PlayerFishEvent.State.REEL_IN || s == PlayerFishEvent.State.IN_GROUND || s == PlayerFishEvent.State.CAUGHT_ENTITY) {
            Location hookLoc = e.getHook().getLocation();
            double dist = hookLoc.distance(p.getLocation());
            if (dist < 3) return;
            if (!checkCooldown(p, id, 3)) return;
            Vector pull = hookLoc.toVector().subtract(p.getLocation().toVector()).normalize()
                    .multiply(Math.min(2.4, 0.9 + dist / 12.0));
            pull.setY(Math.max(0.45, pull.getY() + 0.35));
            p.setVelocity(pull);
            p.setFallDistance(0f);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, true, false));
            line(p.getEyeLocation(), hookLoc, Particle.CRIT);
            p.playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 1.2f);
            cast(p, "Swing!", NamedTextColor.GREEN);
        }
    }
}
