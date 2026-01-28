package com.github.arlowen.chainreactor.actions

import com.github.arlowen.chainreactor.core.ScriptRunner
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import java.util.function.Supplier

/**
 * 测试运行脚本的 Action
 * 用于验证脚本执行和控制台输出功能
 */
class RunScriptAction : AnAction() {

    companion object {
        private val LOG = thisLogger()
        private const val CONSOLE_TOOL_WINDOW_ID = "ChainReactor Console"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 弹出文件选择器让用户选择脚本
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
            .withTitle("选择要执行的脚本")
            .withDescription("选择一个 Shell 脚本文件")

        FileChooser.chooseFile(descriptor, project, null) { virtualFile ->
            val scriptPath = virtualFile.path
            val workingDir = virtualFile.parent?.path ?: return@chooseFile

            LOG.info("用户选择了脚本: $scriptPath")

            // 获取或创建控制台
            val consoleView = getOrCreateConsoleView(project)

            // 清空控制台
            consoleView.clear()

            // 异步执行脚本
            val scriptRunner = ScriptRunner()
            scriptRunner.runScriptAsync(scriptPath, workingDir, consoleView) { result ->
                LOG.info("脚本执行完成: exitCode=${result.exitCode}, success=${result.success}")
            }
        }
    }

    /**
     * 获取或创建控制台视图
     */
    private fun getOrCreateConsoleView(project: Project): ConsoleView {
        val toolWindowManager = ToolWindowManager.getInstance(project)

        // 尝试获取已存在的工具窗口
        var toolWindow = toolWindowManager.getToolWindow(CONSOLE_TOOL_WINDOW_ID)

        if (toolWindow == null) {
            // 注册新的工具窗口
            toolWindow = toolWindowManager.registerToolWindow(CONSOLE_TOOL_WINDOW_ID) {
                stripeTitle = Supplier { "ChainReactor" }
                canCloseContent = true
            }
        }

        // 检查是否已有控制台内容
        val existingContent = toolWindow.contentManager.findContent("Build Output")
        if (existingContent != null) {
            val consoleView = existingContent.component as? ConsoleView
            if (consoleView != null) {
                toolWindow.show()
                return consoleView
            }
        }

        // 创建新的控制台
        val consoleView = TextConsoleBuilderFactory.getInstance()
            .createBuilder(project)
            .console

        val content = ContentFactory.getInstance().createContent(
            consoleView.component,
            "Build Output",
            false
        )
        toolWindow.contentManager.addContent(content)
        toolWindow.show()

        return consoleView
    }

    override fun update(e: AnActionEvent) {
        // 只有在项目打开时才启用
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
