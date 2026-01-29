package com.github.arlowen.chainreactor.actions

import com.github.arlowen.chainreactor.state.ModuleOrderState
import com.github.arlowen.chainreactor.ui.ChainReactorToolWindowPanel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.ToolWindowManager

/**
 * 右键菜单动作：添加到 ChainReactor
 */
class AddToChainReactorAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val project = e.project ?: return

        if (virtualFile.isDirectory) {
            ModuleOrderState.getInstance(project).addManualProject(virtualFile.path)
            
            // 尝试刷新工具窗口
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("ChainReactor")
            if (toolWindow != null && toolWindow.isVisible) {
                // 这里我们只能通过重新激活来触发还是直接操作？
                // 理想情况是通过消息总线通知，或者直接从 content 获取 panel
                // 但为了简单，我们假设用户手动刷新或者我们下次打开时刷新
                // 如果我们想强制刷新，可以尝试获取 Panel
                val content = toolWindow.contentManager.getContent(0)
                val panel = content?.component as? ChainReactorToolWindowPanel
                // 注意：由于 panel 方法是私有的，无法直接调用 refreshModules。
                // 已经在 ModuleOrderState 添加了，用户可以通过点击工具栏刷新按钮来看到。
                
                // 或者我们可以发送一个通知
                // 暂时只保存状态
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = virtualFile != null && virtualFile.isDirectory
    }
}
