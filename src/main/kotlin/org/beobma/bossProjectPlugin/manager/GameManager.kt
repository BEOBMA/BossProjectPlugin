package org.beobma.bossProjectPlugin.manager

import org.beobma.bossProjectPlugin.game.Game

object GameManager {
    private var currentGame: Game? = null

    fun Game.start() {
        currentGame = this

    }

    fun getCurrentGame(): Game? {
        return currentGame
    }
}