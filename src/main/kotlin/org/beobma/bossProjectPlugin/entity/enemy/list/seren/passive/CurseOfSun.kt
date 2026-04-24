package org.beobma.bossProjectPlugin.entity.enemy.list.seren.passive

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.bossProjectPlugin.BossProjectPlugin
import org.beobma.bossProjectPlugin.entity.enemy.skill.BossPassive
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID

class CurseOfSun : BossPassive(), Listener {
    private val maxGauge = 1000
    private val disabledMillis = 5_000L

    private val miniMessage = MiniMessage.miniMessage()
    private val gaugeByPlayer: MutableMap<UUID, Int> = mutableMapOf()
    private val disabledUntilByPlayer: MutableMap<UUID, Long> = mutableMapOf()
    private var listenerRegistered = false

    override val name: String = "태양의 저주"
    override val description: List<String> = listOf(
        "<gray>세렌의 일부 패턴에 피격당할 경우 게이지가 증가한다.",
        "<gray>게이지가 가득 차면 5초간 행동 불가 및 회복 불가 상태가 된다."
    )
    override val itemStack: ItemStack = ItemStack(Material.TOTEM_OF_UNDYING)

    override fun inject(enemyData: org.beobma.bossProjectPlugin.entity.enemy.EnemyData) {
        super.inject(enemyData)
        if (listenerRegistered) return
        Bukkit.getPluginManager().registerEvents(this, BossProjectPlugin.instance)
        listenerRegistered = true
    }

    override fun onTick() {
        game.playerDatas
            .asSequence()
            .map { it.player }
            .filter { it.isOnline }
            .forEach { player ->
                player.sendActionBar(buildActionBarText(player.uniqueId))
            }
    }

    fun increaseGauge(player: Player, amount: Int) {
        val uuid = player.uniqueId
        val current = gaugeByPlayer[uuid] ?: 0
        val next = (current + amount).coerceIn(0, maxGauge)
        gaugeByPlayer[uuid] = next

        player.sendActionBar(buildActionBarText(uuid))

        if (next < maxGauge) return

        gaugeByPlayer[uuid] = 0
        val disabledUntil = System.currentTimeMillis() + disabledMillis
        disabledUntilByPlayer[uuid] = disabledUntil
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!isRestricted(event.player.uniqueId)) return
        val from = event.from
        val to = event.to ?: return
        if (from.x == to.x && from.y == to.y && from.z == to.z) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (!isRestricted(event.player.uniqueId)) return
        if (event.action == Action.PHYSICAL) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerInteractAtEntity(event: PlayerInteractAtEntityEvent) {
        if (!isRestricted(event.player.uniqueId)) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerSwap(event: PlayerSwapHandItemsEvent) {
        if (!isRestricted(event.player.uniqueId)) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerDrop(event: PlayerDropItemEvent) {
        if (!isRestricted(event.player.uniqueId)) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerAttack(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        if (!isRestricted(attacker.uniqueId)) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerRegainHealth(event: EntityRegainHealthEvent) {
        val player = event.entity as? Player ?: return
        if (!isRestricted(player.uniqueId)) return
        event.isCancelled = true
    }

    private fun isRestricted(uuid: UUID): Boolean {
        val disabledUntil = disabledUntilByPlayer[uuid] ?: return false
        if (System.currentTimeMillis() <= disabledUntil) {
            return true
        }

        disabledUntilByPlayer.remove(uuid)
        return false
    }

    private fun buildActionBarText(uuid: UUID): net.kyori.adventure.text.Component {
        val gauge = gaugeByPlayer[uuid] ?: 0
        val barLength = 20
        val filledLength = ((gauge.toDouble() / maxGauge) * barLength).toInt().coerceIn(0, barLength)
        val emptyLength = barLength - filledLength
        val bar = "<gold>${"░".repeat(filledLength)}</gold><dark_gray>${"░".repeat(emptyLength)}</dark_gray>"

        return miniMessage.deserialize(
            "<yellow><bold>[</yellow> $bar <yellow><bold>]</yellow>"
        )
    }
}
