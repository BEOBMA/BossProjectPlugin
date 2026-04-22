package org.beobma.bossProjectPlugin.command

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.bossProjectPlugin.manager.GameManager
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class BossPatternTestCommand : CommandExecutor {
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
            player.sendMessage(miniMessage.deserialize("<red>OP 권한이 있어야 사용할 수 있습니다.</red>"))
            return true
        }

        when (args.firstOrNull()?.lowercase()) {
            null, "status" -> {
                val enabled = GameManager.isPatternOnlyTestMode()
                val statusText = if (enabled) "<green>활성화</green>" else "<red>비활성화</red>"
                player.sendMessage(miniMessage.deserialize("<yellow>패시브 제외 패턴 테스트 모드:</yellow> $statusText"))
            }

            "on" -> {
                GameManager.setPatternOnlyTestMode(true)
                player.sendMessage(miniMessage.deserialize("<green>패시브 제외 패턴 테스트 모드를 활성화했습니다.</green>"))
                player.sendMessage(miniMessage.deserialize("<gray>이제 보스 루프에서 패시브는 실행되지 않고 패턴 스킬만 실행됩니다.</gray>"))
            }

            "off" -> {
                GameManager.setPatternOnlyTestMode(false)
                player.sendMessage(miniMessage.deserialize("<yellow>패시브 제외 패턴 테스트 모드를 비활성화했습니다.</yellow>"))
            }

            else -> {
                player.sendMessage(miniMessage.deserialize("<red>사용법: /bosspatterntest [on|off|status]</red>"))
            }
        }

        return true
    }
}
