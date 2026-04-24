package org.beobma.bossProjectPlugin.command

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.bossProjectPlugin.manager.GameManager
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class EndGameCommand : CommandExecutor {
    private val miniMessage = MiniMessage.miniMessage()

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        val player = sender as? Player
        if (player != null && !player.isOp) {
            player.sendMessage(miniMessage.deserialize("<red>OP 권한이 있어야 사용할 수 있습니다.</red>"))
            return true
        }

        val reason = if (args.isEmpty()) {
            "관리자 명령어 요청"
        } else {
            args.joinToString(" ")
        }

        val ended = GameManager.terminateCurrentGame(reason)
        if (!ended) {
            sender.sendMessage(miniMessage.deserialize("<yellow>현재 진행 중인 게임이 없습니다.</yellow>"))
        }

        return true
    }
}
