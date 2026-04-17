package org.bench245.mobcraft

import net.kyori.adventure.util.TriState
import org.bench245.mobcraft.command.*
import org.bench245.mobcraft.command.MobCraft.MobPowers.MobPowers
import org.bench245.mobcraft.data.PunishmentManager
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.command.CommandExecutor
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.*
import org.bukkit.event.player.*
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import java.util.*

class Mobcraft : JavaPlugin(), Listener, CommandExecutor {

    private var lootEnabled = false
    val mobsToPreventLoot = mutableSetOf<String>()
    val flyingPlayers = mutableSetOf<String>()
    val playerMobMap: MutableMap<UUID, String> = mutableMapOf()
    val takenMobs = mutableSetOf<String>()
    val cursed = mutableSetOf<String>()

    private val lootToggleCommand = LootToggle(this)
    private val flightCommand = Flight(this)
    private val setMob = SetMob(this)
    private val giveItem = GiveItem(this)
    private val mountCommand = MountCommand(this)
    private val dragonEggKey = NamespacedKey(this, "bound_dragon_egg")
    private val punishmentManager = PunishmentManager(this)
    private lateinit var enChest: EnChestCommand

    lateinit var mobPowers: MobPowers

    override fun onEnable() {

        Bukkit.getScheduler().runTaskTimer(this, Runnable {
            mobPowers.updateEnderDragonBeams()
        }, 1L, 1L)

        enChest = EnChestCommand(this)
        mobPowers = MobPowers(this)

        server.pluginManager.registerEvents(this, this)

        getCommand("loottoggle")?.apply {
            setExecutor(lootToggleCommand)
            tabCompleter = LootToggleCompleter(this@Mobcraft)
        }

        getCommand("flight")?.apply {
            setExecutor(flightCommand)
            tabCompleter = FlightCompleter(this@Mobcraft)
        }

        getCommand("giveitem")?.apply {
            setExecutor(giveItem)
            tabCompleter = GiveItemCompleter(this@Mobcraft)
        }

        getCommand("setmob")?.apply {
            setExecutor(setMob)
            tabCompleter = SetMobCompleter()
        }

        val reviveCommand = ReviveCommand(punishmentManager)
        getCommand("revive")?.apply {
            setExecutor(reviveCommand)
            tabCompleter = ReviveCommandCompleter(punishmentManager)
        }

        getCommand("enchest")?.apply {
            setExecutor(enChest)
            tabCompleter = EnChestTabCompleter()
        }

        getCommand("mount")?.apply {
            setExecutor(mountCommand)
            tabCompleter = MountCommandTabCompleter()
        }

        object : BukkitRunnable() {
            override fun run() {
                punishmentManager.checkTimers()
            }
        }.runTaskTimer(this, 0L, 200L)

        loadMobcraftConfig()
        logger.info("MobCraft has been enabled!")
    }

    override fun onDisable() {
        saveMobcraftConfig()
    }

    private fun loadMobcraftConfig() {
        mobsToPreventLoot.clear()
        mobsToPreventLoot.addAll(config.getStringList("mobsToPreventLoot"))

        flyingPlayers.clear()
        flyingPlayers.addAll(config.getStringList("flyingPlayers"))

        cursed.clear()
        cursed.addAll(config.getStringList("cursed"))

        playerMobMap.clear()
        val section = config.getConfigurationSection("playerMobMap") ?: return
        for (key in section.getKeys(false)) {
            val uuid = UUID.fromString(key)
            val value = section.getString(key) ?: continue
            playerMobMap[uuid] = value
        }
    }

    private fun saveMobcraftConfig() {
        config.set("mobsToPreventLoot", mobsToPreventLoot.toList())
        config.set("flyingPlayers", flyingPlayers.toList())
        config.set("cursed", cursed.toList())
        config.set("playerMobMap", null)

        for ((uuid, value) in playerMobMap) {
            config.set("playerMobMap.${uuid}", value)
        }
        saveConfig()
    }

    fun initializeMob(player: Player, mob: String) {
        mobPowers.resetPlayerState(player)
        when (mob.uppercase()) {
            "ENDERMAN"      -> mobPowers.onEndermanInitialize(player)
            "BLAZE"         -> mobPowers.onBlazeInitialize(player)
            "ENDER_DRAGON"  -> mobPowers.onEnderDragonInitialize(player)
            "GHAST"         -> mobPowers.onGhastInitialize(player)
            "TUFFGOLEM"     -> mobPowers.onTuffGolemInitialize(player)
            "AXOLOTL"       -> mobPowers.onAxolotlInitialize(player)
            "ELDER_GUARDIAN"-> mobPowers.onElderGuardianInitialize(player)
            "SHULKER"       -> mobPowers.onShulkerInitialize(player)
            "SILVERFISH"    -> mobPowers.onSilverfishInitialize(player)
        }
    }

    fun setPlayerMob(player: Player, mob: String) {
        playerMobMap[player.uniqueId] = mob
    }

    fun resetPlayerState(player: Player) {
        for (effect in player.activePotionEffects) {
            player.removePotionEffect(effect.type)
        }
        player.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
        player.getAttribute(Attribute.ARMOR_TOUGHNESS)?.baseValue = 0.0
        player.getAttribute(Attribute.ARMOR)?.baseValue = 0.0
        player.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue = 1.0
        player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
        player.health = player.getAttribute(Attribute.MAX_HEALTH)?.baseValue ?: 20.0
        player.allowFlight = false
        player.isFlying = false
        player.flySpeed = 0.1f
        this.flyingPlayers.remove(player.name)
        player.fireTicks = 0
        player.remainingAir = player.maximumAir
        player.fallDistance = 0f
        player.velocity = player.velocity.zero()
        player.world.entities
            .filterIsInstance<EnderCrystal>()
            .forEach { crystal ->
                if (crystal.beamTarget != null) crystal.beamTarget = null
            }
    }

    fun enableFlight(player: Player?) {
        if (player == null) return
        player.allowFlight = true
        player.isFlying = true
        player.setFlyingFallDamage(TriState.TRUE)
        flyingPlayers.add(player.name)
    }

    fun setLootEnabled(enabled: Boolean) {
        lootEnabled = enabled
    }

    // ─── Event Handlers ───────────────────────────────────────────────────────

    @EventHandler
    fun curseHandler(event: EntityPotionEffectEvent) {
        val player = event.entity as? Player ?: return
        val mob = playerMobMap[player.uniqueId]?.uppercase() ?: "NONE"
        if (mob == "NONE") return
        if (event.modifiedType != PotionEffectType.UNLUCK) return
        when (event.action) {
            EntityPotionEffectEvent.Action.ADDED -> {
                when (mob) {
                    "ELDER_GUARDIAN" -> mobPowers.elderGuardianCurse(player)
                    "BLAZE"          -> mobPowers.blazeCurse(player)
                    "ENDERMAN"       -> mobPowers.endermanCurse(player)
                    "ENDER_DRAGON"   -> mobPowers.enderDragonCurse(player)
                    "TUFFGOLEM"      -> mobPowers.tuffGolemCurse(player)
                    "GHAST"          -> mobPowers.ghastCurse(player)
                    "AXOLOTL"        -> mobPowers.axolotlCurse(player)
                }
            }
            EntityPotionEffectEvent.Action.REMOVED -> {
                player.sendMessage(event.action.name)
                when (mob) {
                    "ELDER_GUARDIAN" -> mobPowers.elderGuardianUncurse(player)
                    "BLAZE"          -> mobPowers.blazeUncurse(player)
                    "ENDERMAN"       -> mobPowers.endermanUncurse(player)
                    "ENDER_DRAGON"   -> mobPowers.enderDragonUncurse(player)
                    "TUFFGOLEM"      -> mobPowers.tuffGolemUncurse(player)
                    "GHAST"          -> mobPowers.ghastUncurse(player)
                    "AXOLOTL"        -> mobPowers.axolotlUncurse(player)
                }
            }
            EntityPotionEffectEvent.Action.CLEARED -> {
                event.isCancelled = true
                return
            }
            else -> {}
        }
    }

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        if (!lootEnabled && mobsToPreventLoot.contains(event.entity.type.name)) {
            event.drops.clear()
        }
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val mob = playerMobMap[player.uniqueId]?.uppercase() ?: "NONE"

        Bukkit.getScheduler().runTaskLater(this, Runnable {
            if (mob in listOf("BLAZE", "GHAST", "ENDER_DRAGON")) enableFlight(player)
            when (mob) {
                "ENDERMAN"    -> mobPowers.onEndermanRespawn(event)
                "ENDER_DRAGON"-> mobPowers.applyDragonEffects(player)
                "TUFFGOLEM"   -> mobPowers.applyTuffGolemEffects(player)
                "GHAST"       -> mobPowers.applyGhastEffects(player)
                "AXOLOTL"     -> mobPowers.applyAxolotlEffects(player)
            }
        }, 1L)
    }

    @EventHandler
    fun onGameModeChange(event: PlayerGameModeChangeEvent) {
        val player = event.player
        mobPowers.resetPlayerState(player)
        val mob = playerMobMap[player.uniqueId]?.uppercase() ?: return

        Bukkit.getScheduler().runTaskLater(this, Runnable {
            when (player.gameMode) {
                GameMode.CREATIVE,
                GameMode.SPECTATOR -> {
                    enableFlight(player)
                    player.flySpeed = 0.1f
                }
                else -> {
                    when (mob) {
                        "BLAZE" -> {
                            enableFlight(player)
                            player.flySpeed = 0.03f
                            mobPowers.applyBlazeEffects(player)
                        }
                        "GHAST" -> {
                            enableFlight(player)
                            player.flySpeed = 0.09f
                            mobPowers.applyGhastEffects(player)
                        }
                        "ENDER_DRAGON" -> {
                            enableFlight(player)
                            player.flySpeed = 0.1f
                            mobPowers.applyDragonEffects(player)
                        }
                        "ENDERMAN" -> {
                            mobPowers.applyEndermanSpeed(player)
                            player.allowFlight = false
                            player.isFlying = false
                        }
                        "TUFFGOLEM" -> {
                            mobPowers.applyTuffGolemEffects(player)
                            player.allowFlight = false
                            player.isFlying = false
                        }
                        "AXOLOTL" -> {
                            mobPowers.applyAxolotlEffects(player)
                            player.allowFlight = false
                            player.isFlying = false
                        }
                    }
                }
            }
        }, 1L)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val mob = playerMobMap[player.uniqueId]?.uppercase() ?: return
        when (mob) {
            "BLAZE"         -> mobPowers.applyBlazeEffects(player)
            "GHAST"         -> mobPowers.applyGhastEffects(player)
            "ENDER_DRAGON"  -> mobPowers.applyDragonEffects(player)
            "ENDERMAN"      -> mobPowers.applyEndermanSpeed(player)
            "TUFFGOLEM"     -> mobPowers.applyTuffGolemEffects(player)
            "AXOLOTL"       -> mobPowers.applyAxolotlEffects(player)
        }
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val location = player.location
        val damageSource = player.lastDamageCause
        punishmentManager.onPlayerDeath(event)
        mobPowers.onDragonEggDeath(event)

        val contextBuilder = org.bukkit.loot.LootContext.Builder(location).lootedEntity(player)
        enableFlight(player)

        if (damageSource is EntityDamageByEntityEvent) {
            val killer = damageSource.damager
            if (killer is Player) {
                logger.info("Killer: $killer")
                contextBuilder.killer(killer)
                Bukkit.getOnlinePlayers().forEach { p ->
                    p.playSound(player.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.0f)
                }
            }
        }

        val mob = playerMobMap[player.uniqueId]?.uppercase() ?: "NONE"
        when (mob) {
            "TUFFGOLEM"     -> player.world.dropItemNaturally(location, ItemStack(Material.TUFF, 64))
            "SKELETON"      -> player.world.dropItemNaturally(location, ItemStack(Material.BONE, 64))
            "ENDERMAN"      -> repeat(4) {
                player.world.dropItemNaturally(location, ItemStack(Material.ENDER_PEARL, 64))
            }
            "ENDER_DRAGON"  -> player.world.dropItemNaturally(location, ItemStack(Material.DRAGON_BREATH, 64))
            "BLAZE"         -> {
                player.world.dropItemNaturally(location, ItemStack(Material.BLAZE_ROD, 64))
                player.world.dropItemNaturally(location, ItemStack(Material.BLAZE_ROD, 32))
            }
            "GHAST"         -> {
                repeat(2) { player.world.dropItemNaturally(location, ItemStack(Material.GHAST_TEAR, 64)) }
                player.world.dropItemNaturally(location, ItemStack(Material.GUNPOWDER, 64))
            }
            "GUARDIAN", "ELDER_GUARDIAN" -> {
                player.world.dropItemNaturally(location, ItemStack(Material.SPONGE, 64))
                player.world.dropItemNaturally(location, ItemStack(Material.PRISMARINE, 64))
            }
            "SHULKER"       -> {
                if (player.world.environment != World.Environment.THE_END) {
                    if (Random().nextInt(5000) == 0) {
                        player.world.dropItemNaturally(location, ItemStack(Material.ELYTRA))
                    }
                }
            }
            "SILVERFISH"    -> {
                mobPowers.onSilverfishCleanup(player)
                player.world.dropItemNaturally(location, ItemStack(Material.IRON_NUGGET, 3))
            }
            "NONE"          -> {
                val orb = player.world.spawnEntity(location, EntityType.EXPERIENCE_ORB) as ExperienceOrb
                orb.experience = 1395
            }
        }
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val mob = playerMobMap[player.uniqueId]?.uppercase() ?: return
        when (mob) {
            "BLAZE"         -> mobPowers.onBlazeRodUse(event)
            "GHAST"         -> mobPowers.onGhastFireball(event)
            "ENDER_DRAGON"  -> { mobPowers.onEnderDragonRightClick(event); mobPowers.onEnderDragonBreak(event) }
            "ENDERMAN"      -> mobPowers.onEndermanRightClick(event)
            "ELDER_GUARDIAN"-> mobPowers.onElderGuardianRightClick(event)
            "TUFFGOLEM"     -> mobPowers.onTuffGolemLeftClick(event)
            "SHULKER"       -> mobPowers.onShulkerRightClick(event)
        }
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        when (playerMobMap[player.uniqueId]?.uppercase()) {
            "SHULKER" -> mobPowers.onShulkerDamage(event)
        }
    }

    @EventHandler
    fun onEnderDragonPotionEffect(event: EntityPotionEffectEvent) {
        mobPowers.onEnderDragonPotionEffect(event)
    }

    @EventHandler
    fun onDragonEggDrop(event: PlayerDropItemEvent) {
        val item = event.itemDrop.itemStack
        val meta = item.itemMeta ?: return
        mobPowers.onDragonEggDrop(event)
        if (meta.persistentDataContainer.has(dragonEggKey, PersistentDataType.BYTE)) {
            event.isCancelled = true
            event.player.sendMessage("§cYou cannot drop a Dragon Egg.")
        }
    }

    @EventHandler
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        mobPowers.onDragonEggPickup(event)
    }

    @EventHandler
    fun onPlayerDamage(event: EntityDamageByEntityEvent) {
        val player = event.entity as? Player ?: return
        val mob = playerMobMap[player.uniqueId]?.uppercase() ?: return
        when (mob) {
            "BLAZE" -> {
                if (event.damager is Blaze) event.isCancelled = true
                mobPowers.onBlazeProjectileHit(event)
            }
            "GHAST" -> {
                if (event.damager is Ghast) event.isCancelled = true
                mobPowers.onGhastFireballHit(event)
            }
            "SKELETON"      -> { if (event.damager is Skeleton) event.isCancelled = true }
            "ENDERMAN"      -> mobPowers.onEndermanProjectileDamage(event)
            "ENDER_DRAGON"  -> mobPowers.onDragonDamage(event)
            "ELDER_GUARDIAN"-> { if (event.damager is ElderGuardian) event.isCancelled = true }
            "SHULKER"       -> { if (event.damager is Shulker) event.isCancelled = true }
            "SILVERFISH"    -> {
                if (event.damager is org.bukkit.entity.Silverfish) event.isCancelled = true
                mobPowers.onSilverfishBaneDamage(event)
                mobPowers.onSilverfishArthropodHit(event)
            }
        }
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val mob = playerMobMap[player.uniqueId]?.uppercase() ?: return
        when (mob) {
            "BLAZE" -> mobPowers.onBlazeMove(event)
            "GHAST" -> mobPowers.onGhastMove(event)
        }
    }

    @EventHandler
    fun onEntityTarget(event: EntityTargetEvent) {
        val target = event.target
        val entity = event.entity
        if (target !is Player) return
        val playerMob = playerMobMap[target.uniqueId]?.uppercase() ?: return
        if (entity.type.name == playerMob) event.isCancelled = true
    }

    @EventHandler
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val damaged = event.entity
        val damager = event.damager
        if (damaged !is Player) return
        val playerMob = playerMobMap[damaged.uniqueId]?.uppercase() ?: return
        if (damager is LivingEntity && damager !is Player) {
            if (damager.type.name == playerMob) {
                event.isCancelled = true
                return
            }
        }
        if (damager is Projectile) {
            val shooter = damager.shooter
            if (shooter is LivingEntity && shooter !is Player && shooter.type.name == playerMob) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onPotionEffect(event: EntityPotionEffectEvent) {
        val player = event.entity as? Player ?: return
        if (playerMobMap[player.uniqueId]?.uppercase() != "SILVERFISH") return
        if (event.modifiedType == org.bukkit.potion.PotionEffectType.INFESTED) {
            event.isCancelled = true
            player.sendMessage("§cSilverfish cannot be infested.")
        }
    }
}
