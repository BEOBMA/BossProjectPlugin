package org.beobma.bossProjectPlugin.job.registry

import org.beobma.bossProjectPlugin.job.Job
import org.beobma.bossProjectPlugin.job.list.ArcherJob
import org.beobma.bossProjectPlugin.job.list.GuardianJob
import org.beobma.bossProjectPlugin.job.list.MageJob
import org.beobma.bossProjectPlugin.job.list.WarriorJob
import kotlin.reflect.KClass

object JobRegistry {
    private val jobs: List<KClass<out Job>> = listOf(
        WarriorJob::class,
        ArcherJob::class,
        MageJob::class,
        GuardianJob::class
    )

    fun all(): List<KClass<out Job>> = jobs

    fun create(jobClass: KClass<out Job>): Job? {
        return jobClass.constructors.firstOrNull { it.parameters.isEmpty() }?.call()
    }
}
