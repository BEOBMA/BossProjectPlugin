package org.beobma.bossProjectPlugin.command

import org.beobma.bossProjectPlugin.manager.GameManager
import org.beobma.bossProjectPlugin.manager.GameStartInvitationManager
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class StartGameCommand(
    private val invitationManager: GameStartInvitationManager
) : CommandExecutor {
    private val miniMessage = MiniMessage.miniMessage()

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage(miniMessage.deserialize("<red>플레이어만 사용할 수 있는 명령어입니다.</red>"))
            return true
        }

        if (!player.isOp) {
            player.sendMessage(miniMessage.deserialize("<red>OP 권한이 있어야 게임을 시작할 수 있습니다.</red>"))
            return true
        }

        if (GameManager.getCurrentGame() != null) {
            player.sendMessage(miniMessage.deserialize("<red>이미 진행 중인 게임이 있습니다.</red>"))
            return true
        }

        if (invitationManager.hasActiveSession()) {
            player.sendMessage(miniMessage.deserialize("<red>이미 게임 시작 투표가 진행 중입니다.</red>"))
            return true
        }

        invitationManager.requestStart(player, player.server.onlinePlayers.toList())
        return true
    }
}
