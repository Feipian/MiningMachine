package org.github.WangHongMing.miningMachine

import net.kyori.adventure.text.Component
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.block.BlockState
import org.bukkit.block.data.Directional
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import org.bukkit.persistence.PersistentDataContainer


// Example of a simple EnergyManager (can be more complex)
object EnergyManager {
    fun getPlayerEnergy(player: Player, energyItem: Material): Int {
        return player.inventory.contents
            .filter { it != null && it.type == energyItem }
            .sumOf { it?.amount ?: 0 }
    }

    fun consumeEnergy(player: Player, energyItem: Material, amount: Int): Boolean {
        var remaining = amount
        val inventory = player.inventory

        // First, check if enough energy exists
        if (getPlayerEnergy(player, energyItem) < amount) {
            return false // Not enough energy
        }

        // Then, remove the energy
        for (item in inventory.contents) {
            item?.takeIf { it.type == energyItem }?.let { energyStack ->
                val toRemove = minOf(remaining, energyStack.amount)
                energyStack.amount -= toRemove
                remaining -= toRemove
                if (remaining <= 0) return true // Successfully removed
            }
        }
        return false // Should not happen if check passed, but for safety
    }
}


class MiningMachine : JavaPlugin(), Listener {
    private val activeMachines = mutableMapOf<UUID, BukkitRunnable>()

    override fun onEnable() {
        // Register events
        Bukkit.getPluginManager().registerEvents(this, this)

        val command = getCommand("getminingmachine")
        if (command == null) {
            logger.warning("Command /getminingmachine is not defined in plugin.yml!")
        } else {
            command.setExecutor { sender, _, _, _ ->
                if (sender is Player) {
                    val item = ItemStack(Material.DISPENSER)
                    val meta = item.itemMeta
                    meta.setDisplayName("§6Mining Machine")
                    meta.lore = listOf("§7Place to start mining!")


                    // ADD THIS SECTION TO STORE UUID
                    meta.persistentDataContainer.set(
                        NamespacedKey(this, "mining_machine_id"),
                        PersistentDataType.STRING,
                        UUID.randomUUID().toString()
                    )

                    item.itemMeta = meta

                    sender.inventory.addItem(item)
                    sender.sendMessage(Component.text("You received a Mining Machine!"))
                } else {
                    sender.sendMessage(Component.text("Only players can use this command."))
                }
                true
            }
        }

        // RemoveMiningMachine command
        getCommand("removeminingmachine")?.setExecutor { sender, _, _, _ ->
            if (sender !is Player) {
                sender.sendMessage(Component.text("Only players can use this command."))
                return@setExecutor true
            }

            val targetBlock = sender.getTargetBlockExact(10) ?: run {
                sender.sendMessage(Component.text("§cNo block in sight!"))
                return@setExecutor true
            }

//            if (targetBlock.type != Material.DISPENSER || !activeMachines.any { it.value.first == targetBlock.location }) {
//                sender.sendMessage(Component.text("§cNo active Mining Machine found!"))
//                return@setExecutor true
//            }

            // Return machine to inventory
            sender.inventory.addItem(ItemStack(Material.DISPENSER).apply {
                itemMeta = itemMeta?.apply {
                    setDisplayName("§6Mining Machine")
                    lore = listOf("§7Place to start mining!")
                }
            })

            // Clean up
//            activeMachines[targetBlock.location]?.cancel()
//            activeMachines.remove(targetBlock.location)
            targetBlock.type = Material.AIR

            // Effects
            sender.world.playSound(targetBlock.location, Sound.ENTITY_ITEM_PICKUP, 1f, 1f)
            sender.sendMessage(Component.text("§aMining Machine safely removed!"))
            true
        } ?: logger.warning("Command /removeminingmachine is not defined in plugin.yml!")

    }









    override fun onDisable() {
        // Clean up any running tasks
        Bukkit.getScheduler().cancelTasks(this)

    }



    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val block = event.blockPlaced
        val item = event.itemInHand
        val meta = item.itemMeta ?: return

        if (block.type == Material.DISPENSER &&
            item.itemMeta?.displayName == "§6Mining Machine"
        ) {
            // Get or generate the machine ID
            val machineId = meta.persistentDataContainer.get(
                NamespacedKey(this, "mining_machine_id"),
                PersistentDataType.STRING
            ) ?: UUID.randomUUID().toString()

            // ✅ Save UUID into the block itself
            val blockState = block.state
            if (blockState is org.bukkit.block.TileState) {
                val blockMeta = blockState.persistentDataContainer
                blockMeta.set(
                    NamespacedKey(this, "mining_machine_id"),
                    PersistentDataType.STRING,
                    machineId
                )
                blockState.update(true)
            }

            event.player.sendMessage(Component.text("Mining Machine placed!"))
            startMiningMachine(block, event.player, machineId)
        }
    }

    private fun startMiningMachine(startBlock: Block, owner: Player, machineId: String) {
        val plugin = this // 👈 save reference to your MiningMachine plugin
        val uuid = UUID.fromString(machineId)

        // Cancel any existing machine with this ID
//        activeMachines[uuid]?.second?.cancel()
        val task = object : BukkitRunnable() {


            val ENERGY_ITEM = Material.COAL
            val ENERGY_COST_PER_BLOCK = 1
            var blocksMined = 0
//            var totalEnergy = calcul(owner.inventory)
            var currentPosition = startBlock.location.clone() // Track current position
            val facing = owner.facing // if you have access to the player object


//            // Helper function to calculate total available energy
//            private fun calculateTotalEnergy(inventory: PlayerInventory): Int {
//                return inventory.contents
//                    .filter { it != null && it.type == ENERGY_ITEM }
//                    .sumOf { it?.amount ?: 0 }
//            }

//

            override fun run() {


                // Check and consume energy via the manager
                if (!EnergyManager.consumeEnergy(owner, ENERGY_ITEM, ENERGY_COST_PER_BLOCK)) {
                    owner.sendMessage(Component.text("§cMachine stopped - out of energy!"))
                    cancel()
                    return
                }

                // Now proceed with mining, as energy has been successfully consumed
                val energyAfterConsumption = EnergyManager.getPlayerEnergy(owner, ENERGY_ITEM) // Get updated total for display



//                // Check energy
//                if (totalEnergy < ENERGY_COST_PER_BLOCK) {
//                    owner.sendMessage(Component.text("§cMachine stopped - out of energy!"))
//                    cancel()
//                    return
//                }

                val world = currentPosition.world
                val targetBlock = currentPosition.block.getRelative(facing, 1)

                if (targetBlock.type != Material.AIR && targetBlock.type.isBlock) {

                    if(targetBlock.type != Material.BEDROCK) {
                        val type = targetBlock.type
                        targetBlock.type = Material.AIR
                        world.dropItemNaturally(targetBlock.location, ItemStack(type))
                        blocksMined++

                        // Move the machine forward (break old block, place new one)
                        val oldBlock = currentPosition.block
                        oldBlock.type = Material.AIR // Remove the old Dispenser

                        currentPosition.add(facing.direction) // Update location
                        currentPosition.block.type = Material.DISPENSER // Place new Dispenser

                        // Optionally, set the Dispenser's facing direction
                        val blockData = currentPosition.block.blockData as Directional
                        blockData.facing = facing
                        currentPosition.block.blockData = blockData

                        // re assign UUID to new dispather
                        // ✅ Copy UUID to new dispenser
                        val newState = currentPosition.block.state
                        if (newState is org.bukkit.block.TileState) { // ✅ only TileStates have PDC
                            val container = newState.persistentDataContainer
                            container.set(
                                NamespacedKey(plugin, "mining_machine_id"),
                                PersistentDataType.STRING,
                                machineId
                            )
                            newState.update(true)
                        }

                        owner.sendMessage(Component.text("§aMined: $type §7(Energy left: $energyAfterConsumption) §b[Machine ID: $machineId]"))

                    }

                }
                else if(targetBlock.type == Material.AIR){

                    val oldBlock = currentPosition.block
                    oldBlock.type = Material.AIR // Remove the old Dispenser
                    // continue forward
                    currentPosition.add(facing.direction)
                    currentPosition.block.type = Material.DISPENSER // Place new Dispenser
                }
                else {
                    owner.sendMessage(Component.text("§eNothing to mine - stop machine!"))
                    cancel()
                    return
                }
            }



        }

        // Store with UUID instead of location as key
        activeMachines[uuid] = task
        task.runTaskTimer(this, 0L, 20L)
    }


    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        logger.info("Break at ${block.location} type=${block.type}")

//        if (block.type != Material.LEGACY_DISPENSER) return

        val state = block.state
        if (state !is org.bukkit.block.TileState) return

        val key = NamespacedKey(this, "mining_machine_id")
        val idStr = state.persistentDataContainer.get(key, PersistentDataType.STRING) ?: return
        val uuid = runCatching { UUID.fromString(idStr) }.getOrNull() ?: return

        logger.info("Machine UUID at block: $idStr")
        logger.info("$activeMachines")
        val task = activeMachines.remove(uuid) ?: return
        logger.info("Successful remove the machine $idStr 's task")
        task.cancel()

        // Optional: prevent vanilla drops and give your custom drop
        event.isDropItems = false
        block.world.dropItemNaturally(block.location, ItemStack(Material.DISPENSER).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName("§6Mining Machine")
                lore = listOf("§7Place to start mining!")
            }
        })

        // Let the normal break proceed (the block will disappear). You don’t need to set AIR.
        event.player.sendMessage(Component.text("§cMining Machine destroyed and stopped."))
    }




}