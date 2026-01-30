package com.github.arlowen.chainreactor.ui

import com.github.arlowen.chainreactor.model.BuildModule
import com.github.arlowen.chainreactor.state.ModuleOrderState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import javax.swing.DropMode
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.TransferHandler

/**
 * 已保存流水线编辑弹窗
 * 支持重命名、拖拽排序、启用/禁用、编辑自定义命令
 */
class ProfileEditDialog(
    private val project: Project,
    private val originalName: String,
    profile: ModuleOrderState.PipelineProfile,
    modulesInProject: List<BuildModule>,
    private val nameExists: (String) -> Boolean,
    private val onSave: (ModuleOrderState.PipelineProfile) -> Unit
) : DialogWrapper(project) {

    private val nameField = JBTextField(profile.name)
    private val listModel = BuildModuleListModel()
    private val moduleList = JList<BuildModule>(listModel)
    private val cellRenderer = BuildModuleCellRenderer()

    init {
        title = "编辑"
        initModules(profile, modulesInProject)
        init()
    }

    override fun createCenterPanel(): JComponent {
        val nameLabel = JBLabel("流水线名称:")
        val namePanel = JPanel(BorderLayout(8, 0)).apply {
            border = JBUI.Borders.empty(8, 8, 4, 8)
            add(nameLabel, BorderLayout.WEST)
            add(nameField, BorderLayout.CENTER)
        }

        val tipsLabel = JBLabel("提示：拖拽排序，点击勾选启用/禁用，双击编辑命令").apply {
            border = JBUI.Borders.empty(0, 8, 6, 8)
            foreground = JBColor.GRAY
        }

        val headerPanel = JPanel(BorderLayout()).apply {
            add(namePanel, BorderLayout.NORTH)
            add(tipsLabel, BorderLayout.SOUTH)
        }

        moduleList.cellRenderer = cellRenderer
        moduleList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        moduleList.visibleRowCount = 10

        moduleList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val index = moduleList.locationToIndex(e.point)
                if (index < 0) return

                val cellBounds = moduleList.getCellBounds(index, index) ?: return
                if (!cellBounds.contains(e.point)) return

                val relativeX = e.x - cellBounds.x
                if (relativeX < 50 && e.clickCount == 1) {
                    toggleModuleEnabled(index)
                } else if (e.clickCount == 2) {
                    editSelectedModuleCommand()
                }
            }
        })

        setupDragAndDrop()

        val root = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(560, 420)
            add(headerPanel, BorderLayout.NORTH)
            add(JBScrollPane(moduleList), BorderLayout.CENTER)
            border = JBUI.Borders.empty(0, 8, 8, 8)
        }

        return root
    }

    override fun doOKAction() {
        val newName = nameField.text.trim()
        if (newName.isBlank()) {
            Messages.showWarningDialog(project, "流水线名称不能为空", "编辑")
            return
        }
        if (newName != originalName && nameExists(newName)) {
            Messages.showWarningDialog(project, "已存在同名流水线: $newName", "编辑")
            return
        }

        val modules = listModel.getModules()
        val moduleOrder = modules.map { it.id }.toMutableList()
        val disabledModules = modules.filter { !it.enabled }.map { it.id }.toMutableSet()
        val moduleCommands = modules
            .mapNotNull { module ->
                val cmd = module.customCommand?.trim()
                if (cmd.isNullOrBlank()) null else module.id to cmd
            }
            .toMap()
            .toMutableMap()

        val updatedProfile = ModuleOrderState.PipelineProfile(
            name = newName,
            moduleOrder = moduleOrder,
            disabledModules = disabledModules,
            moduleCommands = moduleCommands
        )

        onSave(updatedProfile)
        super.doOKAction()
    }

    private fun initModules(profile: ModuleOrderState.PipelineProfile, modulesInProject: List<BuildModule>) {
        val moduleMap = modulesInProject.associateBy { it.id }
        val modules = mutableListOf<BuildModule>()

        profile.moduleOrder.forEach { moduleId ->
            val base = moduleMap[moduleId]
            val enabled = moduleId !in profile.disabledModules
            val customCommand = profile.moduleCommands[moduleId]

            if (base != null) {
                modules.add(
                    base.copy(
                        enabled = enabled,
                        customCommand = customCommand
                    )
                )
            } else {
                modules.add(
                    BuildModule(
                        id = moduleId,
                        name = "缺失模块($moduleId)",
                        scriptPath = "模块未找到",
                        workingDir = "",
                        enabled = enabled,
                        customCommand = customCommand
                    )
                )
            }
        }

        listModel.setModules(modules)
        cellRenderer.resetAllStatus()
    }

    private fun toggleModuleEnabled(index: Int) {
        val module = listModel.getElementAt(index) ?: return
        module.enabled = !module.enabled
        moduleList.repaint()
    }

    private fun editSelectedModuleCommand() {
        val module = moduleList.selectedValue ?: return
        val currentCommand = module.customCommand ?: ""
        val defaultInfo = if (module.scriptPath.isNotBlank()) module.scriptPath else module.workingDir

        val newCommand = Messages.showInputDialog(
            project,
            "输入自定义命令 (留空使用默认脚本):\n\n默认: $defaultInfo",
            "编辑执行命令 - ${module.name}",
            null,
            currentCommand,
            null
        )

        if (newCommand != null) {
            module.customCommand = newCommand.takeIf { it.isNotBlank() }
            moduleList.repaint()
        }
    }

    private fun setupDragAndDrop() {
        moduleList.dragEnabled = true
        moduleList.dropMode = DropMode.INSERT
        moduleList.transferHandler = object : TransferHandler() {
            private var dragIndex = -1

            override fun getSourceActions(c: JComponent): Int = MOVE

            override fun createTransferable(c: JComponent): Transferable? {
                dragIndex = moduleList.selectedIndex
                if (dragIndex < 0) return null
                return object : Transferable {
                    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.stringFlavor)
                    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.stringFlavor
                    override fun getTransferData(flavor: DataFlavor): Any = dragIndex.toString()
                }
            }

            override fun canImport(support: TransferSupport): Boolean = support.isDrop

            override fun importData(support: TransferSupport): Boolean {
                if (!canImport(support)) return false
                val dropLocation = support.dropLocation as? JList.DropLocation ?: return false
                val dropIndex = dropLocation.index
                if (dragIndex < 0 || dropIndex < 0) return false
                if (dragIndex != dropIndex) {
                    listModel.moveModule(dragIndex, if (dropIndex > dragIndex) dropIndex - 1 else dropIndex)
                    moduleList.repaint()
                }
                return true
            }

            override fun exportDone(source: JComponent?, data: Transferable?, action: Int) {
                dragIndex = -1
            }
        }
    }
}
