package org.beobma.bossProjectPlugin.entity.enemy.list.seren.passive

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.bossProjectPlugin.entity.enemy.skill.BossPassive
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID

class CurseOfSun : BossPassive() {
    private val maxGauge = 1000
    private val disabledMillis = 5_000L

    private val miniMessage = MiniMessage.miniMessage()
    private val gaugeByPlayer: MutableMap<UUID, Int> = mutableMapOf()
    private val disabledUntilByPlayer: MutableMap<UUID, Long> = mutableMapOf()

    override val name: String = "태양의 저주"
    override val description: List<String> = listOf(
        "<gray>세렌의 일부 패턴에 피격당할 경우 게이지가 증가한다.",
        "<gray>게이지가 가득 차면 5초간 행동 불가 및 회복 불가 상태가 된다."
    )
    override val itemStack: ItemStack = ItemStack(Material.TOTEM_OF_UNDYING)

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

    private fun buildActionBarText(uuid: UUID): net.kyori.adventure.text.Component {
        val gauge = gaugeByPlayer[uuid] ?: 0
        val barLength = 20
        val filledLength = ((gauge.toDouble() / maxGauge) * barLength).toInt().coerceIn(0, barLength)
        val emptyLength = barLength - filledLength
        val bar = "<gold>${"█".repeat(filledLength)}</gold><dark_gray>${"░".repeat(emptyLength)}</dark_gray>"

        return miniMessage.deserialize(
            "<yellow><bold>[</yellow> $bar <yellow><bold>]</yellow>"
        )
    }
}
