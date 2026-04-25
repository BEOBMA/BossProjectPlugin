package org.beobma.bossProjectPlugin.game

import org.beobma.bossProjectPlugin.entity.enemy.EnemyData
import org.beobma.bossProjectPlugin.entity.player.PlayerData
import org.beobma.bossProjectPlugin.entity.enemy.BossBattleMapData
import org.beobma.bossProjectPlugin.entity.enemy.DeathCountMode
import java.util.UUID
import kotlin.math.max

class Game {
    val playerDatas: MutableList<PlayerData> = mutableListOf()

    lateinit var mapData: BossBattleMapData
        private set

    lateinit var bossData: EnemyData
        private set
    val isBossInitialized: Boolean
        get() = this::bossData.isInitialized

    private val deathCountByPlayer: MutableMap<UUID, Int> = mutableMapOf()
    var sharedDeathCount: Int = 0
        private set

    var startedAtMillis: Long = 0L
        private set

    fun setupMap(mapData: BossBattleMapData) {
        this.mapData = mapData
    }

    fun setupBoss(bossData: EnemyData) {
        this.bossData = bossData
    }

    fun initializeBattleState() {
        startedAtMillis = System.currentTimeMillis()
        sharedDeathCount = 0
        deathCountByPlayer.clear()
        playerDatas.forEach { deathCountByPlayer[it.player.uniqueId] = 0 }
    }

    fun increasePlayerDeath(uuid: UUID) {
        deathCountByPlayer[uuid] = (deathCountByPlayer[uuid] ?: 0) + 1
    }

    fun increaseSharedDeath() {
        sharedDeathCount += 1
    }

    fun playerDeathCount(uuid: UUID): Int = deathCountByPlayer[uuid] ?: 0

    fun remainingDeaths(uuid: UUID): Int? {
        val limit = mapData.deathLimit ?: return null
        return when (mapData.deathCountMode) {
            DeathCountMode.NONE -> null
            DeathCountMode.PER_PLAYER -> (limit - playerDeathCount(uuid)).coerceAtLeast(0)
            DeathCountMode.SHARED -> (limit - sharedDeathCount).coerceAtLeast(0)
        }
    }

    fun consumeDeathIfAvailable(uuid: UUID): Boolean {
        val remaining = remainingDeaths(uuid) ?: return true
        if (remaining <= 0) return false

        when (mapData.deathCountMode) {
            DeathCountMode.NONE -> Unit
            DeathCountMode.PER_PLAYER -> increasePlayerDeath(uuid)
            DeathCountMode.SHARED -> increaseSharedDeath()
        }
        return true
    }

    fun carryOverDeathState(nextMapData: BossBattleMapData) {
        val currentLimit = mapData.deathLimit ?: return
        val nextLimit = nextMapData.deathLimit ?: return

        when {
            mapData.deathCountMode == DeathCountMode.PER_PLAYER && nextMapData.deathCountMode == DeathCountMode.PER_PLAYER -> {
                playerDatas.forEach { playerData ->
                    val uuid = playerData.player.uniqueId
                    val spentCount = playerDeathCount(uuid)
                    val previousRemaining = max(currentLimit - spentCount, 0)
                    deathCountByPlayer[uuid] = max(nextLimit - previousRemaining, 0)
                }
            }
            mapData.deathCountMode == DeathCountMode.SHARED && nextMapData.deathCountMode == DeathCountMode.SHARED -> {
                val previousRemaining = max(currentLimit - sharedDeathCount, 0)
                sharedDeathCount = max(nextLimit - previousRemaining, 0)
            }
            else -> Unit
        }
    }
}
