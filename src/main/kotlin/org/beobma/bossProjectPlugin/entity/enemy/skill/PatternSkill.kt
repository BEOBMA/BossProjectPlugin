package org.beobma.bossProjectPlugin.entity.enemy.skill

import org.beobma.bossProjectPlugin.entity.enemy.EnemyData
import org.beobma.bossProjectPlugin.entity.enemy.EnemyStatus
import org.beobma.bossProjectPlugin.game.Game
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack

abstract class PatternSkill {
    protected lateinit var enemyData: EnemyData
    protected lateinit var enemyStatus: EnemyStatus
    protected lateinit var game: Game

    private var lastUsedTick: Long = -1_000_000L

    abstract val name: String
    abstract val description: List<String>
    abstract val itemStack: ItemStack

    open val functionId: String? = null

    open fun inject(enemyData: EnemyData) {
        if (enemyData.status !is EnemyStatus) return
        this.enemyData = enemyData
        this.enemyStatus = enemyData.status as EnemyStatus
        this.game = enemyData.game
    }

    abstract fun canUse(): Boolean

    fun use() {
        if (!canUse()) return
        runMcFunction()
        onUse()
    }

    protected open fun onUse() {}

    open fun onPlayerHit(player: Player, event: EntityDamageByEntityEvent) {
        // default no-op
    }

    open fun onGameEnd() {
        // default no-op
    }

    protected fun canUseOnCooldown(cooldownTicks: Long): Boolean {
        return enemyStatus.elapsedTicks - lastUsedTick >= cooldownTicks
    }

    protected fun markUsedNow() {
        lastUsedTick = enemyStatus.elapsedTicks
    }


    protected fun runMcFunction() {
        val id = functionId ?: return
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "function $id")
    }
}
