package org.beobma.bossProjectPlugin.manager

import org.beobma.bossProjectPlugin.entity.player.PlayerData
import org.beobma.bossProjectPlugin.game.Game
import org.bukkit.entity.Player

object GameManager {
    private var currentGame: Game? = null

    fun startGame(players: Collection<Player>) {
        val game = Game()
        players.forEach { player ->
            game.playerDatas.add(PlayerData(player, game))
        }

        game.start()
    }

    fun Game.start() {
        currentGame = this

        TODO("게임 시작 로직은 추후 구현 예정")
    }

    fun getCurrentGame(): Game? {
        return currentGame
    }
}
