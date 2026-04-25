package org.beobma.bossProjectPlugin.entity.enemy

import org.beobma.bossProjectPlugin.entity.EntityData
import org.beobma.bossProjectPlugin.entity.enemy.skill.BossPassive
import org.beobma.bossProjectPlugin.entity.enemy.skill.PatternSkill
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import java.util.UUID

abstract class EnemyData : EntityData() {
    abstract val maxHealth: Double
    abstract var health: Double
    open val phase: Int = 1
    open val displayName: String
        get() = this::class.simpleName ?: "Unknown Boss"

    abstract val passives: List<BossPassive>
    abstract val patternSkills: List<PatternSkill>
    abstract val mapData: BossBattleMapData

    open val interactionTag: String = BossCombatConstants.BOSS_INTERACTION_TAG
    open val interactionSummonCommand: String? = null
    open val interactionSummonCommands: List<String>
        get() = interactionSummonCommand?.let(::listOf) ?: emptyList()
    open fun createNextPhase(): EnemyData? = null

    private var trackedInteractionEntityIds: Set<UUID> = emptySet()

    fun interactionEntityIds(): Set<UUID> = trackedInteractionEntityIds

    fun resolveInteractionEntities(): List<Entity> {
        if (trackedInteractionEntityIds.isEmpty()) return emptyList()

        val world = mapData.world()
        return world.entities.filter { trackedInteractionEntityIds.contains(it.uniqueId) }
    }

    protected fun resolveBossEntity(): Entity {
        val world = mapData.world()
        if (interactionSummonCommands.isNotEmpty()) {
            val summonTags = interactionSummonCommands.map { summonCommand ->
                val summonTag = "${interactionTag}_${UUID.randomUUID().toString().replace("-", "")}"
                val commandWithTag = appendSummonTag(summonCommand, summonTag)
                Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    "execute in ${world.key.asString()} run ${commandWithTag.removePrefix("/")}"
                )
                summonTag
            }

            val resolvedEntities = summonTags.mapNotNull { summonTag ->
                world.entities.firstOrNull {
                    it.scoreboardTags.contains(interactionTag) && it.scoreboardTags.contains(summonTag)
                }
            }
            trackedInteractionEntityIds = resolvedEntities.map { it.uniqueId }.toSet()

            if (resolvedEntities.isEmpty()) {
                error("새로 소환된 보스 엔티티 태그를 월드 '${world.name}' 에서 찾지 못했습니다.")
            }

            val mainSummonTag = summonTags.first()
            return resolvedEntities.firstOrNull {
                it.scoreboardTags.contains(interactionTag) && it.scoreboardTags.contains(mainSummonTag)
            } ?: error("새로 소환된 보스 엔티티 태그 '$mainSummonTag' 를 월드 '${world.name}' 에서 찾지 못했습니다.")
        }

        val resolvedEntity = world.entities.firstOrNull { it.scoreboardTags.contains(interactionTag) }
            ?: error("보스 엔티티 태그 '$interactionTag' 를 가진 엔티티를 월드 '${world.name}' 에서 찾지 못했습니다.")
        trackedInteractionEntityIds = setOf(resolvedEntity.uniqueId)
        return resolvedEntity
    }

    private fun appendSummonTag(command: String, summonTag: String): String {
        val serializedTag = "\"$summonTag\""
        val tagsRegex = Regex("""Tags\s*:\s*\[(.*?)]""")
        if (tagsRegex.containsMatchIn(command)) {
            return command.replace(tagsRegex) { match ->
                val existingTags = match.groupValues[1].trim()
                val mergedTags = if (existingTags.isEmpty()) serializedTag else "$existingTags,$serializedTag"
                "Tags:[$mergedTags]"
            }
        }

        val nbtStart = command.indexOf('{')
        val nbtEnd = command.lastIndexOf('}')
        if (nbtStart != -1 && nbtEnd > nbtStart) {
            return buildString {
                append(command.substring(0, nbtEnd))
                append(",Tags:[")
                append(serializedTag)
                append(']')
                append(command.substring(nbtEnd))
            }
        }

        return "$command {Tags:[$serializedTag]}"
    }
}
