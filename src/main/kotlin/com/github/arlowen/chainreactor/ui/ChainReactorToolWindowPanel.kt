package com.github.arlowen.chainreactor.ui

import com.github.arlowen.chainreactor.core.ModuleScanner
import com.github.arlowen.chainreactor.core.PipelineExecutor
import com.github.arlowen.chainreactor.model.BuildModule
import com.github.arlowen.chainreactor.model.ModuleStatus
import com.github.arlowen.chainreactor.state.ModuleOrderState
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.awt.BorderLayout
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
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

/**
 * ChainReactor 工具窗面板
 * 包含模块列表、工具栏和控制台
 */
class ChainReactorToolWindowPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    companion object {
        private val LOG = thisLogger()
    }

    private val listModel = BuildModuleListModel()
    private val cellRenderer = BuildModuleCellRenderer()
    private val moduleList: JBList<BuildModule>
    private val consoleView: ConsoleView
    private val pipelineExecutor = PipelineExecutor(project)
    private val moduleScanner = ModuleScanner(project)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var runAction: AnAction? = null
    private var stopAction: AnAction? = null

    init {
        // 创建模块列表
        moduleList = JBList(listModel).apply {
            cellRenderer = this@ChainReactorToolWindowPanel.cellRenderer
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            emptyText.text = "未找到构建模块"
            emptyText.appendSecondaryText("点击刷新按钮扫描项目", SimpleTextAttributes.GRAYED_ATTRIBUTES, null)

            // 鼠标点击处理
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (pipelineExecutor.isRunning()) return
                    
                    val index = locationToIndex(e.point)
                    if (index < 0) return
                    
                    val cellBounds = getCellBounds(index, index) ?: return
                    val relativeX = e.x - cellBounds.x
                    
                    // 第一个 50px 区域是复选框区域
                    if (relativeX < 50 && e.clickCount == 1) {
                        toggleModuleEnabled(index)
                    } else if (e.clickCount == 2) {
                        editSelectedModuleCommand()
                    }
                }
            })
        }

        // 启用拖拽排序
        setupDragAndDrop()

        // 创建控制台
        consoleView = TextConsoleBuilderFactory.getInstance()
            .createBuilder(project)
            .console

        // 监听列表变化，保存顺序
        listModel.addListDataListener(object : ListDataListener {
            override fun intervalAdded(e: ListDataEvent) = saveOrder()
            override fun intervalRemoved(e: ListDataEvent) = saveOrder()
            override fun contentsChanged(e: ListDataEvent) = saveOrder()
        })

        // 设置内容
        setContent(createMainContent())

        // 设置工具栏
        toolbar = createToolbar().component

        // 初始扫描
        refreshModules()
    }

    /**
     * 创建主要内容区域
     */
    private fun createMainContent(): JPanel {
        val splitter = JBSplitter(true, 0.5f).apply {
            firstComponent = createListPanel()
            secondComponent = createConsolePanel()
        }

        return JPanel(BorderLayout()).apply {
            add(splitter, BorderLayout.CENTER)
        }
    }

    /**
     * 创建列表面板
     */
    private fun createListPanel(): JPanel {
        val scrollPane = JBScrollPane(moduleList)

        val headerLabel = JBLabel("构建模块 (拖拽排序)").apply {
            border = JBUI.Borders.empty(8)
            foreground = JBColor.GRAY
        }

        return JPanel(BorderLayout()).apply {
            add(headerLabel, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
        }
    }

    /**
     * 创建控制台面板
     */
    private fun createConsolePanel(): JPanel {
        val headerLabel = JBLabel("构建日志").apply {
            border = JBUI.Borders.empty(8)
            foreground = JBColor.GRAY
        }

        return JPanel(BorderLayout()).apply {
            add(headerLabel, BorderLayout.NORTH)
            add(consoleView.component, BorderLayout.CENTER)
        }
    }

    /**
     * 创建工具栏
     */
    private fun createToolbar(): ActionToolbar {
        val actionGroup = DefaultActionGroup().apply {
            // 运行按钮
            runAction = object : AnAction("运行", "执行构建流水线", AllIcons.Actions.Execute) {
                override fun actionPerformed(e: AnActionEvent) {
                    runPipeline()
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = !pipelineExecutor.isRunning() && listModel.size() > 0
                }
            }
            add(runAction!!)

            // 停止按钮
            stopAction = object : AnAction("停止", "停止构建", AllIcons.Actions.Suspend) {
                override fun actionPerformed(e: AnActionEvent) {
                    pipelineExecutor.stop()
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = pipelineExecutor.isRunning()
                }
            }
            add(stopAction!!)

            addSeparator()

            // 刷新按钮
            add(object : AnAction("刷新", "重新扫描项目", AllIcons.Actions.Refresh) {
                override fun actionPerformed(e: AnActionEvent) {
                    refreshModules()
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = !pipelineExecutor.isRunning()
                }
            })

            // 清空日志按钮
            add(object : AnAction("清空日志", "清空控制台日志", AllIcons.Actions.GC) {
                override fun actionPerformed(e: AnActionEvent) {
                    consoleView.clear()
                }
            })

            addSeparator()
            
            // 添加项目按钮
            add(object : AnAction("添加项目", "添加现有项目目录", AllIcons.General.Add) {
                override fun actionPerformed(e: AnActionEvent) {
                    addProject()
                }
            })
            
            // 移除项目按钮
            add(object : AnAction("移除项目", "从列表中移除选中项目", AllIcons.General.Remove) {
                override fun actionPerformed(e: AnActionEvent) {
                    removeSelectedProject()
                }
                
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = !pipelineExecutor.isRunning() && !moduleList.isSelectionEmpty
                }
            })
        }

        return ActionManager.getInstance()
            .createActionToolbar("ChainReactorToolbar", actionGroup, true)
            .apply {
                targetComponent = this@ChainReactorToolWindowPanel
            }
    }

    /**
     * 设置拖拽排序
     */
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

            override fun canImport(support: TransferSupport): Boolean {
                return support.isDrop && !pipelineExecutor.isRunning()
            }

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

    /**
     * 刷新模块列表
     */
    private fun refreshModules() {
        LOG.info("刷新模块列表")

        ApplicationManager.getApplication().executeOnPooledThread {
            // ModuleScanner.scan() 现在会自动合并手动项目并过滤移除项目
            val modules = moduleScanner.scan()

            ApplicationManager.getApplication().invokeLater {
                val orderState = ModuleOrderState.getInstance(project)
                
                // 加载每个模块的自定义命令和启用状态
                modules.forEach { module ->
                    module.customCommand = orderState.getCommand(module.id)
                    module.enabled = orderState.isEnabled(module.id)
                }
                
                listModel.setModules(modules)

                // 应用保存的顺序
                val savedOrder = orderState.getOrder()
                if (savedOrder.isNotEmpty()) {
                    listModel.applyOrder(savedOrder)
                }

                // 重置状态
                cellRenderer.resetAllStatus()
                moduleList.repaint()

                val enabledCount = modules.count { it.enabled }
                consoleView.print("✅ 刷新完成，当前共有 ${modules.size} 个模块\n", ConsoleViewContentType.SYSTEM_OUTPUT)
            }
        }
    }

    /**
     * 添加项目
     */
    private fun addProject() {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle("选择项目目录")
            .withDescription("选择包含 pom.xml 或 build.gradle 的目录")
            
        val virtualFile = FileChooser.chooseFile(descriptor, project, null)
        if (virtualFile != null) {
            val path = virtualFile.path
            ModuleOrderState.getInstance(project).addManualProject(path)
            refreshModules()
            consoleView.print("➕ 已添加项目: ${virtualFile.name}\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        }
    }

    /**
     * 移除选中的项目
     */
    private fun removeSelectedProject() {
        val module = moduleList.selectedValue ?: return
        val result = Messages.showYesNoDialog(
            project,
            "确定要从列表中移除 '${module.name}' 吗？\n(这不会删除物理文件)",
            "移除项目",
            Messages.getQuestionIcon()
        )
        
        if (result == Messages.YES) {
            ModuleOrderState.getInstance(project).removeProject(module.id)
            refreshModules()
            consoleView.print("➖ 已移除项目: ${module.name}\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        }
    }

    /**
     * 切换模块启用状态
     */
    private fun toggleModuleEnabled(index: Int) {
        val module = listModel.getElementAt(index) ?: return
        module.enabled = !module.enabled
        ModuleOrderState.getInstance(project).setEnabled(module.id, module.enabled)
        moduleList.repaint()
        
        val status = if (module.enabled) "✅ 已启用" else "⚪ 已禁用"
        consoleView.print("$status: ${module.name}\n", ConsoleViewContentType.SYSTEM_OUTPUT)
    }

    /**
     * 编辑选中模块的命令
     */
    private fun editSelectedModuleCommand() {
        val module = moduleList.selectedValue ?: return
        val currentCommand = module.customCommand ?: ""
        
        val newCommand = Messages.showInputDialog(
            project,
            "输入自定义命令 (留空使用默认脚本):\n\n默认: ${module.scriptPath}",
            "编辑执行命令 - ${module.name}",
            null,
            currentCommand,
            null
        )
        
        // 用户点击取消时 newCommand 为 null
        if (newCommand != null) {
            module.customCommand = newCommand.takeIf { it.isNotBlank() }
            ModuleOrderState.getInstance(project).setCommand(module.id, module.customCommand)
            moduleList.repaint()
            
            val msg = if (module.customCommand != null) {
                "✏️ 已设置 ${module.name} 的自定义命令: ${module.customCommand}\n"
            } else {
                "🔄 已重置 ${module.name} 为默认命令\n"
            }
            consoleView.print(msg, ConsoleViewContentType.SYSTEM_OUTPUT)
        }
    }

    /**
     * 保存模块顺序
     */
    private fun saveOrder() {
        val order = listModel.getModuleIds()
        ModuleOrderState.getInstance(project).setOrder(order)
    }

    /**
     * 运行构建流水线
     */
    private fun runPipeline() {
        val allModules = listModel.getModules()
        val enabledModules = allModules.filter { it.enabled }
        
        if (enabledModules.isEmpty()) {
            Messages.showWarningDialog(project, "没有勾选任何构建模块", "ChainReactor")
            return
        }

        // 重置状态
        cellRenderer.resetAllStatus()
        enabledModules.forEach { cellRenderer.updateStatus(it.id, ModuleStatus.PENDING) }
        // 禁用的模块标记为 SKIPPED
        allModules.filter { !it.enabled }.forEach { cellRenderer.updateStatus(it.id, ModuleStatus.SKIPPED) }
        moduleList.repaint()

        // 清空控制台
        consoleView.clear()
        consoleView.print("🚀 开始构建流水线 (共 ${enabledModules.size}/${allModules.size} 个模块)...\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        consoleView.print("═".repeat(50) + "\n\n", ConsoleViewContentType.SYSTEM_OUTPUT)

        coroutineScope.launch {
            pipelineExecutor.execute(enabledModules, consoleView, object : PipelineExecutor.StatusListener {
                override fun onStatusChanged(moduleId: String, status: ModuleStatus) {
                    ApplicationManager.getApplication().invokeLater {
                        cellRenderer.updateStatus(moduleId, status)
                        moduleList.repaint()
                    }
                }

                override fun onPipelineStarted() {
                    ApplicationManager.getApplication().invokeLater {
                        // 更新工具栏按钮状态
                        runAction?.templatePresentation?.isEnabled = false
                        stopAction?.templatePresentation?.isEnabled = true
                    }
                }

                override fun onPipelineFinished(success: Boolean, failedModule: BuildModule?) {
                    ApplicationManager.getApplication().invokeLater {
                        // 更新工具栏按钮状态
                        runAction?.templatePresentation?.isEnabled = true
                        stopAction?.templatePresentation?.isEnabled = false

                        consoleView.print("\n" + "═".repeat(50) + "\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                        if (success) {
                            consoleView.print("✅ 所有模块构建成功！\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                        } else {
                            val msg = if (failedModule != null) {
                                "❌ 构建失败：${failedModule.name}\n"
                            } else {
                                "❌ 构建被中断\n"
                            }
                            consoleView.print(msg, ConsoleViewContentType.ERROR_OUTPUT)
                        }
                    }
                }
            })
        }
    }
}
