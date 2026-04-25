package org.beobma.bossProjectPlugin.manager

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.bossProjectPlugin.BossProjectPlugin
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

object PlayerDeathLifecycleManager : Listener {
    private const val AUTO_RESPAWN_TICKS = 20L * 30L
    private const val SNEAK_HOLD_RESPAWN_TICKS = 20L * 3L
    private const val RESPAWN_IMMUNE_MILLIS = 3_000L

    private val miniMessage = MiniMessage.miniMessage()
    private val pendingRespawnLocations: MutableMap<UUID, Location> = mutableMapOf()
    private val fixedSpectatorPlayers: MutableSet<UUID> = mutableSetOf()
    private val autoRespawnTasks: MutableMap<UUID, BukkitTask> = mutableMapOf()
    private val sneakHoldRespawnTasks: MutableMap<UUID, BukkitTask> = mutableMapOf()
    private val respawnInvulnerableUntilByPlayer: MutableMap<UUID, Long> = mutableMapOf()

    fun clearAllStates() {
        autoRespawnTasks.values.forEach { it.cancel() }
        sneakHoldRespawnTasks.values.forEach { it.cancel() }
        autoRespawnTasks.clear()
        sneakHoldRespawnTasks.clear()
        pendingRespawnLocations.clear()
        fixedSpectatorPlayers.clear()
        respawnInvulnerableUntilByPlayer.clear()
    }

    fun isRespawnInvulnerable(player: Player): Boolean {
        val until = respawnInvulnerableUntilByPlayer[player.uniqueId] ?: return false
        if (System.currentTimeMillis() <= until) return true
        respawnInvulnerableUntilByPlayer.remove(player.uniqueId)
        return false
    }

    fun canBeTargetedByPattern(player: Player): Boolean {
        if (!player.isOnline || player.isDead) return false
        if (player.gameMode == GameMode.SPECTATOR) return false
        return !isRespawnInvulnerable(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerDamaged(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        val game = GameManager.getCurrentGame() ?: return
        if (game.playerDatas.none { it.player.uniqueId == player.uniqueId }) return

        if (isRespawnInvulnerable(player)) {
            event.isCancelled = true
            return
        }

        val finalHealth = player.health - event.finalDamage
        if (finalHealth > 0.0) return

        event.isCancelled = true
        handleLethalDamage(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onFixedSpectatorMove(event: PlayerMoveEvent) {
        if (!fixedSpectatorPlayers.contains(event.player.uniqueId)) return
        val from = event.from
        val to = event.to ?: return
        if (from.x == to.x && from.y == to.y && from.z == to.z) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSwapHandForInstantRespawn(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (!pendingRespawnLocations.containsKey(player.uniqueId)) return
        event.isCancelled = true
        respawnPlayer(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSneakForInstantRespawn(event: PlayerToggleSneakEvent) {
        val player = event.player
        val uuid = player.uniqueId
        if (!pendingRespawnLocations.containsKey(uuid)) return

        if (event.isSneaking) {
            sneakHoldRespawnTasks.remove(uuid)?.cancel()
            sneakHoldRespawnTasks[uuid] = BossProjectPlugin.instance.server.scheduler.runTaskLater(
                BossProjectPlugin.instance,
                Runnable {
                    if (!pendingRespawnLocations.containsKey(uuid)) return@Runnable
                    if (player.isSneaking) {
                        respawnPlayer(player)
                    }
                },
                SNEAK_HOLD_RESPAWN_TICKS
            )
            return
        }

        sneakHoldRespawnTasks.remove(uuid)?.cancel()
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        clearPlayerState(event.player.uniqueId)
    }

    private fun handleLethalDamage(player: Player) {
        val game = GameManager.getCurrentGame() ?: return
        val uuid = player.uniqueId
        val deathLocation = player.location.clone()

        val canRespawn = game.consumeDeathIfAvailable(uuid)
        val remainingAfterDeath = game.remainingDeaths(uuid)

        player.health = 1.0
        player.fireTicks = 0
        player.fallDistance = 0f
        player.gameMode = GameMode.SPECTATOR

        if (canRespawn) {
            pendingRespawnLocations[uuid] = deathLocation
            fixedSpectatorPlayers += uuid
            player.teleport(deathLocation)
            queueAutoRespawn(player)
            val remainText = remainingAfterDeath?.let { "<gray>(남은 데스카운트: $it)</gray>" } ?: ""
            player.sendMessage(
                miniMessage.deserialize(
                    "<yellow>30초 후 자동으로 부활합니다. <key:key.sneak> 키를 3초간 유지하면 즉시 부활할 수 있습니다.</yellow> $remainText"
                )
            )
            return
        }

        clearPlayerState(uuid)
        player.sendMessage(miniMessage.deserialize("<red>데스카운트를 모두 소모하여 관전자 모드로 전환됩니다.</red>"))
        if (shouldEndBattleBecauseEveryoneIsEliminated()) {
            GameManager.terminateCurrentGame("모든 플레이어가 데스카운트를 소진했습니다.")
        }
    }

    private fun queueAutoRespawn(player: Player) {
        val uuid = player.uniqueId
        autoRespawnTasks.remove(uuid)?.cancel()
        autoRespawnTasks[uuid] = BossProjectPlugin.instance.server.scheduler.runTaskLater(
            BossProjectPlugin.instance,
            Runnable {
                respawnPlayer(player)
            },
            AUTO_RESPAWN_TICKS
        )
    }

    private fun respawnPlayer(player: Player) {
        val uuid = player.uniqueId
        val location = pendingRespawnLocations[uuid]?.clone() ?: return
        clearPlayerState(uuid)

        val maxHealth = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        player.gameMode = GameMode.ADVENTURE
        player.teleport(location)
        player.health = maxHealth.coerceAtLeast(1.0)
        player.foodLevel = 20
        player.saturation = 20f
        player.fireTicks = 0
        player.fallDistance = 0f

        respawnInvulnerableUntilByPlayer[uuid] = System.currentTimeMillis() + RESPAWN_IMMUNE_MILLIS
    }

    private fun shouldEndBattleBecauseEveryoneIsEliminated(): Boolean {
        val game = GameManager.getCurrentGame() ?: return false
        return game.playerDatas
            .map { it.player }
            .filter { it.isOnline }
            .all { player ->
                val remaining = game.remainingDeaths(player.uniqueId) ?: Int.MAX_VALUE
                player.gameMode == GameMode.SPECTATOR && remaining <= 0 && !pendingRespawnLocations.containsKey(player.uniqueId)
            }
    }

    private fun clearPlayerState(uuid: UUID) {
        autoRespawnTasks.remove(uuid)?.cancel()
        sneakHoldRespawnTasks.remove(uuid)?.cancel()
        pendingRespawnLocations.remove(uuid)
        fixedSpectatorPlayers.remove(uuid)
        respawnInvulnerableUntilByPlayer.remove(uuid)
    }
}
