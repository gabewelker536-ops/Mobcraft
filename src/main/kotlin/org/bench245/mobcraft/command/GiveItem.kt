package org.bench245.mobcraft.command

import org.bench245.mobcraft.Mobcraft
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class GiveItem(private val plugin: Mobcraft) : CommandExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) return true
        val player = sender
        var amount: Int

        val mobType = plugin.playerMobMap[player.uniqueId]?.uppercase() ?: run {
            player.sendMessage("§cYou don't have a mob assigned.")
            return true
        }

        if (args.isEmpty()) {
            player.sendMessage("§cUsage: /giveitem <mobitem> <amount>")
            return true
        }

        amount = if (args.size == 1) 1 else args[1].toIntOrNull() ?: 1

        when (mobType) {
            "GHAST" -> {
                when (args[0].lowercase()) {
                    "ghast_tear" -> giveItem(player, Material.GHAST_TEAR, amount)
                    "gunpowder"  -> giveItem(player, Material.GUNPOWDER, amount)
                    else -> player.sendMessage("§cInvalid type. Use 'ghast_tear' or 'gunpowder'.")
                }
            }
            "BLAZE" -> {
                when (args[0].lowercase()) {
                    "blaze_rod" -> giveItem(player, Material.BLAZE_ROD, amount)
                    else -> player.sendMessage("§cInvalid type. Use 'blaze_rod'.")
                }
            }
            "ENDERMAN" -> {
                when (args[0].lowercase()) {
                    "ender_pearl" -> giveItem(player, Material.ENDER_PEARL, amount)
                    else -> player.sendMessage("§cInvalid type. Use 'ender_pearl'.")
                }
            }
            "TUFFGOLEM" -> {
                when (args[0].lowercase()) {
                    "tuff" -> giveItem(player, Material.TUFF, amount)
                    else -> player.sendMessage("§cInvalid type. Use 'tuff'.")
                }
            }
            "ENDER_DRAGON" -> {
                when (args[0].lowercase()) {
                    "dragon_egg"       -> giveItem(player, Material.DRAGON_EGG, amount)
                    "dragon_breath"    -> giveItem(player, Material.DRAGON_BREATH, amount)
                    "end_portal_frame" -> giveItem(player, Material.END_PORTAL_FRAME, amount)
                    else -> player.sendMessage("§cInvalid type. Use 'dragon_egg', 'dragon_breath', or 'end_portal_frame'.")
                }
            }
            "ELDER_GUARDIAN" -> {
                when (args[0].lowercase()) {
                    "sponge"     -> giveItem(player, Material.SPONGE, amount)
                    "prismarine" -> giveItem(player, Material.PRISMARINE, amount)
                    else -> player.sendMessage("§cInvalid type. Use 'sponge' or 'prismarine'.")
                }
            }
            "SHULKER" -> {
                when (args[0].lowercase()) {
                    "shulker_shell" -> giveItem(player, Material.SHULKER_SHELL, amount)
                    else -> player.sendMessage("§cInvalid type. Use 'shulker_shell'.")
                }
            }
            "SILVERFISH" -> {
                when (args[0].lowercase()) {
                    "iron_nugget" -> giveItem(player, Material.IRON_NUGGET, amount)
                    else -> player.sendMessage("§cInvalid type. Use 'iron_nugget'.")
                }
            }
        }
        return true
    }

    private fun giveItem(player: Player, material: Material, amount: Int) {
        player.inventory.addItem(ItemStack(material, amount))
    }
}
