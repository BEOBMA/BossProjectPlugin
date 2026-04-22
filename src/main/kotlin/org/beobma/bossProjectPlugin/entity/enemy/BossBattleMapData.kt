package org.beobma.bossProjectPlugin.entity.enemy

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World

enum class DeathCountMode {
    NONE,
    PER_PLAYER,
    SHARED
}

data class BossBattleMapData(
    val id: String,
    val worldName: String,
    val spawnX: Double,
    val spawnY: Double,
    val spawnZ: Double,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val deathCountMode: DeathCountMode = DeathCountMode.PER_PLAYER,
    val deathLimit: Int? = 3,
    val timeLimitMinutes: Int? = 30
) {
    fun world(): World = Bukkit.getWorld(worldName)
        ?: error("월드 '$worldName' 를 찾을 수 없습니다. 보스전 전용 맵이 로드되어 있는지 확인해주세요.")

    fun spawnLocation(): Location {
        return Location(world(), spawnX, spawnY, spawnZ, yaw, pitch)
    }
}
