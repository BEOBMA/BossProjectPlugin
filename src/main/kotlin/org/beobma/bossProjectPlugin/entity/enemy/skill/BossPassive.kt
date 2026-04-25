package org.beobma.bossProjectPlugin.entity.enemy.skill

import org.beobma.bossProjectPlugin.entity.enemy.EnemyData
import org.beobma.bossProjectPlugin.entity.enemy.EnemyStatus
import org.beobma.bossProjectPlugin.game.Game
import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack

abstract class BossPassive {
    protected lateinit var enemyData: EnemyData
    protected lateinit var enemyStatus: EnemyStatus
    protected lateinit var game: Game

    abstract val name: String
    abstract val description: List<String>
    abstract val itemStack: ItemStack

    open val functionId: String? = null
    open val validPhases: Set<Int>? = null

    open fun inject(enemyData: EnemyData) {
        if (enemyData.status !is EnemyStatus) return
        this.enemyData = enemyData
        this.enemyStatus = enemyData.status as EnemyStatus
        this.game = enemyData.game
    }

    open fun onTick() {
        if (!isPhaseValid()) return
        runMcFunction()
    }

    protected fun isPhaseValid(): Boolean {
        val phases = validPhases ?: return true
        return phases.contains(enemyData.phase)
    }

    protected fun runMcFunction() {
        val id = functionId ?: return
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "function $id")
    }
}
