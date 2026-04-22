package org.beobma.bossProjectPlugin.job.registry

import org.beobma.bossProjectPlugin.job.Job
import kotlin.reflect.KClass

object JobRegistry {
    private val jobs: List<KClass<out Job>> = listOf(

    )

    fun all(): List<KClass<out Job>> = jobs

    fun create(jobClass: KClass<out Job>): Job? {
        return jobClass.constructors.firstOrNull { it.parameters.isEmpty() }?.call()
    }
}
