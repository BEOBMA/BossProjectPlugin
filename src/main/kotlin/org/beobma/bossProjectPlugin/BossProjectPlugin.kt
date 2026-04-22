package org.beobma.bossProjectPlugin

import org.bukkit.plugin.java.JavaPlugin

class BossProjectPlugin : JavaPlugin() {
    companion object {
        lateinit var instance: BossProjectPlugin
    }

    override fun onEnable() {
        instance = this

        loggerInfo("플러그인이 정상적으로 활성화되었습니다.")
    }

    override fun onDisable() {
        loggerInfo("플러그인이 정상적으로 비활성화되었습니다.")
    }

    fun loggerInfo(msg: String) {
        logger.info("[${this.pluginMeta.name}] $msg")
    }
}
