package com.github.arlowen.chainreactor.core

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Key
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Shell 脚本执行器
 * 封装 GeneralCommandLine + OSProcessHandler，支持同步/异步执行
 */
class ScriptRunner {

    companion object {
        private val LOG = thisLogger()
    }

    @Volatile
    private var currentProcessHandler: OSProcessHandler? = null

    /**
     * 脚本执行结果
     */
    data class ScriptResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val success: Boolean = exitCode == 0
    )

    /**
     * 同步执行脚本
     * @param scriptPath 脚本完整路径
     * @param workingDir 工作目录
     * @param customCommand 自定义执行命令，为空时使用 scriptPath
     * @param timeoutSeconds 超时时间（秒）
     * @param consoleView 可选的控制台视图，用于实时输出
     * @return 执行结果
     */
    fun runScript(
        scriptPath: String,
        workingDir: String,
        customCommand: String? = null,
        timeoutSeconds: Long = 300,
        consoleView: ConsoleView? = null
    ): ScriptResult {
        val workingDirFile = File(workingDir)
        if (!workingDirFile.exists() || !workingDirFile.isDirectory) {
            val errorMsg = "工作目录不存在或不可用: $workingDir"
            LOG.error(errorMsg)
            consoleView?.print("❌ $errorMsg\n", ConsoleViewContentType.ERROR_OUTPUT)
            return ScriptResult(-1, "", errorMsg, false)
        }

        val hasCustomCommand = !customCommand.isNullOrBlank()
        if (!hasCustomCommand && scriptPath.isBlank()) {
            val errorMsg = "未配置可执行脚本或命令"
            LOG.error(errorMsg)
            consoleView?.print("❌ $errorMsg\n", ConsoleViewContentType.ERROR_OUTPUT)
            return ScriptResult(-1, "", errorMsg, false)
        }

        val resolvedScriptPath = if (!hasCustomCommand) {
            val scriptFile = File(scriptPath)
            val resolvedFile = if (scriptFile.isAbsolute) {
                scriptFile
            } else {
                File(workingDirFile, scriptPath)
            }

            if (!resolvedFile.exists()) {
                val errorMsg = "脚本文件不存在: ${resolvedFile.absolutePath}"
                LOG.error(errorMsg)
                consoleView?.print("❌ $errorMsg\n", ConsoleViewContentType.ERROR_OUTPUT)
                return ScriptResult(-1, "", errorMsg, false)
            }

            if (!resolvedFile.canExecute()) {
                resolvedFile.setExecutable(true)
            }
            resolvedFile.absolutePath
        } else {
            null
        }

        val commandToRun = customCommand?.takeIf { it.isNotBlank() } ?: resolvedScriptPath.orEmpty()
        LOG.info("开始执行命令: $commandToRun, 工作目录: $workingDir")

        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val latch = CountDownLatch(1)
        var exitCode = -1

        try {
            // 构建命令行
            val commandLine = GeneralCommandLine()
                .withExePath("/bin/bash")
                .withParameters("-c", commandToRun)
                .withWorkDirectory(workingDir)
                .withEnvironment(System.getenv())

            // 创建进程处理器
            val processHandler = OSProcessHandler(commandLine)
            currentProcessHandler = processHandler

            // 添加进程监听器
            processHandler.addProcessListener(object : ProcessAdapter() {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    val text = event.text
                    when (outputType) {
                        ProcessOutputTypes.STDOUT -> {
                            stdout.append(text)
                            consoleView?.print(text, ConsoleViewContentType.NORMAL_OUTPUT)
                        }
                        ProcessOutputTypes.STDERR -> {
                            stderr.append(text)
                            consoleView?.print(text, ConsoleViewContentType.ERROR_OUTPUT)
                        }
                        ProcessOutputTypes.SYSTEM -> {
                            consoleView?.print(text, ConsoleViewContentType.SYSTEM_OUTPUT)
                        }
                    }
                }

                override fun processTerminated(event: ProcessEvent) {
                    exitCode = event.exitCode
                    LOG.info("脚本执行完成: $scriptPath, 退出码: $exitCode")
                    currentProcessHandler = null
                    latch.countDown()
                }
            })

            // 开始执行
            consoleView?.print("▶ 执行: $commandToRun\n", ConsoleViewContentType.SYSTEM_OUTPUT)
            consoleView?.print("📁 工作目录: $workingDir\n", ConsoleViewContentType.SYSTEM_OUTPUT)
            consoleView?.print("─".repeat(50) + "\n", ConsoleViewContentType.SYSTEM_OUTPUT)

            processHandler.startNotify()

            // 等待执行完成
            val completed = latch.await(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                processHandler.destroyProcess()
                currentProcessHandler = null
                val errorMsg = "脚本执行超时 (${timeoutSeconds}s): $scriptPath"
                LOG.warn(errorMsg)
                consoleView?.print("\n⏱ $errorMsg\n", ConsoleViewContentType.ERROR_OUTPUT)
                return ScriptResult(-1, stdout.toString(), errorMsg, false)
            }

            // 输出结果
            consoleView?.print("─".repeat(50) + "\n", ConsoleViewContentType.SYSTEM_OUTPUT)
            if (exitCode == 0) {
                consoleView?.print("✅ 执行成功 (退出码: 0)\n\n", ConsoleViewContentType.SYSTEM_OUTPUT)
            } else {
                consoleView?.print("❌ 执行失败 (退出码: $exitCode)\n\n", ConsoleViewContentType.ERROR_OUTPUT)
            }

            return ScriptResult(exitCode, stdout.toString(), stderr.toString())

        } catch (e: Exception) {
            val errorMsg = "脚本执行异常: ${e.message}"
            LOG.error(errorMsg, e)
            consoleView?.print("❌ $errorMsg\n", ConsoleViewContentType.ERROR_OUTPUT)
            currentProcessHandler?.destroyProcess()
            currentProcessHandler = null
            return ScriptResult(-1, stdout.toString(), errorMsg, false)
        }
    }

    /**
     * 尝试停止当前正在执行的脚本
     */
    fun stopRunning() {
        val handler = currentProcessHandler
        if (handler != null) {
            LOG.info("停止当前脚本执行")
            handler.destroyProcess()
        }
    }

    /**
     * 异步执行脚本
     * @param scriptPath 脚本完整路径
     * @param workingDir 工作目录
     * @param consoleView 可选的控制台视图
     * @param onComplete 完成回调
     */
    fun runScriptAsync(
        scriptPath: String,
        workingDir: String,
        consoleView: ConsoleView? = null,
        onComplete: (ScriptResult) -> Unit
    ) {
        Thread {
            val result = runScript(scriptPath, workingDir, consoleView = consoleView)
            onComplete(result)
        }.start()
    }
}
