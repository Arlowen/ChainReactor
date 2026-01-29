package com.github.arlowen.chainreactor.ui

import com.github.arlowen.chainreactor.model.BuildModule
import com.github.arlowen.chainreactor.model.ModuleStatus
import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.Icon
import javax.swing.JCheckBox
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
        val isDisabled = !value.enabled

        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8, 12)

            if (isSelected) {
                background = list.selectionBackground
            } else {
                background = list.background
            }
        }

        // 左侧：复选框 + 序号 + 状态图标
        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
            
            // 复选框
            val checkBox = JCheckBox().apply {
                this.isSelected = value.enabled
                isOpaque = false
                isFocusable = false
            }
            add(checkBox)
            
            // 状态图标和序号
            val orderLabel = JLabel().apply {
                val status = statusMap[value.id] ?: ModuleStatus.PENDING
                icon = getStatusIcon(status)
                text = "${index + 1}."
                font = font.deriveFont(Font.BOLD)
                foreground = when {
                    isDisabled -> JBColor.GRAY
                    isSelected -> list.selectionForeground
                    else -> JBColor.GRAY
                }
            }
            add(orderLabel)
        }

        // 中间：模块信息
        val infoPanel = JPanel(BorderLayout()).apply {
            isOpaque = false

            // 模块名称 + 自定义标记
            val nameLabel = JLabel().apply {
                val baseName = value.name
                text = if (value.customCommand != null) "$baseName [自定义]" else baseName
                font = font.deriveFont(Font.BOLD, 13f)
                foreground = when {
                    isDisabled -> JBColor.GRAY
                    isSelected -> list.selectionForeground 
                    else -> list.foreground
                }
            }

            // 显示实际命令（自定义命令或脚本路径）
            val commandText = value.customCommand ?: value.scriptPath
            val pathLabel = JLabel(commandText).apply {
                font = font.deriveFont(11f)
                foreground = when {
                    isDisabled -> JBColor.GRAY
                    value.customCommand != null -> if (isSelected) JBColor.CYAN.darker() else JBColor.BLUE
                    else -> if (isSelected) list.selectionForeground.darker() else JBColor.GRAY
                }
            }

            add(nameLabel, BorderLayout.NORTH)
            add(pathLabel, BorderLayout.SOUTH)
        }

        // 右侧：拖拽手柄图标
        val handleLabel = JLabel(AllIcons.General.Ellipsis)
        handleLabel.border = JBUI.Borders.emptyLeft(8)

        panel.add(leftPanel, BorderLayout.WEST)
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
