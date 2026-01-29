package com.github.arlowen.chainreactor.core

import com.github.arlowen.chainreactor.model.BuildModule
import com.github.arlowen.chainreactor.model.ModuleStatus
import com.github.arlowen.chainreactor.settings.ChainReactorSettings
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 流水线执行引擎
 * 负责按顺序串行执行构建模块
 */
class PipelineExecutor(private val project: Project) {

    companion object {
        private val LOG = thisLogger()
    }

    private val scriptRunner = ScriptRunner()
    private var isRunning = false
    private var shouldStop = false

    /**
     * 状态变更监听器
     */
    interface StatusListener {
        fun onStatusChanged(moduleId: String, status: ModuleStatus)
        fun onPipelineStarted()
        fun onPipelineFinished(success: Boolean, failedModule: BuildModule?)
    }

    /**
     * 执行构建流水线
     * @param modules 要执行的模块列表（按顺序）
     * @param consoleView 控制台视图
     * @param listener 状态监听器
     */
    suspend fun execute(
        modules: List<BuildModule>,
        consoleView: ConsoleView,
        listener: StatusListener
    ) {
        if (isRunning) {
            LOG.warn("流水线已在运行中")
            return
        }

        isRunning = true
        shouldStop = false
        val settings = ChainReactorSettings.getInstance()

        LOG.info("开始执行构建流水线，共 ${modules.size} 个模块")
        listener.onPipelineStarted()

        // 初始化所有模块状态为 PENDING
        modules.forEach { module ->
            listener.onStatusChanged(module.id, ModuleStatus.PENDING)
        }

        var failedModule: BuildModule? = null
        var allSuccess = true

        for ((index, module) in modules.withIndex()) {
            if (shouldStop) {
                LOG.info("用户请求停止，终止流水线")
                // 标记剩余模块为 SKIPPED
                modules.drop(index).forEach { m ->
                    listener.onStatusChanged(m.id, ModuleStatus.SKIPPED)
                }
                allSuccess = false
                break
            }

            LOG.info("执行模块 ${index + 1}/${modules.size}: ${module.name}")
            listener.onStatusChanged(module.id, ModuleStatus.RUNNING)

            // 在 IO 线程执行脚本
            val result = withContext(Dispatchers.IO) {
                scriptRunner.runScript(
                    scriptPath = module.scriptPath,
                    workingDir = module.workingDir,
                    customCommand = module.customCommand,
                    timeoutSeconds = settings.timeoutSeconds,
                    consoleView = consoleView
                )
            }

            if (result.success) {
                LOG.info("模块 ${module.name} 执行成功")
                listener.onStatusChanged(module.id, ModuleStatus.SUCCESS)
            } else {
                LOG.warn("模块 ${module.name} 执行失败，退出码: ${result.exitCode}")
                listener.onStatusChanged(module.id, ModuleStatus.FAILED)
                failedModule = module
                allSuccess = false

                if (!settings.continueOnFailure) {
                    // 标记后续模块为 SKIPPED
                    modules.drop(index + 1).forEach { m ->
                        LOG.info("跳过模块: ${m.name}")
                        listener.onStatusChanged(m.id, ModuleStatus.SKIPPED)
                    }
                    break
                }
            }
        }

        isRunning = false
        LOG.info("构建流水线执行完成，成功: $allSuccess")
        listener.onPipelineFinished(allSuccess, failedModule)
    }

    /**
     * 停止执行
     */
    fun stop() {
        LOG.info("请求停止流水线")
        shouldStop = true
    }

    /**
     * 是否正在运行
     */
    fun isRunning(): Boolean = isRunning
}
