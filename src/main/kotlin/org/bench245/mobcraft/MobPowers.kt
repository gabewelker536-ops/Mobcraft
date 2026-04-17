package org.bench245.mobcraft.command.MobCraft.MobPowers

import org.bench245.mobcraft.Mobcraft
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.entity.*
import org.bukkit.event.entity.*
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import java.util.*
import kotlin.random.Random

class MobPowers(private val plugin: Mobcraft) {

    val cursed = plugin.cursed

    private val dragonAbilityCooldown = mutableSetOf<Player>()

    private val dragonEggKey = NamespacedKey(plugin, "bound_dragon_egg")

    private val eggOwners = WeakHashMap<Item, UUID>()

    private val elderGuardianFatigueCooldown = mutableSetOf<Player>()

    private val tuffGolemCooldown = mutableSetOf<UUID>()

    fun resetPlayerState(player: Player) {

        player.walkSpeed = 0.2f
        player.flySpeed = 0.1f

        player.allowFlight = false
        player.isFlying = false

        player.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
        player.getAttribute(Attribute.ARMOR)?.baseValue = 0.0
        player.getAttribute(Attribute.ARMOR_TOUGHNESS)?.baseValue = 0.0
        player.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue = 1.0
        player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0
        player.getAttribute(Attribute.SCALE)?.baseValue = 1.0

        // Clamp health
        if (player.health > 20.0) {
            player.health = 20.0
        }
    }

    fun disableFlight(player: Player) {
        player.allowFlight = false
        player.isFlying = false
    }

    // ----------------------- BLAZE -------------------------------

    private val blazeCombatActive = mutableSetOf<UUID>()
    private val blazeCooldown = mutableSetOf<UUID>()

    fun onBlazeInitialize(player: Player) {
        plugin.mobsToPreventLoot.add("BLAZE")
        plugin.enableFlight(player)
        player.flySpeed = 0.03F
        applyBlazeEffects(player)
    }

    fun blazeCurse(player: Player) {
        cursed.add(player.name)
        disableFlight(player)
        blazeCombatActive.remove(player.uniqueId)
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE)
        player.removePotionEffect(PotionEffectType.STRENGTH)
    }

    fun blazeUncurse(player: Player) {
        cursed.remove(player.name)
        applyBlazeEffects(player)
    }

    fun applyBlazeEffects(player: Player) {
        if (cursed.contains(player.name)) return
        player.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, -1, 0, false, false, false))
        plugin.enableFlight(player)
        player.flySpeed = 0.03F
    }

    fun onBlazeMove(event: PlayerMoveEvent) {
        val player = event.player
        if (cursed.contains(player.name)) return
        player.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, -1, 0, false, false, false))
    }

    fun onBlazeRodUse(event: PlayerInteractEvent) {
        val player = event.player
        if (cursed.contains(player.name)) return
        if (player.inventory.itemInMainHand.type != Material.BLAZE_ROD) return

        if (event.action.name.contains("LEFT_CLICK")) {
            if (blazeCombatActive.contains(player.uniqueId)) return
            if (blazeCooldown.contains(player.uniqueId)) {
                player.sendMessage("§cYou must wait before entering combat phase again!")
                return
            }
            enterBlazeCombatPhase(player)
            return
        }

        if (event.action.name.contains("RIGHT_CLICK")) {
            if (!blazeCombatActive.contains(player.uniqueId)) {
                player.sendMessage("§cYou can only shoot fireballs during combat phase!")
                return
            }
            val fireball = player.launchProjectile(SmallFireball::class.java)
            fireball.yield = 0f
            player.world.playSound(player.location, Sound.ENTITY_BLAZE_SHOOT, 1f, 1f)
        }
    }

    private fun enterBlazeCombatPhase(player: Player) {
        if (blazeCombatActive.contains(player.uniqueId)) return
        if (blazeCooldown.contains(player.uniqueId)) {
            player.sendMessage("§cYou must wait before entering combat phase again!")
            return
        }

        blazeCombatActive.add(player.uniqueId)
        blazeCooldown.add(player.uniqueId)
        player.sendMessage("§6You enter Blaze combat phase!")
        player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 200, 1, false, false, false))

        val particleTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!blazeCombatActive.contains(player.uniqueId)) return@Runnable
            player.world.spawnParticle(Particle.SMOKE, player.location.clone().add(0.0, 1.0, 0.0), 20, 0.4, 0.6, 0.4, 0.01)
            player.world.spawnParticle(Particle.FLAME, player.location.clone().add(0.0, 1.0, 0.0), 8, 0.3, 0.4, 0.3, 0.0)
        }, 0L, 1L)

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            blazeCombatActive.remove(player.uniqueId)
            player.removePotionEffect(PotionEffectType.STRENGTH)
            particleTask.cancel()
            player.sendMessage("§7Combat phase ended. Cooling down...")
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                blazeCooldown.remove(player.uniqueId)
                player.sendMessage("§aYou may enter combat phase again.")
            }, 200L)
        }, 200L)
    }

    fun onBlazeProjectileHit(event: EntityDamageByEntityEvent) {
        val player = event.entity as? Player ?: return
        val damager = event.damager
        if (!blazeCombatActive.contains(player.uniqueId)) return
        if (damager is Projectile) {
            event.isCancelled = true
            damager.velocity = damager.velocity.multiply(-1)
            player.world.playSound(player.location, Sound.ENTITY_BLAZE_HURT, 1f, 1.2f)
        }
    }

    // ----------------------- ENDERMAN -------------------------------

    private val reflectedKey = NamespacedKey(plugin, "enderman_reflected")
    private val enderPearlCooldowns = mutableMapOf<Player, Long>()

    fun onEndermanInitialize(player: Player) {
        plugin.mobsToPreventLoot.add("ENDERMAN")
        applyEndermanSpeed(player)
    }

    fun endermanCurse(player: Player) {
        cursed.add(player.name)
        player.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
    }

    fun endermanUncurse(player: Player) {
        cursed.remove(player.name)
        applyEndermanSpeed(player)
    }

    fun applyEndermanSpeed(player: Player) {
        if (cursed.contains(player.name)) return
        player.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.14
    }

    fun onEndermanRespawn(event: PlayerRespawnEvent) = applyEndermanSpeed(event.player)

    fun onEndermanRightClick(event: PlayerInteractEvent) {
        val player = event.player
        if (cursed.contains(player.name)) return
        if (!event.action.name.contains("RIGHT_CLICK")) return
        if (player.inventory.itemInMainHand.type != Material.ENDER_PEARL) return
        event.isCancelled = true

        val now = System.currentTimeMillis()
        val lastUse = enderPearlCooldowns[player] ?: 0
        if (now - lastUse < 1000) return
        enderPearlCooldowns[player] = now

        val targetBlock = player.getTargetBlockExact(128) ?: return
        val safeLoc = targetBlock.location.add(0.0, 1.0, 0.0)

        repeat(7) {
            val feet = safeLoc.block
            val head = safeLoc.clone().add(0.0, 1.0, 0.0).block
            if (!feet.type.isSolid && !head.type.isSolid) return@repeat
            safeLoc.add(0.0, 1.0, 0.0)
        }

        if (Random.nextInt(0, 100) >= 95) player.world.spawn(player.location, Endermite::class.java)

        safeLoc.yaw = player.location.yaw
        safeLoc.pitch = player.location.pitch
        player.fallDistance = 0f
        player.teleport(safeLoc)
        player.world.playSound(safeLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f)
        player.world.spawnParticle(Particle.PORTAL, safeLoc, 30, 0.5, 1.0, 0.5, 0.2)
    }

    fun onEndermanProjectileDamage(event: EntityDamageByEntityEvent) {
        val player = event.entity as? Player ?: return
        if (cursed.contains(player.name)) return

        if (event.damager is Enderman) event.isCancelled = true

        if (event.damager is Projectile) {
            event.isCancelled = true
            event.damager.remove()
            player.world.spawnParticle(Particle.CRIT, player.location.add(0.0, 1.0, 0.0), 10, 0.2, 0.2, 0.2, 0.05)
            player.world.playSound(player.location, Sound.ENTITY_ITEM_BREAK, 1f, 1f)
        }

        val projectile = event.damager as? Projectile ?: return
        if (projectile !is Arrow) return
        if (projectile.persistentDataContainer.has(reflectedKey, PersistentDataType.BYTE)) return
        if (projectile.shooter is Player && (projectile.shooter as Player).name == "MercilessRattler") return

        event.isCancelled = true
        projectile.persistentDataContainer.set(reflectedKey, PersistentDataType.BYTE, 1)
        projectile.velocity = projectile.velocity.multiply(-1)
        player.world.spawnParticle(Particle.CRIT, player.location.add(0.0, 1.0, 0.0), 15, 0.3, 0.3, 0.3, 0.05)
        player.world.playSound(player.location, Sound.ITEM_SHIELD_BLOCK, 1f, 1.3f)
    }

    // ----------------------- ELDER GUARDIAN -----------------------

    fun onElderGuardianInitialize(player: Player) {
        plugin.mobsToPreventLoot.add("ELDER_GUARDIAN")
        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, -1, 0, false, false, false))
        player.addPotionEffect(PotionEffect(PotionEffectType.WATER_BREATHING, -1, 0, false, false, false))
    }

    fun elderGuardianCurse(player: Player) {
        cursed.add(player.name)
        player.removePotionEffect(PotionEffectType.RESISTANCE)
        player.removePotionEffect(PotionEffectType.WATER_BREATHING)
    }

    fun elderGuardianUncurse(player: Player) {
        cursed.remove(player.name)
        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, -1, 0, false, false, false))
        player.addPotionEffect(PotionEffect(PotionEffectType.WATER_BREATHING, -1, 0, false, false, false))
    }

    fun onElderGuardianRightClick(event: PlayerInteractEvent) {
        val player = event.player
        if (cursed.contains(player.name)) return
        if (!event.action.name.contains("RIGHT_CLICK")) return
        val item = player.inventory.itemInMainHand.type
        when (item) {
            Material.PRISMARINE -> applyElderGuardianFatigue(player)
            Material.SPONGE -> fireElderGuardianLaser(player)
            else -> return
        }
        event.isCancelled = true
    }

    fun applyElderGuardianFatigue(player: Player) {
        if (!player.location.block.isLiquid) {
            player.sendMessage("§cYou must be underwater to use this ability.")
            return
        }
        if (elderGuardianFatigueCooldown.contains(player)) {
            player.sendMessage("§cMining Fatigue is recharging!")
            return
        }
        elderGuardianFatigueCooldown.add(player)
        player.world.getNearbyPlayers(player.location, 30.0).forEach { target ->
            if (target == player) return@forEach
            target.addPotionEffect(PotionEffect(PotionEffectType.MINING_FATIGUE, 20 * 8, 2))
        }
        player.world.playSound(player.location, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.5f, 1f)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            elderGuardianFatigueCooldown.remove(player)
            player.sendMessage("§aMining Fatigue ready!")
        }, 20L * 30)
    }

    fun fireElderGuardianLaser(player: Player) {
        val target = player.getTargetEntity(25) as? Player ?: return
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (!target.isOnline || target.isDead) return@Runnable
            target.damage(10.0, player)
            target.world.playSound(target.location, Sound.ENTITY_ELDER_GUARDIAN_HURT, 1.3f, 0.8f)
        }, 40L)
    }

    // ----------------------- ENDER DRAGON -------------------------

    fun onEnderDragonInitialize(player: Player) {
        plugin.mobsToPreventLoot.add("ENDER_DRAGON")
        plugin.enableFlight(player)
        player.flySpeed = 0.1F
        player.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, -1, 0, false, false, false))
        player.addPotionEffect(PotionEffect(PotionEffectType.WATER_BREATHING, -1, 0, false, false, false))
    }

    fun enderDragonCurse(player: Player) {
        cursed.add(player.name)
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE)
        player.removePotionEffect(PotionEffectType.WATER_BREATHING)
        disableFlight(player)
    }

    fun enderDragonUncurse(player: Player) {
        cursed.remove(player.name)
        applyDragonEffects(player)
    }

    fun onDragonDamage(event: EntityDamageByEntityEvent) {
        if (cursed.contains(event.entity.name)) return
        if (event.damager is EnderDragon) event.isCancelled = true
        if (event.damager is AreaEffectCloud) {
            val cloud = event.damager as AreaEffectCloud
            if (cloud.particle == Particle.DRAGON_BREATH) event.isCancelled = true
        }
    }

    fun applyDragonEffects(player: Player) {
        if (cursed.contains(player.name)) return
        player.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, -1, 0, false, false, false))
        player.addPotionEffect(PotionEffect(PotionEffectType.WATER_BREATHING, -1, 0, false, false, false))
        plugin.enableFlight(player)
        player.flySpeed = 0.1F
    }

    fun onEnderDragonPotionEffect(event: EntityPotionEffectEvent) {
        val player = event.entity as? Player ?: return
        if (cursed.contains(player.name)) return
        if (plugin.playerMobMap[player.uniqueId]?.uppercase() != "ENDER_DRAGON") return
        when (event.modifiedType) {
            PotionEffectType.POISON,
            PotionEffectType.WITHER,
            PotionEffectType.BLINDNESS,
            PotionEffectType.SLOWNESS,
            PotionEffectType.MINING_FATIGUE,
            PotionEffectType.INSTANT_DAMAGE,
            PotionEffectType.WEAKNESS -> {
                when (event.action) {
                    EntityPotionEffectEvent.Action.ADDED -> event.isCancelled
                    else -> {}
                }
            }
            else -> {}
        }
    }

    fun onDragonEggDrop(event: PlayerDropItemEvent) {
        val item = event.itemDrop.itemStack
        val meta = item.itemMeta ?: return
        if (meta.persistentDataContainer.has(dragonEggKey, PersistentDataType.BYTE)) {
            event.isCancelled = true
            event.player.sendMessage("§cYou cannot drop a Dragon Egg.")
        }
    }

    fun onDragonEggDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val iterator = event.drops.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            val meta = item.itemMeta ?: continue
            if (meta.persistentDataContainer.has(dragonEggKey, PersistentDataType.BYTE)) {
                iterator.remove()
                val dropped = player.world.dropItemNaturally(player.location, item)
                eggOwners[dropped] = player.uniqueId
            }
        }
    }

    fun onDragonEggPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        val item = event.item
        if (!eggOwners.containsKey(item)) return
        val ownerId = eggOwners[item] ?: return
        if (player.uniqueId != ownerId) {
            event.isCancelled = true
            player.sendMessage("§cYou cannot pick up this Dragon Egg.")
        } else {
            eggOwners.remove(item)
        }
    }

    fun onEnderDragonRightClick(event: PlayerInteractEvent) {
        val player = event.player
        if (cursed.contains(player.name)) return
        if (player.inventory.itemInMainHand.type != Material.DRAGON_BREATH) return
        if (!event.action.name.contains("RIGHT_CLICK")) return
        if (dragonAbilityCooldown.contains(player)) {
            player.sendMessage("§cYour purple fireball is recharging!")
            return
        }
        dragonAbilityCooldown.add(player)
        Bukkit.getScheduler().runTaskLater(plugin as org.bukkit.plugin.java.JavaPlugin, Runnable {
            dragonAbilityCooldown.remove(player)
            player.sendMessage("§aPurple fireball ready!")
        }, 60L)
        shootPurpleFireball(player)
        event.isCancelled = true
    }

    private fun shootPurpleFireball(player: Player) {
        val eyeLocation = player.eyeLocation
        val direction = eyeLocation.direction.normalize()
        player.world.spawn(eyeLocation.add(direction), DragonFireball::class.java) {
            it.velocity = direction.multiply(1.5)
            it.shooter = player
            it.persistentDataContainer.set(NamespacedKey(plugin, "dragon_fireball"), PersistentDataType.BYTE, 1)
        }
    }

    fun updateEnderDragonBeams() {
        Bukkit.getOnlinePlayers().forEach { player ->
            if (cursed.contains(player.name)) return
            val mob = plugin.playerMobMap[player.uniqueId]?.uppercase() ?: return@forEach
            if (mob != "ENDER_DRAGON") return@forEach
            val crystals = player.getNearbyEntities(100.0, 100.0, 100.0).filterIsInstance<EnderCrystal>()
            if (crystals.isEmpty()) return@forEach
            crystals.forEach { crystal ->
                sendRealCrystalBeam(crystal, player)
                player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 20, 1, true, false, false))
            }
        }
    }

    fun sendRealCrystalBeam(crystal: EnderCrystal, player: Player) {
        crystal.beamTarget = player.location.clone().add(0.0, 1.0, 0.0)
    }

    fun onEnderDragonBreak(event: PlayerInteractEvent) {
        if (cursed.contains(event.player.name)) return
        if (!event.action.name.contains("LEFT_CLICK_BLOCK")) return
        val block = event.clickedBlock ?: return
        if (block.type != Material.END_PORTAL_FRAME && block.type != Material.END_PORTAL) return
        val player = event.player
        block.breakNaturally(player.inventory.itemInMainHand)
        player.playSound(player.location, Sound.BLOCK_GLASS_BREAK, 1.5f, 1.0f)
    }

    // ----------------------- TUFF GOLEM ---------------------------

    fun onTuffGolemInitialize(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, -1, 0, false, false, false))
        player.getAttribute(Attribute.SCALE)?.baseValue = 0.5
    }

    fun tuffGolemCurse(player: Player) {
        cursed.add(player.name)
        player.removePotionEffect(PotionEffectType.RESISTANCE)
        player.getAttribute(Attribute.SCALE)?.baseValue = 1.0
    }

    fun tuffGolemUncurse(player: Player) {
        cursed.remove(player.name)
        applyTuffGolemEffects(player)
        player.getAttribute(Attribute.SCALE)?.baseValue = 0.5
    }

    private fun activateTuffShield(player: Player) {
        tuffGolemCooldown.add(player.uniqueId)
        player.isInvulnerable = true
        player.world.playSound(player.location, Sound.BLOCK_TUFF_PLACE, 1.2f, 0.8f)
        player.world.spawnParticle(Particle.BLOCK, player.location.add(0.0, 1.0, 0.0), 40, 0.4, 0.6, 0.4, Material.TUFF.createBlockData())
        Bukkit.getScheduler().runTaskLater(plugin, Runnable { player.isInvulnerable = false }, 20L)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            tuffGolemCooldown.remove(player.uniqueId)
            player.sendMessage("§aTuff Shield ready!")
        }, 20L * 30)
    }

    fun onTuffGolemLeftClick(event: PlayerInteractEvent) {
        val player = event.player
        if (cursed.contains(player.name)) return
        val mob = plugin.playerMobMap[player.uniqueId]?.uppercase() ?: return
        if (mob != "TUFFGOLEM") return
        if (!event.action.name.contains("LEFT_CLICK")) return
        if (player.inventory.itemInMainHand.type != Material.TUFF) return
        if (tuffGolemCooldown.contains(player.uniqueId)) {
            player.sendMessage("§cTuff Shield is recharging!")
            return
        }
        activateTuffShield(player)
        event.isCancelled = true
    }

    fun applyTuffGolemEffects(player: Player) {
        if (cursed.contains(player.name)) return
        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, -1, 0, false, false, false))
    }

    fun openTuffGolemEnderChest(player: Player) {
        if (cursed.contains(player.name)) return
        if (plugin.playerMobMap[player.uniqueId] == "TUFFGOLEM") {
            player.openInventory(player.enderChest)
        } else {
            player.sendMessage("§cOnly Tuff Golems can use this command!")
        }
    }

    // ----------------------- GHAST -------------------------------

    private val ghastFireballCooldowns = mutableMapOf<Player, Long>()

    fun onGhastInitialize(player: Player) {
        plugin.mobsToPreventLoot.add("GHAST")
        plugin.enableFlight(player)
        player.flySpeed = 0.09F
        applyGhastEffects(player)
    }

    fun ghastCurse(player: Player) {
        cursed.add(player.name)
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE)
        player.removePotionEffect(PotionEffectType.REGENERATION)
        disableFlight(player)
    }

    fun ghastUncurse(player: Player) {
        cursed.remove(player.name)
        applyGhastEffects(player)
    }

    fun applyGhastEffects(player: Player) {
        if (cursed.contains(player.name)) return
        player.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, -1, 0, false, false, false))
        player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, -1, 0, false, false, false))
        plugin.enableFlight(player)
        player.flySpeed = 0.09F
    }

    fun onGhastFireball(event: PlayerInteractEvent) {
        val player = event.player
        if (cursed.contains(player.name)) return
        if (plugin.playerMobMap[player.uniqueId]?.uppercase() != "GHAST") return
        if (!event.action.name.contains("LEFT_CLICK")) return
        val item = player.inventory.itemInMainHand.type
        if (item != Material.GHAST_TEAR && item != Material.GUNPOWDER) return
        val now = System.currentTimeMillis()
        val lastShoot = ghastFireballCooldowns[player] ?: 0
        if (now - lastShoot < 1000) return
        ghastFireballCooldowns[player] = now
        val direction = player.location.direction.normalize()
        val fireball = player.world.spawn(player.eyeLocation.add(direction), Fireball::class.java)
        fireball.velocity = direction.multiply(0.25)
        fireball.yield = 4f
        fireball.isIncendiary
        fireball.shooter = player
        player.world.playSound(player.location, "minecraft:entity.ghast.shoot", 1f, 1f)
    }

    fun onGhastFireballHit(event: EntityDamageByEntityEvent) {
        val player = event.entity as? Player ?: return
        if (plugin.playerMobMap[player.uniqueId]?.uppercase() != "GHAST") return
        val fireball = event.damager as? Fireball
        if (fireball != null && fireball.shooter == player) {
            player.velocity = player.velocity.clone().add(Vector(0.0, 2.5, 0.0))
            event.isCancelled = true
        }
    }

    fun onGhastMove(event: PlayerMoveEvent) {
        val player = event.player
        if (cursed.contains(player.name)) return
        val mob = plugin.playerMobMap[player.uniqueId]?.uppercase() ?: return
        if (mob != "GHAST") return
        player.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, -1, 0, false, false, false))
    }

    // ----------------------- AXOLOTL -------------------------------

    fun onAxolotlInitialize(player: Player) {
        plugin.mobsToPreventLoot.add("AXOLOTL")
        player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, -1, 1))
        player.addPotionEffect(PotionEffect(PotionEffectType.WATER_BREATHING, -1, 0))
        player.addPotionEffect(PotionEffect(PotionEffectType.CONDUIT_POWER, -1, 0, false, false, false))
        player.addPotionEffect(PotionEffect(PotionEffectType.DOLPHINS_GRACE, -1, 0, false, false, false))
    }

    fun axolotlCurse(player: Player) {
        cursed.add(player.name)
        player.removePotionEffect(PotionEffectType.REGENERATION)
        player.removePotionEffect(PotionEffectType.WATER_BREATHING)
        player.removePotionEffect(PotionEffectType.CONDUIT_POWER)
        player.removePotionEffect(PotionEffectType.DOLPHINS_GRACE)
        disableFlight(player)
    }

    fun axolotlUncurse(player: Player) {
        cursed.remove(player.name)
        applyAxolotlEffects(player)
    }

    fun applyAxolotlEffects(player: Player) {
        if (cursed.contains(player.name)) return
        player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, -1, 1, false, false, false))
        player.addPotionEffect(PotionEffect(PotionEffectType.WATER_BREATHING, -1, 0, false, false, false))
        player.addPotionEffect(PotionEffect(PotionEffectType.CONDUIT_POWER, -1, 0, false, false, false))
        player.addPotionEffect(PotionEffect(PotionEffectType.DOLPHINS_GRACE, -1, 0, false, false, false))
    }

    // ----------------------- SKELETON -----------------------

    fun onSkeletonInitialize(player: Player) {
        plugin.mobsToPreventLoot.add("SKELETON")
        player.addPotionEffect(PotionEffect(PotionEffectType.POISON, 1, 0, false, false))
        player.removePotionEffect(PotionEffectType.POISON)
    }

    fun onSkeletonTick(player: Player) {
        if (player.hasPotionEffect(PotionEffectType.POISON)) {
            player.removePotionEffect(PotionEffectType.POISON)
        }
    }

    // ----------------------- SILVERFISH --------------------------

    private val silverfishHerdTasks   = mutableMapOf<UUID, org.bukkit.scheduler.BukkitTask>()
    private val silverfishTunnelTasks = mutableMapOf<UUID, org.bukkit.scheduler.BukkitTask>()
    private val silverfishInfestTasks = mutableMapOf<UUID, org.bukkit.scheduler.BukkitTask>()

    private val tunnelable = setOf(
        Material.STONE, Material.COBBLESTONE,
        Material.DEEPSLATE, Material.COBBLED_DEEPSLATE,
        Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS,
        Material.MOSSY_STONE_BRICKS, Material.MOSSY_COBBLESTONE,
        Material.INFESTED_STONE, Material.INFESTED_COBBLESTONE,
        Material.INFESTED_DEEPSLATE, Material.INFESTED_STONE_BRICKS
    )

    private val infestMap = mapOf(
        Material.STONE                 to Material.INFESTED_STONE,
        Material.COBBLESTONE           to Material.INFESTED_COBBLESTONE,
        Material.DEEPSLATE             to Material.INFESTED_DEEPSLATE,
        Material.COBBLED_DEEPSLATE     to Material.INFESTED_DEEPSLATE,
        Material.STONE_BRICKS          to Material.INFESTED_STONE_BRICKS,
        Material.CRACKED_STONE_BRICKS  to Material.INFESTED_CRACKED_STONE_BRICKS,
        Material.MOSSY_STONE_BRICKS    to Material.INFESTED_MOSSY_STONE_BRICKS,
        Material.CHISELED_STONE_BRICKS to Material.INFESTED_CHISELED_STONE_BRICKS
    )

    fun onSilverfishInitialize(player: Player) {
        plugin.mobsToPreventLoot.add("SILVERFISH")
        player.getAttribute(Attribute.SCALE)?.baseValue = 0.25
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, -1, 0, false, false, false))
        startSilverfishHerd(player)
        startSilverfishTunnel(player)
        startSilverfishInfest(player)
    }

    fun onSilverfishCleanup(player: Player) {
        player.getAttribute(Attribute.SCALE)?.baseValue = 1.0
        player.removePotionEffect(PotionEffectType.SPEED)
        silverfishHerdTasks.remove(player.uniqueId)?.cancel()
        silverfishTunnelTasks.remove(player.uniqueId)?.cancel()
        silverfishInfestTasks.remove(player.uniqueId)?.cancel()
    }

    private fun startSilverfishHerd(player: Player) {
        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!isActiveSilverfish(player)) { silverfishHerdTasks.remove(player.uniqueId)?.cancel(); return@Runnable }
            player.getNearbyEntities(24.0, 24.0, 24.0)
                .filterIsInstance<org.bukkit.entity.Silverfish>()
                .filter { it.location.distanceSquared(player.location) > 4.0 }
                .forEach { sf -> sf.pathfinder.moveTo(player.location, 1.1) }
        }, 0L, 20L)
        silverfishHerdTasks[player.uniqueId] = task
    }

    private fun startSilverfishTunnel(player: Player) {
        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!isActiveSilverfish(player)) { silverfishTunnelTasks.remove(player.uniqueId)?.cancel(); return@Runnable }
            val feet = player.location.block
            val head = player.location.clone().add(0.0, 1.0, 0.0).block
            for (block in listOf(feet, head)) {
                if (block.type !in tunnelable) continue
                val original = block.type
                block.type = Material.AIR
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    val pFeet = player.location.block
                    val pHead = player.location.clone().add(0.0, 1.0, 0.0).block
                    if (block != pFeet && block != pHead && block.type == Material.AIR) {
                        block.type = original
                    }
                }, 8L)
            }
        }, 0L, 1L)
        silverfishTunnelTasks[player.uniqueId] = task
    }

    private fun startSilverfishInfest(player: Player) {
        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!isActiveSilverfish(player)) { silverfishInfestTasks.remove(player.uniqueId)?.cancel(); return@Runnable }
            val center = player.location
            val candidates = mutableListOf<org.bukkit.block.Block>()
            for (x in -1..1) for (y in -1..1) for (z in -1..1) {
                val b = center.clone().add(x.toDouble(), y.toDouble(), z.toDouble()).block
                if (b.type in infestMap) candidates.add(b)
            }
            candidates.shuffle()
            candidates.take(3).forEach { b -> b.type = infestMap[b.type] ?: return@forEach }
        }, 0L, 40L)
        silverfishInfestTasks[player.uniqueId] = task
    }

    fun onSilverfishBaneDamage(event: EntityDamageByEntityEvent) {
        val damager = event.damager as? Player ?: return
        val weapon = damager.inventory.itemInMainHand
        if (weapon.containsEnchantment(org.bukkit.enchantments.Enchantment.BANE_OF_ARTHROPODS)) {
            event.damage = event.damage * 1.5
        }
    }

    private val arthropodTypes = setOf(
        org.bukkit.entity.EntityType.SPIDER,
        org.bukkit.entity.EntityType.CAVE_SPIDER,
        org.bukkit.entity.EntityType.BEE,
        org.bukkit.entity.EntityType.SILVERFISH,
        org.bukkit.entity.EntityType.ENDERMITE
    )

    private val silverfishSlowKey = NamespacedKey(plugin, "silverfish_arthropod_slow")

    fun onSilverfishArthropodHit(event: EntityDamageByEntityEvent) {
        val damager = event.damager
        if (damager.type !in arthropodTypes) return
        val player = event.entity as? Player ?: return
        val attr = player.getAttribute(Attribute.MOVEMENT_SPEED) ?: return
        attr.modifiers.filter { it.key == silverfishSlowKey }.forEach { attr.removeModifier(it) }
        attr.addModifier(
            org.bukkit.attribute.AttributeModifier(
                silverfishSlowKey,
                -0.075,
                org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1
            )
        )
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            attr.modifiers.filter { it.key == silverfishSlowKey }.forEach { attr.removeModifier(it) }
        }, 60L)
    }

    private fun isActiveSilverfish(player: Player) =
        player.isOnline && plugin.playerMobMap[player.uniqueId]?.uppercase() == "SILVERFISH"

    // ----------------------- SHULKER ------------------------------

    private val shulkerBoxCooldown = mutableSetOf<UUID>()

    fun onShulkerInitialize(player: Player) {
        plugin.mobsToPreventLoot.add("SHULKER")
        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, -1, 1, false, false, false))
    }

    fun onShulkerDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        val finalHealth = player.health - event.finalDamage
        if (finalHealth <= 10.0 && !player.scoreboardTags.contains("split_cd")) {
            player.addScoreboardTag("split_cd")
            repeat(2) { player.world.spawn(player.location, Shulker::class.java) }
        }
    }

    fun onShulkerRightClick(event: PlayerInteractEvent) {
        val player = event.player
        if (!event.action.name.contains("RIGHT_CLICK")) return
        if (shulkerBoxCooldown.contains(player.uniqueId)) {
            player.sendMessage("§cYour shulker boxes are recharging!")
            return
        }
        repeat(3) { player.inventory.addItem(ItemStack(Material.SHULKER_BOX)) }
        shulkerBoxCooldown.add(player.uniqueId)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            shulkerBoxCooldown.remove(player.uniqueId)
            player.sendMessage("§aShulker boxes ready!")
        }, 200L)
        event.isCancelled = true
    }

    // ----------------------- HOMING ARROWS -----------------------

    fun onSkeletonShoot(event: EntityShootBowEvent) {
        val player = event.entity
        if (player !is Player) return
        if (plugin.playerMobMap[player.uniqueId]?.equals("SKELETON", ignoreCase = true) != true) return
        val arrow = event.projectile
        if (arrow !is Arrow) return
        object : BukkitRunnable() {
            override fun run() {
                if (arrow.isDead || arrow.isOnGround) { cancel(); return }
                val nearby = arrow.world.getNearbyEntities(arrow.location, 16.0, 16.0, 16.0)
                    .filterIsInstance<LivingEntity>()
                    .filter { it != player }
                val target = nearby.minByOrNull { it.location.distanceSquared(arrow.location) } ?: return
                val direction = target.eyeLocation.toVector().subtract(arrow.location.toVector()).normalize()
                arrow.velocity = arrow.velocity.add(direction.multiply(0.2)).normalize().multiply(arrow.velocity.length())
            }
        }.runTaskTimer(plugin, 1L, 1L)
    }
}
