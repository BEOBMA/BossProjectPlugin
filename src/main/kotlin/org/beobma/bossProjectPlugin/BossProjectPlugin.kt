package org.beobma.bossProjectPlugin

import org.beobma.bossProjectPlugin.command.BossPatternTestCommand
import org.beobma.bossProjectPlugin.command.StartGameCommand
import org.beobma.bossProjectPlugin.manager.BossInteractionDamageListener
import org.beobma.bossProjectPlugin.manager.GameStartInvitationManager
import org.bukkit.plugin.java.JavaPlugin

class BossProjectPlugin : JavaPlugin() {
    companion object {
        lateinit var instance: BossProjectPlugin
    }

    private lateinit var gameStartInvitationManager: GameStartInvitationManager

    override fun onEnable() {
        instance = this

        gameStartInvitationManager = GameStartInvitationManager(this)
        server.pluginManager.registerEvents(gameStartInvitationManager, this)
        server.pluginManager.registerEvents(BossInteractionDamageListener, this)
        getCommand("startgame")?.setExecutor(StartGameCommand(gameStartInvitationManager))
        getCommand("bosspatterntest")?.setExecutor(BossPatternTestCommand())

        loggerInfo("플러그인이 정상적으로 활성화되었습니다.")
    }

    override fun onDisable() {
        loggerInfo("플러그인이 정상적으로 비활성화되었습니다.")
    }

    fun loggerInfo(msg: String) {
        logger.info("[${this.pluginMeta.name}] $msg")
    }
}
