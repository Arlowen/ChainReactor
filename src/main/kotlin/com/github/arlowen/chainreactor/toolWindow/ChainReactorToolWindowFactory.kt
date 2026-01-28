package com.github.arlowen.chainreactor.toolWindow

import com.github.arlowen.chainreactor.ui.ChainReactorToolWindowPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * ChainReactor 工具窗工厂
 * 创建和管理侧边栏工具窗
 */
class ChainReactorToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ChainReactorToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(
            panel,
            null,
            false
        )
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
