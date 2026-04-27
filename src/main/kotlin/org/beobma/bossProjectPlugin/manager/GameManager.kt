package org.beobma.bossProjectPlugin.manager

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.bossProjectPlugin.BossProjectPlugin
import org.beobma.bossProjectPlugin.entity.enemy.EnemyStatus
import org.beobma.bossProjectPlugin.entity.player.PlayerData
import org.beobma.bossProjectPlugin.game.Game
import org.beobma.bossProjectPlugin.entity.enemy.list.EnemyRegistry
import org.beobma.bossProjectPlugin.job.Job
import org.beobma.bossProjectPlugin.job.registry.JobRegistry
import org.bukkit.Bukkit
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.Locale
import kotlin.math.ceil
import kotlin.reflect.KClass
import kotlin.random.Random

object GameManager : Listener {
    private const val JOB_SELECT_TIMEOUT_TICKS = 20L * 30L
    private const val PHASE_TRANSITION_DELAY_TICKS = 20L * 5L
    private const val INVENTORY_SIZE = 54
    private const val PAGE_CAPACITY = 44

    private var listenerRegistered = false
    private var currentGame: Game? = null
    private var activeSession: JobSelectionSession? = null
    private var bossLoopTask: BukkitTask? = null
    private var phaseTransitionTask: BukkitTask? = null
    private var bossBar: BossBar? = null
    private var patternOnlyTestMode: Boolean = false
    private var phaseTransitioning: Boolean = false

    private val miniMessage = MiniMessage.miniMessage()

    private val randomKey by lazy { org.bukkit.NamespacedKey(BossProjectPlugin.instance, "job_random") }
    private val pagePrevKey by lazy { org.bukkit.NamespacedKey(BossProjectPlugin.instance, "job_prev") }
    private val pageNextKey by lazy { org.bukkit.NamespacedKey(BossProjectPlugin.instance, "job_next") }
    private val jobClassKey by lazy { org.bukkit.NamespacedKey(BossProjectPlugin.instance, "job_class") }

    fun startGame(players: Collection<Player>) {
        ensureListenerRegistered()
        clearBossBar()
        PlayerDeathLifecycleManager.clearAllStates()
        PlayerStatusEffectManager.clearAllStates()

        val game = Game()
        players.forEach { player ->
            game.playerDatas.add(PlayerData(player, game))
        }

        game.start()
    }

    fun terminateCurrentGame(reason: String, broadcast: Boolean = true): Boolean {
        val game = currentGame ?: return false

        activeSession?.terminate()
        activeSession = null

        phaseTransitionTask?.cancel()
        phaseTransitionTask = null
        phaseTransitioning = false
        bossLoopTask?.cancel()
        bossLoopTask = null
        clearBossBar()
        clearPlayerInvulnerability(game)
        PlayerDeathLifecycleManager.clearAllStates()
        PlayerStatusEffectManager.clearAllStates()

        if (game.isBossInitialized) {
            game.bossData.patternSkills.forEach { it.onGameEnd() }
            game.bossData.passives.forEach { it.onGameEnd() }
        }

        if (game.isBossInitialized && game.bossData.entity.isValid) {
            game.bossData.entity.remove()
        }

        Bukkit.getOnlinePlayers().forEach { onlinePlayer ->
            val holder = onlinePlayer.openInventory.topInventory.holder as? JobSelectionHolder
            if (holder?.session != null) {
                onlinePlayer.closeInventory()
            }
        }

        currentGame = null

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "time set noon")

        if (broadcast) {
            Bukkit.broadcast(miniMessage.deserialize("<red>진행 중인 게임이 종료되었습니다: $reason</red>"))
        }
        return true
    }

    fun Game.start() {
        currentGame = this
        startJobSelection(this@start)
    }

    fun getCurrentGame(): Game? = currentGame

    fun setPatternOnlyTestMode(enabled: Boolean) {
        patternOnlyTestMode = enabled
    }

    fun isPatternOnlyTestMode(): Boolean = patternOnlyTestMode

    @EventHandler
    fun onJobInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? JobSelectionHolder ?: return
        val session = activeSession
        if (session !== holder.session) {
            event.isCancelled = true
            return
        }

        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        session.handleClick(player, event.currentItem)
    }

    @EventHandler
    fun onJobInventoryClose(event: InventoryCloseEvent) {
        val holder = event.view.topInventory.holder as? JobSelectionHolder ?: return
        val session = activeSession
        if (session !== holder.session) {
            return
        }

        val player = event.player as? Player ?: return
        session.onClose(player)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val game = currentGame ?: return
        val leaver = game.playerDatas.any { it.player.uniqueId == event.player.uniqueId }
        if (!leaver) return

        terminateCurrentGame("${event.player.name} 님이 서버를 떠났습니다.")
    }

    @EventHandler
    fun onPlayerKick(event: PlayerKickEvent) {
        val game = currentGame ?: return
        val leaver = game.playerDatas.any { it.player.uniqueId == event.player.uniqueId }
        if (!leaver) return

        terminateCurrentGame("${event.player.name} 님이 서버에서 퇴장되었습니다.")
    }

    private fun ensureListenerRegistered() {
        if (listenerRegistered) return

        Bukkit.getPluginManager().registerEvents(this, BossProjectPlugin.instance)
        listenerRegistered = true
    }

    private fun startJobSelection(game: Game) {
        if (activeSession != null) {
            Bukkit.broadcast(miniMessage.deserialize("<red>이미 직업 선택이 진행 중입니다.</red>"))
            return
        }

        val totalPlayers = game.playerDatas.size
        val available = JobRegistry.all().size

        if (available < totalPlayers) {
            Bukkit.broadcast(
                miniMessage.deserialize("<red>직업 수가 플레이어 수보다 적어 게임을 시작할 수 없습니다. (${available}/${totalPlayers})</red>")
            )
            currentGame = null
            return
        }

        val session = JobSelectionSession(game)
        activeSession = session
        session.openAll()
    }

    private fun finalizeAfterSelection(game: Game) {
        Bukkit.getOnlinePlayers().forEach { it.closeInventory() }

        val enemyEntry = EnemyRegistry.randomEnemy()
        val mapData = enemyEntry.mapData
        val spawnLocation = mapData.spawnLocation()

        game.setupMap(mapData)
        game.setupBoss(enemyEntry.factory(game))
        game.initializeBattleState()

        game.playerDatas.forEach { playerData ->
            val player = playerData.player
            player.teleport(spawnLocation)
            player.level = game.remainingDeaths(player.uniqueId) ?: 0
            player.exp = 0f
        }

        Bukkit.broadcast(miniMessage.deserialize("<green>직업 선택이 완료되어 보스전 맵으로 이동합니다.</green>"))
        Bukkit.broadcast(miniMessage.deserialize("<yellow>선택된 보스: ${game.bossData.displayName}</yellow>"))
        initializeBossBar(game)
        startBossLoop(game)
    }

    fun applyBossInteractionDamage(attacker: Entity, damaged: Entity, damageAmount: Double) {
        val game = currentGame ?: return
        if (damaged.uniqueId != game.bossData.entity.uniqueId) return
        if (!isPlayerDamageSource(attacker)) return
        if (damageAmount <= 0.0) return

        game.bossData.health = (game.bossData.health - damageAmount).coerceAtLeast(0.0)
        updateBossBar(game)

        if (game.bossData.health <= 0.0) {
            handleBossPhaseCleared(game)
        }
    }

    private fun isPlayerDamageSource(entity: Entity): Boolean {
        if (entity is Player) return true
        if (entity is Projectile) {
            return entity.shooter is Player
        }
        return false
    }

    private fun startBossLoop(game: Game) {
        bossLoopTask?.cancel()
        bossLoopTask = object : BukkitRunnable() {
            override fun run() {
                if (currentGame !== game) {
                    cancel()
                    return
                }

                val status = game.bossData.status as? EnemyStatus
                status?.let { it.elapsedTicks += 20L }
                updateBossBar(game)

                if (phaseTransitioning) return

                if (!patternOnlyTestMode) {
                    game.bossData.passives.forEach { it.onTick() }
                }
                game.bossData.patternSkills.forEach { it.use() }
            }
        }.runTaskTimer(BossProjectPlugin.instance, 20L, 20L)
    }

    private fun handleBossPhaseCleared(game: Game) {
        val clearedBoss = game.bossData
        val nextPhaseBoss = clearedBoss.createNextPhase()
        if (nextPhaseBoss == null) {
            terminateCurrentGame("${clearedBoss.displayName} 처치 완료!", broadcast = true)
            return
        }

        phaseTransitioning = true
        clearedBoss.patternSkills.forEach { it.onGameEnd() }
        if (clearedBoss.entity.isValid) {
            clearedBoss.entity.remove()
        }

        game.playerDatas
            .map { it.player }
            .filter { it.isOnline }
            .forEach { it.isInvulnerable = true }

        Bukkit.broadcast(
            miniMessage.deserialize(
                "<gold>${clearedBoss.displayName} ${clearedBoss.phase}페이즈 종료!</gold> <yellow>5초 후 ${nextPhaseBoss.phase}페이즈를 시작합니다.</yellow>"
            )
        )

        phaseTransitionTask?.cancel()
        phaseTransitionTask = object : BukkitRunnable() {
            override fun run() {
                if (currentGame !== game) {
                    cancel()
                    return
                }

                game.carryOverDeathState(nextPhaseBoss.mapData)
                game.setupMap(nextPhaseBoss.mapData)
                game.setupBoss(nextPhaseBoss)
                game.playerDatas
                    .map { it.player }
                    .filter { it.isOnline }
                    .forEach { player ->
                        player.teleport(nextPhaseBoss.mapData.spawnLocation())
                        player.isInvulnerable = false
                    }

                initializeBossBar(game)
                phaseTransitioning = false
                phaseTransitionTask = null

                Bukkit.broadcast(
                    miniMessage.deserialize(
                        "<red>${nextPhaseBoss.phase}페이즈 시작!</red>"
                    )
                )
            }
        }.runTaskLater(BossProjectPlugin.instance, PHASE_TRANSITION_DELAY_TICKS)
    }

    private fun clearPlayerInvulnerability(game: Game) {
        game.playerDatas
            .map { it.player }
            .filter { it.isOnline }
            .forEach { it.isInvulnerable = false }
    }

    private fun initializeBossBar(game: Game) {
        clearBossBar()
        val created = Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID)
        created.isVisible = true
        game.playerDatas
            .map { it.player }
            .filter { it.isOnline }
            .forEach { created.addPlayer(it) }
        bossBar = created
        updateBossBar(game)
    }

    private fun updateBossBar(game: Game) {
        val targetBar = bossBar ?: return
        val progress = if (game.bossData.maxHealth <= 0.0) {
            0.0
        } else {
            (game.bossData.health / game.bossData.maxHealth).coerceIn(0.0, 1.0)
        }
        val percent = progress * 100.0
        targetBar.setTitle("${game.bossData.displayName} P${game.bossData.phase} ${"%.1f".format(Locale.US, percent)}%")
        targetBar.progress = progress
    }

    private fun clearBossBar() {
        bossBar?.removeAll()
        bossBar = null
    }


    private class JobSelectionSession(private val game: Game) {
        private val allJobClasses = JobRegistry.all()
        private val selectedJobsByPlayer: MutableMap<PlayerData, KClass<out Job>> = mutableMapOf()
        private val pagesByPlayer: MutableMap<PlayerData, Int> = mutableMapOf()
        private val inventoriesByPlayer: MutableMap<PlayerData, Inventory> = mutableMapOf()
        private val timeoutTask: BukkitTask

        init {
            timeoutTask = object : BukkitRunnable() {
                override fun run() {
                    if (activeSession !== this@JobSelectionSession) {
                        cancel()
                        return
                    }

                    autoSelectForPendingPlayers()
                    completeIfDone()
                }
            }.runTaskLater(BossProjectPlugin.instance, JOB_SELECT_TIMEOUT_TICKS)
        }

        fun openAll() {
            val totalSeconds = JOB_SELECT_TIMEOUT_TICKS / 20

            game.playerDatas.forEach { playerData ->
                pagesByPlayer[playerData] = 0
                val inventory = createInventory(playerData, 0)
                inventoriesByPlayer[playerData] = inventory
                playerData.player.openInventory(inventory)
                playerData.player.sendMessage(
                    miniMessage.deserialize("<yellow>${totalSeconds}초 안에 직업을 선택해주세요. 미선택 시 자동 랜덤 선택됩니다.</yellow>")
                )
            }
        }

        fun onClose(player: Player) {
            val playerData = game.playerDatas.firstOrNull { it.player.uniqueId == player.uniqueId } ?: return
            if (selectedJobsByPlayer[playerData] != null) return

            Bukkit.getScheduler().runTask(BossProjectPlugin.instance, Runnable {
                if (activeSession !== this@JobSelectionSession) return@Runnable
                if (selectedJobsByPlayer[playerData] != null) return@Runnable
                val page = pagesByPlayer[playerData] ?: 0
                val inventory = createInventory(playerData, page)
                inventoriesByPlayer[playerData] = inventory
                player.openInventory(inventory)
            })
        }

        fun handleClick(player: Player, currentItem: ItemStack?) {
            val playerData = game.playerDatas.firstOrNull { it.player.uniqueId == player.uniqueId } ?: return
            if (selectedJobsByPlayer[playerData] != null) return
            val item = currentItem ?: return
            val meta = item.itemMeta ?: return
            val data = meta.persistentDataContainer

            when {
                data.has(pagePrevKey, PersistentDataType.INTEGER) -> {
                    val currentPage = pagesByPlayer[playerData] ?: 0
                    openPage(playerData, (currentPage - 1).coerceAtLeast(0))
                }

                data.has(pageNextKey, PersistentDataType.INTEGER) -> {
                    val currentPage = pagesByPlayer[playerData] ?: 0
                    val maxPage = maxPage()
                    openPage(playerData, (currentPage + 1).coerceAtMost(maxPage))
                }

                data.has(randomKey, PersistentDataType.INTEGER) -> {
                    val randomJob = pickRandomAvailableJob()
                    if (randomJob == null) {
                        player.sendMessage(miniMessage.deserialize("<red>선택 가능한 직업이 없습니다.</red>"))
                        return
                    }
                    selectJob(playerData, randomJob, true)
                }

                data.has(jobClassKey, PersistentDataType.STRING) -> {
                    val className = data.get(jobClassKey, PersistentDataType.STRING) ?: return
                    val jobClass = allJobClasses.firstOrNull { it.qualifiedName == className } ?: return
                    if (isJobTaken(jobClass)) {
                        player.sendMessage(miniMessage.deserialize("<red>이미 다른 플레이어가 선택한 직업입니다.</red>"))
                        refreshAllInventories()
                        return
                    }
                    selectJob(playerData, jobClass, false)
                }
            }
        }

        private fun selectJob(playerData: PlayerData, jobClass: KClass<out Job>, random: Boolean) {
            if (isJobTaken(jobClass)) {
                playerData.player.sendMessage(miniMessage.deserialize("<red>이미 선택된 직업입니다.</red>"))
                return
            }

            val job = JobRegistry.create(jobClass)
            if (job == null) {
                playerData.player.sendMessage(miniMessage.deserialize("<red>직업 생성에 실패했습니다.</red>"))
                return
            }

            selectedJobsByPlayer[playerData] = jobClass
            playerData.selectJob(job)

            val selectedTypeText = if (random) "랜덤" else "직접"
            playerData.player.sendMessage(
                miniMessage.deserialize("<green>${selectedTypeText} 선택 완료: ${job.name}</green>")
            )

            refreshAllInventories()
            completeIfDone()
        }

        private fun autoSelectForPendingPlayers() {
            game.playerDatas.forEach { playerData ->
                if (selectedJobsByPlayer[playerData] != null) return@forEach
                val randomJob = pickRandomAvailableJob() ?: return@forEach
                selectJob(playerData, randomJob, true)
            }
        }

        private fun completeIfDone() {
            if (selectedJobsByPlayer.size != game.playerDatas.size) {
                return
            }

            timeoutTask.cancel()
            activeSession = null

            finalizeAfterSelection(game)
        }

        fun terminate() {
            timeoutTask.cancel()
        }

        private fun openPage(playerData: PlayerData, page: Int) {
            pagesByPlayer[playerData] = page
            val inventory = createInventory(playerData, page)
            inventoriesByPlayer[playerData] = inventory
            playerData.player.openInventory(inventory)
        }

        private fun refreshAllInventories() {
            game.playerDatas.forEach { playerData ->
                if (selectedJobsByPlayer[playerData] != null) return@forEach
                val page = pagesByPlayer[playerData] ?: 0
                val opened = playerData.player.openInventory.topInventory
                val holder = opened.holder as? JobSelectionHolder
                if (holder?.session !== this) return@forEach

                val inventory = createInventory(playerData, page)
                inventoriesByPlayer[playerData] = inventory
                playerData.player.openInventory(inventory)
            }
        }

        private fun createInventory(playerData: PlayerData, page: Int): Inventory {
            val holder = JobSelectionHolder(this, playerData)
            val inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, miniMessage.deserialize("직업 선택 (${page + 1}/${maxPage() + 1})"))
            holder.holderInventory = inventory

            fillClassItems(inventory, page)
            fillRandomItem(inventory)
            fillNavigation(inventory, page)

            return inventory
        }

        private fun fillClassItems(inventory: Inventory, page: Int) {
            val offset = page * PAGE_CAPACITY
            val slice = allJobClasses.drop(offset).take(PAGE_CAPACITY)
            slice.forEachIndexed { index, jobClass ->
                val slot = index + 1
                val item = if (isJobTaken(jobClass)) {
                    createLockedItem(jobClass)
                } else {
                    createJobItem(jobClass)
                }
                inventory.setItem(slot, item)
            }
        }

        private fun fillRandomItem(inventory: Inventory) {
            val item = ItemStack(Material.NETHER_STAR)
            val meta = item.itemMeta
            meta.displayName(miniMessage.deserialize("<aqua><bold>무작위 직업 선택</bold></aqua>"))
            meta.lore(
                listOf(
                    miniMessage.deserialize("<gray>선택 가능한 직업 중에서</gray>"),
                    miniMessage.deserialize("<gray>무작위로 하나를 선택합니다.</gray>")
                )
            )
            meta.persistentDataContainer.set(randomKey, PersistentDataType.INTEGER, 1)
            item.itemMeta = meta
            inventory.setItem(0, item)
        }

        private fun fillNavigation(inventory: Inventory, page: Int) {
            if (allJobClasses.size <= PAGE_CAPACITY) {
                return
            }

            if (page > 0) {
                val prev = ItemStack(Material.ARROW)
                val meta = prev.itemMeta
                meta.displayName(miniMessage.deserialize("<yellow>이전 페이지</yellow>"))
                meta.persistentDataContainer.set(pagePrevKey, PersistentDataType.INTEGER, 1)
                prev.itemMeta = meta
                inventory.setItem(45, prev)
            }

            if (page < maxPage()) {
                val next = ItemStack(Material.ARROW)
                val meta = next.itemMeta
                meta.displayName(miniMessage.deserialize("<yellow>다음 페이지</yellow>"))
                meta.persistentDataContainer.set(pageNextKey, PersistentDataType.INTEGER, 1)
                next.itemMeta = meta
                inventory.setItem(53, next)
            }
        }

        private fun createJobItem(jobClass: KClass<out Job>): ItemStack {
            val job = JobRegistry.create(jobClass) ?: return ItemStack(Material.BARRIER)
            val item = job.toItem()
            val meta = item.itemMeta ?: return item
            meta.persistentDataContainer.set(jobClassKey, PersistentDataType.STRING, jobClass.qualifiedName ?: jobClass.simpleName.orEmpty())
            item.itemMeta = meta
            return item
        }

        private fun createLockedItem(jobClass: KClass<out Job>): ItemStack {
            val base = createJobItem(jobClass)
            val item = ItemStack(Material.BARRIER)
            val meta = item.itemMeta
            meta.displayName(miniMessage.deserialize("<red>선택 불가</red>"))

            val baseName = base.itemMeta?.displayName() ?: miniMessage.deserialize("<gray>알 수 없는 직업</gray>")
            meta.lore(
                listOf(
                    miniMessage.deserialize("<gray>이미 다른 플레이어가 선택한 직업입니다.</gray>"),
                    baseName
                )
            )
            item.itemMeta = meta
            return item
        }

        private fun isJobTaken(jobClass: KClass<out Job>): Boolean = selectedJobsByPlayer.values.contains(jobClass)

        private fun pickRandomAvailableJob(): KClass<out Job>? {
            val available = allJobClasses.filterNot(::isJobTaken)
            if (available.isEmpty()) return null
            return available[Random.nextInt(available.size)]
        }

        private fun maxPage(): Int {
            if (allJobClasses.isEmpty()) return 0
            return ceil(allJobClasses.size / PAGE_CAPACITY.toDouble()).toInt() - 1
        }
    }

    private class JobSelectionHolder(val session: JobSelectionSession, val playerData: PlayerData) : InventoryHolder {
        lateinit var holderInventory: Inventory
        override fun getInventory(): Inventory = holderInventory
    }
}
