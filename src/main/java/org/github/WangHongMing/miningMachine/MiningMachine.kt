package org.github.WangHongMing.miningMachine

import net.kyori.adventure.text.Component
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.block.data.Directional
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

/* ================= ENERGY MANAGER ================= */

object EnergyManager {
    fun getPlayerEnergy(player: Player, energyItem: Material): Int {
        return player.inventory.contents
            .filter { it != null && it.type == energyItem }
            .sumOf { it?.amount ?: 0 }
    }

    fun consumeEnergy(player: Player, energyItem: Material, amount: Int): Boolean {
        if (getPlayerEnergy(player, energyItem) < amount) return false

        var remaining = amount
        for (item in player.inventory.contents) {
            if (item != null && item.type == energyItem) {
                val remove = minOf(item.amount, remaining)
                item.amount -= remove
                remaining -= remove
                if (remaining <= 0) return true
            }
        }
        return false
    }
}

/* ================= MAIN PLUGIN ================= */

class MiningMachine : JavaPlugin(), Listener {

    private val activeMachines = mutableMapOf<UUID, BukkitRunnable>()
    private val machineStorages = mutableMapOf<UUID, Inventory>()

    private val MACHINE_KEY by lazy { NamespacedKey(this, "mining_machine_id") }

    override fun onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this)

        getCommand("getminingmachine")?.setExecutor { sender, _, _, _ ->
            if (sender !is Player) return@setExecutor true

            val item = ItemStack(Material.DISPENSER)
            val meta = item.itemMeta!!
            meta.setDisplayName("§6Mining Machine")
            meta.lore = listOf("§7Place to start mining!")
            meta.persistentDataContainer.set(
                MACHINE_KEY,
                PersistentDataType.STRING,
                UUID.randomUUID().toString()
            )
            item.itemMeta = meta

            sender.inventory.addItem(item)
            sender.sendMessage(Component.text("§aYou received a Mining Machine!"))
            true
        }
    }

    override fun onDisable() {
        Bukkit.getScheduler().cancelTasks(this)
        activeMachines.clear()
        machineStorages.clear()
    }

    /* ================= PLACE MACHINE ================= */

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand
        val meta = item.itemMeta ?: return

        if (event.blockPlaced.type != Material.DISPENSER) return
        if (meta.displayName != "§6Mining Machine") return

        val machineId = meta.persistentDataContainer.get(
            MACHINE_KEY,
            PersistentDataType.STRING
        ) ?: return

        val uuid = UUID.fromString(machineId)

        val state = event.blockPlaced.state as org.bukkit.block.TileState
        state.persistentDataContainer.set(
            MACHINE_KEY,
            PersistentDataType.STRING,
            machineId
        )
        state.update(true)

        machineStorages.computeIfAbsent(uuid) {
            Bukkit.createInventory(null, 27, Component.text("Mining Machine Storage"))
        }

        startMiningMachine(event.blockPlaced, event.player, uuid)
        event.player.sendMessage(Component.text("§aMining Machine activated!"))
    }

    /* ================= MACHINE LOGIC ================= */

    private fun startMiningMachine(startBlock: Block, owner: Player, uuid: UUID) {

        activeMachines[uuid]?.cancel()

        val task = object : BukkitRunnable() {

            val ENERGY_ITEM = Material.COAL
            val ENERGY_COST = 1

            var currentPos = startBlock.location.clone()
            val facing = owner.facing

            override fun run() {

                if (!EnergyManager.consumeEnergy(owner, ENERGY_ITEM, ENERGY_COST)) {
                    owner.sendMessage(Component.text("§cMachine stopped (no energy)"))
                    cancel()
                    activeMachines.remove(uuid)
                    return
                }

                val target = currentPos.block.getRelative(facing)

                if (target.type == Material.AIR || target.type == Material.BEDROCK) {
                    moveMachine()
                    return
                }

                val type = target.type
                target.type = Material.AIR

                val storage = machineStorages[uuid]
                val leftover = storage?.addItem(ItemStack(type))
                leftover?.values?.forEach {
                    target.world.dropItemNaturally(target.location, it)
                }

                moveMachine()
            }

            private fun moveMachine() {
                currentPos.block.type = Material.AIR
                currentPos.add(facing.direction)
                currentPos.block.type = Material.DISPENSER

                val data = currentPos.block.blockData as Directional
                data.facing = facing
                currentPos.block.blockData = data

                val state = currentPos.block.state as org.bukkit.block.TileState
                state.persistentDataContainer.set(
                    MACHINE_KEY,
                    PersistentDataType.STRING,
                    uuid.toString()
                )
                state.update(true)
            }
        }

        activeMachines[uuid] = task
        task.runTaskTimer(this, 20L, 20L)
    }

    /* ================= OPEN STORAGE ================= */

    @EventHandler
    fun onRightClick(event: PlayerInteractEvent) {
        val block = event.clickedBlock ?: return
        if (block.type != Material.DISPENSER) return

        val state = block.state as? org.bukkit.block.TileState ?: return
        val idStr = state.persistentDataContainer.get(
            MACHINE_KEY,
            PersistentDataType.STRING
        ) ?: return

        val uuid = UUID.fromString(idStr)
        val storage = machineStorages[uuid] ?: return

        event.isCancelled = true
        event.player.openInventory(storage)
    }

    /* ================= BREAK MACHINE ================= */

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        if (block.type != Material.DISPENSER) return

        val state = block.state as? org.bukkit.block.TileState ?: return
        val idStr = state.persistentDataContainer.get(
            MACHINE_KEY,
            PersistentDataType.STRING
        ) ?: return

        val uuid = UUID.fromString(idStr)

        activeMachines.remove(uuid)?.cancel()

        machineStorages.remove(uuid)?.contents
            ?.filterNotNull()
            ?.forEach {
                block.world.dropItemNaturally(block.location, it)
            }

        event.isDropItems = false
        block.world.dropItemNaturally(block.location, ItemStack(Material.DISPENSER).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName("§6Mining Machine")
                lore = listOf("§7Place to start mining!")
            }
        })

        event.player.sendMessage(Component.text("§cMining Machine stopped"))
    }
}
