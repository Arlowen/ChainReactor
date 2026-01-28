package com.github.arlowen.chainreactor.ui

import com.github.arlowen.chainreactor.model.BuildModule
import com.github.arlowen.chainreactor.model.ModuleStatus
import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * 构建模块列表项渲染器
 * 显示模块名称、路径和执行状态图标
 */
class BuildModuleCellRenderer : ListCellRenderer<BuildModule> {

    // 模块状态映射
    private val statusMap = mutableMapOf<String, ModuleStatus>()

    override fun getListCellRendererComponent(
        list: JList<out BuildModule>,
        value: BuildModule,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8, 12)

            if (isSelected) {
                background = list.selectionBackground
            } else {
                background = list.background
            }
        }

        // 左侧：序号和状态图标
        val orderLabel = JLabel().apply {
            val status = statusMap[value.id] ?: ModuleStatus.PENDING
            icon = getStatusIcon(status)
            text = "${index + 1}."
            font = font.deriveFont(Font.BOLD)
            foreground = if (isSelected) list.selectionForeground else JBColor.GRAY
            border = JBUI.Borders.emptyRight(8)
        }

        // 中间：模块信息
        val infoPanel = JPanel(BorderLayout()).apply {
            isOpaque = false

            // 模块名称
            val nameLabel = JLabel(value.name).apply {
                font = font.deriveFont(Font.BOLD, 13f)
                foreground = if (isSelected) list.selectionForeground else list.foreground
            }

            // 脚本路径
            val pathLabel = JLabel(value.scriptPath).apply {
                font = font.deriveFont(11f)
                foreground = if (isSelected) list.selectionForeground.darker() else JBColor.GRAY
            }

            add(nameLabel, BorderLayout.NORTH)
            add(pathLabel, BorderLayout.SOUTH)
        }

        // 右侧：拖拽手柄图标
        val handleLabel = JLabel(AllIcons.General.Ellipsis)
        handleLabel.border = JBUI.Borders.emptyLeft(8)

        panel.add(orderLabel, BorderLayout.WEST)
        panel.add(infoPanel, BorderLayout.CENTER)
        panel.add(handleLabel, BorderLayout.EAST)

        return panel
    }

    /**
     * 获取状态图标
     */
    private fun getStatusIcon(status: ModuleStatus): Icon {
        return when (status) {
            ModuleStatus.PENDING -> AllIcons.RunConfigurations.TestNotRan
            ModuleStatus.RUNNING -> AllIcons.Process.Step_1
            ModuleStatus.SUCCESS -> AllIcons.RunConfigurations.TestPassed
            ModuleStatus.FAILED -> AllIcons.RunConfigurations.TestFailed
            ModuleStatus.SKIPPED -> AllIcons.RunConfigurations.TestSkipped
        }
    }

    /**
     * 更新模块状态
     */
    fun updateStatus(moduleId: String, status: ModuleStatus) {
        statusMap[moduleId] = status
    }

    /**
     * 重置所有状态为 PENDING
     */
    fun resetAllStatus() {
        statusMap.clear()
    }

    /**
     * 获取模块状态
     */
    fun getStatus(moduleId: String): ModuleStatus = statusMap[moduleId] ?: ModuleStatus.PENDING
}
