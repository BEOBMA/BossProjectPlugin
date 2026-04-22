package org.beobma.bossProjectPlugin.manager

import org.beobma.bossProjectPlugin.BossProjectPlugin
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

class GameStartInvitationManager(
    private val plugin: BossProjectPlugin,
    private val timeoutTicks: Long = 20L * 15L
) : Listener {
    private var currentSession: InvitationSession? = null
    private val miniMessage = MiniMessage.miniMessage()

    fun hasActiveSession(): Boolean = currentSession != null

    fun requestStart(initiator: Player, targetPlayers: List<Player>) {
        if (currentSession != null) {
            initiator.sendMessage(miniMessage.deserialize("<red>이미 진행 중인 게임 시작 요청이 있습니다.</red>"))
            return
        }

        if (targetPlayers.size <= 1) {
            GameManager.startGame(targetPlayers)
            return
        }

        val session = InvitationSession(targetPlayers)
        currentSession = session
        session.openAll()
    }

    @EventHandler
    fun onInviteClick(event: InventoryClickEvent) {
        val session = currentSession ?: return
        if (!session.isInvitationInventory(event.view.topInventory)) {
            return
        }

        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val choice = session.resolveChoice(event.currentItem?.type) ?: return
        session.respond(player, choice)
        player.closeInventory()
    }

    @EventHandler
    fun onInviteClose(event: InventoryCloseEvent) {
        val session = currentSession ?: return
        if (!session.isInvitationInventory(event.view.topInventory)) {
            return
        }

        val player = event.player as? Player ?: return
        if (session.isPending(player.uniqueId)) {
            player.sendMessage(miniMessage.deserialize("<yellow>시간 내 선택하지 않으면 자동으로 거절 처리됩니다.</yellow>"))
        }
    }

    private inner class InvitationSession(players: List<Player>) {
        private val title = miniMessage.deserialize("<gold>게임 참가 여부</gold>")
        private val responses: MutableMap<UUID, Boolean?> = players.associate { it.uniqueId to null }.toMutableMap()
        private val inventories: MutableMap<UUID, Inventory> = mutableMapOf()

        private val timeoutTask: BukkitTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            finalizeRequest()
        }, timeoutTicks)

        fun openAll() {
            val totalSeconds = timeoutTicks / 20L
            Bukkit.getOnlinePlayers().forEach { onlinePlayer ->
                if (!responses.containsKey(onlinePlayer.uniqueId)) {
                    return@forEach
                }

                val inventory = createInviteInventory(totalSeconds)
                inventories[onlinePlayer.uniqueId] = inventory
                onlinePlayer.openInventory(inventory)
                onlinePlayer.sendMessage(miniMessage.deserialize("<gold>게임 참가 요청이 도착했습니다. ${totalSeconds}초 내로 선택해주세요.</gold>"))
            }
        }

        fun isInvitationInventory(inventory: Inventory): Boolean {
            return inventory.viewers.isNotEmpty() && inventory.viewers.any {
                it is Player && inventories[it.uniqueId] === inventory
            }
        }

        fun resolveChoice(type: Material?): Boolean? {
            return when (type) {
                Material.LIME_WOOL -> true
                Material.RED_WOOL -> false
                else -> null
            }
        }

        fun respond(player: Player, accepted: Boolean) {
            if (!responses.containsKey(player.uniqueId) || responses[player.uniqueId] != null) {
                return
            }

            responses[player.uniqueId] = accepted
            val message = if (accepted) "참가" else "거절"
            player.sendMessage(miniMessage.deserialize("<aqua>게임 참가 의사: ${message}</aqua>"))

            if (responses.values.none { it == null }) {
                finalizeRequest()
            }
        }

        fun isPending(uuid: UUID): Boolean = responses[uuid] == null

        private fun finalizeRequest() {
            if (currentSession !== this) {
                return
            }

            timeoutTask.cancel()

            val participants = responses.filterValues { it == true }.keys.mapNotNull { Bukkit.getPlayer(it) }
            val pendingPlayers = responses.filterValues { it == null }.keys.mapNotNull { Bukkit.getPlayer(it) }
            pendingPlayers.forEach {
                it.sendMessage(miniMessage.deserialize("<red>응답 시간이 초과되어 거절 처리되었습니다.</red>"))
            }

            Bukkit.getOnlinePlayers().forEach { player ->
                val summary = miniMessage.deserialize("<gray>게임 참가자 수: ${participants.size}/${responses.size}</gray>")
                player.sendMessage(summary)
            }

            currentSession = null

            if (participants.isEmpty()) {
                Bukkit.broadcast(miniMessage.deserialize("<red>참가자가 없어 게임 시작이 취소되었습니다.</red>"))
                return
            }

            GameManager.startGame(participants)
        }

        private fun createInviteInventory(totalSeconds: Long): Inventory {
            val inventory = Bukkit.createInventory(null, 9, title)
            inventory.setItem(3, createItem(Material.LIME_WOOL, "<green>참가</green>"))
            inventory.setItem(5, createItem(Material.RED_WOOL, "<red>거절</red>"))
            inventory.setItem(8, createItem(Material.CLOCK, "<yellow>제한 시간: ${totalSeconds}초</yellow>"))
            return inventory
        }

        private fun createItem(material: Material, displayName: String): ItemStack {
            val item = ItemStack(material)
            val meta: ItemMeta? = item.itemMeta
            meta?.displayName(miniMessage.deserialize(displayName))
            item.itemMeta = meta
            return item
        }
    }
}
