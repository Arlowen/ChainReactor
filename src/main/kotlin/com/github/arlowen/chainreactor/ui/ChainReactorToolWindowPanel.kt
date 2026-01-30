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
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import javax.swing.DefaultListModel
import javax.swing.DropMode
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.TransferHandler
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

/**
 * ChainReactor 工具窗面板
 * 包含模块列表、工具栏和控制台
 */
class ChainReactorToolWindowPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    companion object {
        private val LOG = thisLogger()
        private val HAMBURGER_ICON = IconLoader.getIcon("/icons/hamburger.svg", ChainReactorToolWindowPanel::class.java)
    }

    private data class ProfileItem(
        val name: String,
        val enabledCount: Int,
        val totalCount: Int
    )

    private data class ProfileRun(
        val executor: PipelineExecutor,
        val console: ConsoleView
    )

    private inner class ProfileListCellRenderer : ListCellRenderer<ProfileItem> {
        override fun getListCellRendererComponent(
            list: JList<out ProfileItem>,
            value: ProfileItem,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val isRunning = isProfileRunning(value.name)
            val panel = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(6, 8)
                background = if (isSelected) list.selectionBackground else list.background
            }

            val runLabel = JLabel(if (isRunning) AllIcons.Actions.Suspend else AllIcons.Actions.Execute).apply {
                border = JBUI.Borders.emptyRight(2)
                disabledIcon = if (isRunning) AllIcons.Actions.Suspend else AllIcons.Actions.Execute
                isEnabled = !isRunning
            }
            val stopLabel = JLabel(AllIcons.Actions.Suspend).apply {
                border = JBUI.Borders.emptyRight(2)
                disabledIcon = AllIcons.Actions.Suspend
                isEnabled = isRunning
            }
            val iconPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 2, 0)).apply {
                isOpaque = false
                preferredSize = JBUI.size(36, 16)
                add(runLabel)
                add(stopLabel)
            }

            val nameLabel = JLabel(value.name).apply {
                font = font.deriveFont(Font.BOLD, 12f)
                foreground = if (isSelected) list.selectionForeground else list.foreground
            }

            val countLabel = JLabel("${value.enabledCount}/${value.totalCount}").apply {
                foreground = if (isSelected) list.selectionForeground else JBColor.GRAY
            }

            panel.add(iconPanel, BorderLayout.WEST)
            panel.add(nameLabel, BorderLayout.CENTER)
            panel.add(countLabel, BorderLayout.EAST)
            return panel
        }
    }

    private val listModel = BuildModuleListModel()
    private val cellRenderer = BuildModuleCellRenderer()
    private val moduleList: JBList<BuildModule>
    private val profileListModel = DefaultListModel<ProfileItem>()
    private val profileList: JBList<ProfileItem>
    private val mainConsoleView: ConsoleView
    private lateinit var logTabs: JBTabbedPane
    private val pipelineTabPrefix = "流水线: "
    private val pipelineExecutor = PipelineExecutor(project)
    private val moduleScanner = ModuleScanner(project)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var runAction: AnAction? = null
    private var stopAction: AnAction? = null
    private var profileRunAction: AnAction? = null
    private var profileStopAction: AnAction? = null
    private var profileEditAction: AnAction? = null
    private var profileDeleteAction: AnAction? = null
    private var profileListToggleAction: ToggleAction? = null
    private val profileRuns = mutableMapOf<String, ProfileRun>()
    private val tabTitleLabels = mutableMapOf<JComponent, JBLabel>()

    private var profileListVisible = true
    private var profileListProportion = 0.28f
    private lateinit var listSplitter: JBSplitter
    private lateinit var profilePanel: JPanel
    private lateinit var modulePanel: JPanel

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

        // 创建流水线列表
        profileList = JBList(profileListModel).apply {
            cellRenderer = ProfileListCellRenderer()
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            emptyText.text = "暂无流水线"
            emptyText.appendSecondaryText("保存后可一键运行", SimpleTextAttributes.GRAYED_ATTRIBUTES, null)

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.button != MouseEvent.BUTTON1) return

                    val index = locationToIndex(e.point)
                    if (index < 0) return

                    val cellBounds = getCellBounds(index, index) ?: return
                    if (!cellBounds.contains(e.point)) return
                    val relativeX = e.x - cellBounds.x

                    val item = profileListModel.getElementAt(index)
                    val runArea = 24
                    val stopArea = 48

                    when {
                        relativeX <= runArea -> {
                            if (isProfileRunning(item.name)) {
                                stopProfilePipeline(item.name)
                            } else {
                                runSavedProfile(item.name)
                            }
                        }
                        relativeX <= stopArea -> stopProfilePipeline(item.name)
                        e.clickCount == 2 -> runSavedProfile(item.name)
                    }
                }

                override fun mousePressed(e: MouseEvent) {
                    if (e.isPopupTrigger) {
                        showProfileContextMenu(e)
                    }
                }

                override fun mouseReleased(e: MouseEvent) {
                    if (e.isPopupTrigger) {
                        showProfileContextMenu(e)
                    }
                }
            })

            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_DELETE) {
                        val item = selectedValue ?: return
                        deleteProfile(item.name)
                    }
                }
            })
        }

        // 启用拖拽排序
        setupDragAndDrop()

        // 创建控制台
        mainConsoleView = TextConsoleBuilderFactory.getInstance()
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
        toolbar = createToolbar()

        // 初始扫描
        refreshModules()
        refreshProfileList()
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
        profilePanel = createProfilePanel()
        modulePanel = createModulePanel()
        listSplitter = JBSplitter(true, profileListProportion).apply {
            firstComponent = profilePanel
            secondComponent = modulePanel
        }

        return JPanel(BorderLayout()).apply {
            add(listSplitter, BorderLayout.CENTER)
        }
    }

    /**
     * 创建流水线列表面板
     */
    private fun createProfilePanel(): JPanel {
        val headerLabel = JBLabel("流水线列表").apply {
            border = JBUI.Borders.empty(8, 8, 8, 0)
            foreground = JBColor.GRAY
        }

        val headerPanel = JPanel(BorderLayout()).apply {
            add(headerLabel, BorderLayout.WEST)
            add(createProfileToolbar().component, BorderLayout.EAST)
        }

        val scrollPane = JBScrollPane(profileList)
        return JPanel(BorderLayout()).apply {
            add(headerPanel, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
        }
    }

    /**
     * 创建模块面板
     */
    private fun createModulePanel(): JPanel {
        val scrollPane = JBScrollPane(moduleList)

        val headerLabel = JBLabel("当前流水线 (拖拽排序)").apply {
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
        val tabs = JBTabbedPane()
        logTabs = tabs
        addClosableTab("${pipelineTabPrefix}当前流水线", mainConsoleView.component, false, null)

        val headerLabel = JBLabel("构建日志").apply {
            border = JBUI.Borders.empty(8)
            foreground = JBColor.GRAY
        }

        return JPanel(BorderLayout()).apply {
            add(headerLabel, BorderLayout.NORTH)
            add(logTabs, BorderLayout.CENTER)
        }
    }

    /**
     * 创建工具栏
     */
    private fun createToolbar(): JComponent {
        val leftGroup = DefaultActionGroup().apply {
            // 运行按钮
            runAction = object : AnAction("运行", "执行流水线", AllIcons.Actions.Execute) {
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
                    mainConsoleView.clear()
                    profileRuns.values.forEach { it.console.clear() }
                }
            })
        }

        val rightGroup = DefaultActionGroup().apply {
            // 保存（独立按钮）
            add(object : AnAction("保存", "将当前列表顺序和命令保存为流水线", AllIcons.Actions.MenuSaveall) {
                override fun actionPerformed(e: AnActionEvent) {
                    saveCurrentProfile()
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = !pipelineExecutor.isRunning() && listModel.size() > 0
                }
            })

            // 显示/隐藏流水线列表
            profileListToggleAction = object : ToggleAction("流水线列表", "显示/隐藏流水线列表", HAMBURGER_ICON) {
                override fun isSelected(e: AnActionEvent): Boolean = profileListVisible

                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    setProfileListVisible(state)
                }
            }
            add(profileListToggleAction!!)
        }

        val leftToolbar = ActionManager.getInstance()
            .createActionToolbar("ChainReactorToolbarLeft", leftGroup, true)
            .apply {
                targetComponent = this@ChainReactorToolWindowPanel
            }

        val rightToolbar = ActionManager.getInstance()
            .createActionToolbar("ChainReactorToolbarRight", rightGroup, true)
            .apply {
                targetComponent = this@ChainReactorToolWindowPanel
            }

        return JPanel(BorderLayout()).apply {
            add(leftToolbar.component, BorderLayout.WEST)
            add(rightToolbar.component, BorderLayout.EAST)
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
                mainConsoleView.print("✅ 刷新完成，当前共有 ${modules.size} 个模块\n", ConsoleViewContentType.SYSTEM_OUTPUT)
            }
        }
    }

    /**
     * 刷新流水线列表
     */
    private fun refreshProfileList() {
        val state = ModuleOrderState.getInstance(project)
        val profiles = state.getProfileNames()

        val selectedName = profileList.selectedValue?.name
        profileListModel.clear()
        profiles.forEach { name ->
            val profile = state.getProfile(name) ?: return@forEach
            val totalCount = profile.moduleOrder.size
            val enabledCount = profile.moduleOrder.count { it !in profile.disabledModules }
            profileListModel.addElement(ProfileItem(name, enabledCount, totalCount))
        }
        if (!selectedName.isNullOrBlank()) {
            val newIndex = (0 until profileListModel.size())
                .firstOrNull { profileListModel.getElementAt(it).name == selectedName }
            if (newIndex != null) {
                profileList.selectedIndex = newIndex
            }
        }
    }

    /**
     * 创建流水线列表的操作按钮
     */
    private fun createProfileToolbar(): ActionToolbar {
        val group = DefaultActionGroup().apply {
            profileRunAction = object : AnAction("运行", "运行选中的流水线", AllIcons.Actions.Execute) {
                override fun actionPerformed(e: AnActionEvent) {
                    getSelectedProfileName()?.let { runSavedProfile(it) }
                }

                override fun update(e: AnActionEvent) {
                    val name = getSelectedProfileName()
                    e.presentation.isEnabled = name != null && !isProfileRunning(name)
                }
            }
            add(profileRunAction!!)

            profileStopAction = object : AnAction("停止", "停止正在运行的流水线", AllIcons.Actions.Suspend) {
                override fun actionPerformed(e: AnActionEvent) {
                    getSelectedProfileName()?.let { stopProfilePipeline(it) }
                }

                override fun update(e: AnActionEvent) {
                    val name = getSelectedProfileName()
                    e.presentation.isEnabled = name != null && isProfileRunning(name)
                }
            }
            add(profileStopAction!!)

            profileEditAction = object : AnAction("编辑", "编辑流水线", AllIcons.Actions.Edit) {
                override fun actionPerformed(e: AnActionEvent) {
                    getSelectedProfileName()?.let { showEditProfileDialog(it) }
                }

                override fun update(e: AnActionEvent) {
                    val name = getSelectedProfileName()
                    e.presentation.isEnabled = name != null && !isProfileRunning(name)
                }
            }
            add(profileEditAction!!)

            profileDeleteAction = object : AnAction("删除", "删除流水线", AllIcons.Actions.GC) {
                override fun actionPerformed(e: AnActionEvent) {
                    getSelectedProfileName()?.let { deleteProfile(it) }
                }

                override fun update(e: AnActionEvent) {
                    val name = getSelectedProfileName()
                    e.presentation.isEnabled = name != null && !isProfileRunning(name)
                }
            }
            add(profileDeleteAction!!)

        }

        return ActionManager.getInstance()
            .createActionToolbar("ChainReactorProfileToolbar", group, true)
            .apply {
                targetComponent = profileList
            }
    }

    private fun getSelectedProfileName(): String? = profileList.selectedValue?.name

    private fun isProfileRunning(profileName: String): Boolean {
        return profileRuns[profileName]?.executor?.isRunning() == true
    }

    private fun getOrCreateProfileConsole(profileName: String): ConsoleView {
        return profileRuns[profileName]?.console ?: TextConsoleBuilderFactory.getInstance()
            .createBuilder(project)
            .console
    }

    private fun addClosableTab(title: String, component: JComponent, closable: Boolean, onClose: (() -> Unit)?) {
        logTabs.addTab(title, component)
        val index = logTabs.indexOfComponent(component)
        if (index < 0) return

        val titleLabel = JBLabel(title)
        tabTitleLabels[component] = titleLabel

        val tabPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(titleLabel)
        }

        if (closable) {
            val closeLabel = JLabel(AllIcons.Actions.Close).apply {
                border = JBUI.Borders.emptyLeft(6)
                isEnabled = onClose != null
            }
            closeLabel.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (!closeLabel.isEnabled || onClose == null) return
                    onClose.invoke()
                }
            })
            tabPanel.add(closeLabel)
        }

        logTabs.setTabComponentAt(index, tabPanel)
    }

    /**
     * 显示或隐藏流水线列表
     */
    private fun setProfileListVisible(visible: Boolean) {
        if (profileListVisible == visible) return
        profileListVisible = visible

        if (visible) {
            listSplitter.firstComponent = profilePanel
            listSplitter.secondComponent = modulePanel
            listSplitter.proportion = profileListProportion
        } else {
            profileListProportion = listSplitter.proportion
            listSplitter.firstComponent = null
            listSplitter.secondComponent = modulePanel
            listSplitter.proportion = 0.0f
        }
        listSplitter.revalidate()
        listSplitter.repaint()
    }

    private fun ensureProfileLogTab(profileName: String, console: ConsoleView) {
        val tabTitle = "$pipelineTabPrefix$profileName"
        val existingIndex = logTabs.indexOfComponent(console.component)
        if (existingIndex >= 0) {
            logTabs.setTitleAt(existingIndex, tabTitle)
            tabTitleLabels[console.component]?.text = tabTitle
            logTabs.selectedIndex = existingIndex
        } else {
            addClosableTab(tabTitle, console.component, true) {
                if (isProfileRunning(profileName)) return@addClosableTab
                val idx = logTabs.indexOfComponent(console.component)
                if (idx >= 0) {
                    logTabs.removeTabAt(idx)
                }
                profileRuns.remove(profileName)
                tabTitleLabels.remove(console.component)
            }
            logTabs.selectedIndex = logTabs.tabCount - 1
        }
    }

    /**
     * 显示流水线列表的右键菜单
     */
    private fun showProfileContextMenu(e: MouseEvent) {
        val index = profileList.locationToIndex(e.point)
        if (index >= 0) {
            profileList.selectedIndex = index
        }

        val item = profileList.selectedValue ?: return
        val dataContext = DataManager.getInstance().getDataContext(profileList)

        val group = DefaultActionGroup().apply {
            add(object : AnAction("运行", "运行流水线", AllIcons.Actions.Execute) {
                override fun actionPerformed(actionEvent: AnActionEvent) {
                    if (isProfileRunning(item.name)) return
                    runSavedProfile(item.name)
                }

                override fun update(actionEvent: AnActionEvent) {
                    actionEvent.presentation.isEnabled = !isProfileRunning(item.name)
                }
            })
            add(object : AnAction("停止", "停止流水线", AllIcons.Actions.Suspend) {
                override fun actionPerformed(actionEvent: AnActionEvent) {
                    if (!isProfileRunning(item.name)) return
                    stopProfilePipeline(item.name)
                }

                override fun update(actionEvent: AnActionEvent) {
                    actionEvent.presentation.isEnabled = isProfileRunning(item.name)
                }
            })
            add(object : AnAction("编辑", "编辑流水线", AllIcons.Actions.Edit) {
                override fun actionPerformed(actionEvent: AnActionEvent) {
                    if (isProfileRunning(item.name)) return
                    showEditProfileDialog(item.name)
                }

                override fun update(actionEvent: AnActionEvent) {
                    actionEvent.presentation.isEnabled = !isProfileRunning(item.name)
                }
            })
            addSeparator()
            add(object : AnAction("删除", "删除流水线", AllIcons.Actions.GC) {
                override fun actionPerformed(actionEvent: AnActionEvent) {
                    if (isProfileRunning(item.name)) return
                    deleteProfile(item.name)
                }

                override fun update(actionEvent: AnActionEvent) {
                    actionEvent.presentation.isEnabled = !isProfileRunning(item.name)
                }
            })
        }

        JBPopupFactory.getInstance()
            .createActionGroupPopup(
                null,
                group,
                dataContext,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true
            )
            .showInBestPositionFor(dataContext)
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
            mainConsoleView.print("➕ 已添加项目: ${virtualFile.name}\n", ConsoleViewContentType.SYSTEM_OUTPUT)
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
            mainConsoleView.print("➖ 已移除项目: ${module.name}\n", ConsoleViewContentType.SYSTEM_OUTPUT)
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
        mainConsoleView.print("$status: ${module.name}\n", ConsoleViewContentType.SYSTEM_OUTPUT)
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
            mainConsoleView.print(msg, ConsoleViewContentType.SYSTEM_OUTPUT)
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

        startPipelineRun(
            title = "",
            allModules = allModules,
            runModules = enabledModules,
            totalModuleCount = allModules.size
        )
    }

    /**
     * 弹窗编辑 Profile
     */
    private fun showEditProfileDialog(profileName: String) {
        if (isProfileRunning(profileName)) return

        val state = ModuleOrderState.getInstance(project)
        val profile = state.getProfile(profileName)
        if (profile == null) {
            Messages.showWarningDialog(project, "流水线 '$profileName' 不存在或已损坏", "ChainReactor")
            refreshProfileList()
            return
        }

        val modulesInProject = listModel.getModules()
        val dialog = ProfileEditDialog(
            project = project,
            originalName = profileName,
            profile = cloneProfile(profile, profileName),
            modulesInProject = modulesInProject,
            nameExists = { name ->
                val existing = state.getProfile(name)
                existing != null && name != profileName
            },
            onSave = { updatedProfile ->
                state.upsertProfile(updatedProfile, profileName)
                refreshProfileList()
                mainConsoleView.print("📝 已更新流水线: ${updatedProfile.name}\n", ConsoleViewContentType.SYSTEM_OUTPUT)
            }
        )
        dialog.show()
    }

    /**
     * 删除 Profile
     */
    private fun deleteProfile(profileName: String) {
        if (isProfileRunning(profileName)) return

        if (Messages.showYesNoDialog(
                project,
                "确定要删除流水线 '$profileName' 吗?",
                "删除流水线",
                Messages.getQuestionIcon()
            ) == Messages.YES) {
            ModuleOrderState.getInstance(project).deleteProfile(profileName)
            mainConsoleView.print("🗑️ 已删除流水线: $profileName\n", ConsoleViewContentType.SYSTEM_OUTPUT)
            refreshProfileList()
        }
    }

    /**
     * 直接运行已保存的 Profile（不加载到当前列表）
     */
    private fun runSavedProfile(profileName: String) {
        val state = ModuleOrderState.getInstance(project)
        val profile = state.getProfile(profileName)

        if (profile == null) {
            Messages.showWarningDialog(project, "流水线 '$profileName' 不存在或已损坏", "ChainReactor")
            refreshProfileList()
            return
        }

        if (isProfileRunning(profileName)) {
            Messages.showInfoMessage(project, "流水线正在运行: $profileName", "ChainReactor")
            return
        }

        val allModules = listModel.getModules()
        if (allModules.isEmpty()) {
            Messages.showWarningDialog(project, "当前没有可运行的模块", "ChainReactor")
            return
        }

        val moduleMap = allModules.associateBy { it.id }
        val orderedModules = mutableListOf<BuildModule>()
        var missingCount = 0

        profile.moduleOrder.forEach { moduleId ->
            val module = moduleMap[moduleId]
            if (module == null) {
                missingCount++
                return@forEach
            }

            val enabled = moduleId !in profile.disabledModules
            val command = profile.moduleCommands[moduleId]
            orderedModules.add(
                module.copy(
                    enabled = enabled,
                    customCommand = command
                )
            )
        }

        if (orderedModules.isEmpty()) {
            Messages.showWarningDialog(project, "流水线中没有可运行的模块", "ChainReactor")
            return
        }

        val enabledModules = orderedModules.filter { it.enabled }
        if (enabledModules.isEmpty()) {
            Messages.showWarningDialog(project, "流水线中所有模块均被禁用", "ChainReactor")
            return
        }

        val console = getOrCreateProfileConsole(profileName)
        val executor = PipelineExecutor(project)
        profileRuns[profileName] = ProfileRun(executor, console)

        startProfilePipelineRun(
            profileName = profileName,
            runModules = enabledModules,
            totalModuleCount = orderedModules.size,
            missingCount = missingCount,
            executor = executor,
            console = console
        )
    }

    private fun stopProfilePipeline(profileName: String) {
        val run = profileRuns[profileName] ?: return
        if (!run.executor.isRunning()) return
        run.executor.stop()
        run.console.print("⏹️ 已请求停止流水线: $profileName\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        profileList.repaint()
    }

    /**
     * 后台运行已保存的流水线，不影响当前构建列表状态
     */
    private fun startProfilePipelineRun(
        profileName: String,
        runModules: List<BuildModule>,
        totalModuleCount: Int,
        missingCount: Int,
        executor: PipelineExecutor,
        console: ConsoleView
    ) {
        console.clear()
        ensureProfileLogTab(profileName, console)
        profileList.repaint()

        console.print(
            "🧩 [流水线:$profileName] 开始运行 (共 ${runModules.size}/$totalModuleCount 个模块)...\n",
            ConsoleViewContentType.SYSTEM_OUTPUT
        )
        if (missingCount > 0) {
            console.print("⚠️ [流水线:$profileName] 有 $missingCount 个模块未找到，已跳过\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        }
        console.print("─".repeat(50) + "\n", ConsoleViewContentType.SYSTEM_OUTPUT)

        coroutineScope.launch {
            executor.execute(runModules, console, object : PipelineExecutor.StatusListener {
                override fun onStatusChanged(moduleId: String, status: ModuleStatus) {
                    // 流水线后台运行不影响主列表状态
                }

                override fun onPipelineStarted() {
                    ApplicationManager.getApplication().invokeLater {
                        profileList.repaint()
                    }
                }

                override fun onPipelineFinished(success: Boolean, failedModule: BuildModule?) {
                    ApplicationManager.getApplication().invokeLater {
                        console.print("─".repeat(50) + "\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                        if (success) {
                            console.print("✅ [流水线:$profileName] 执行完成\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                        } else {
                            val msg = if (failedModule != null) {
                                "❌ [流水线:$profileName] 失败模块：${failedModule.name}\n"
                            } else {
                                "❌ [流水线:$profileName] 构建被中断\n"
                            }
                            console.print(msg, ConsoleViewContentType.ERROR_OUTPUT)
                        }
                        profileList.repaint()
                    }
                }
            })
        }
    }

    private fun cloneProfile(
        profile: ModuleOrderState.PipelineProfile,
        newName: String = profile.name
    ): ModuleOrderState.PipelineProfile {
        return ModuleOrderState.PipelineProfile(
            name = newName,
            moduleOrder = profile.moduleOrder.toMutableList(),
            disabledModules = profile.disabledModules.toMutableSet(),
            moduleCommands = profile.moduleCommands.toMutableMap()
        )
    }

    /**
     * 启动流水线执行，并负责 UI 状态更新
     */
    private fun startPipelineRun(
        title: String,
        allModules: List<BuildModule>,
        runModules: List<BuildModule>,
        totalModuleCount: Int,
        missingCount: Int = 0
    ) {
        if (pipelineExecutor.isRunning()) return

        // 统一设置状态：参与执行的为 PENDING，其余为 SKIPPED
        val runIds = runModules.map { it.id }.toSet()
        cellRenderer.resetAllStatus()
        allModules.forEach { module ->
            val status = if (module.id in runIds) ModuleStatus.PENDING else ModuleStatus.SKIPPED
            cellRenderer.updateStatus(module.id, status)
        }
        moduleList.repaint()

        // 清空控制台
        mainConsoleView.clear()
        val titleSuffix = if (title.isBlank()) "" else " - $title"
        mainConsoleView.print(
            "🚀 开始运行流水线$titleSuffix (共 ${runModules.size}/$totalModuleCount 个模块)...\n",
            ConsoleViewContentType.SYSTEM_OUTPUT
        )
        if (missingCount > 0) {
            mainConsoleView.print("⚠️ 流水线中有 $missingCount 个模块未找到，已跳过\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        }
        mainConsoleView.print("═".repeat(50) + "\n\n", ConsoleViewContentType.SYSTEM_OUTPUT)

        coroutineScope.launch {
            pipelineExecutor.execute(runModules, mainConsoleView, object : PipelineExecutor.StatusListener {
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

                        mainConsoleView.print("\n" + "═".repeat(50) + "\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                        if (success) {
                            mainConsoleView.print("✅ 所有模块构建成功！\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                        } else {
                            val msg = if (failedModule != null) {
                                "❌ 构建失败：${failedModule.name}\n"
                            } else {
                                "❌ 构建被中断\n"
                            }
                            mainConsoleView.print(msg, ConsoleViewContentType.ERROR_OUTPUT)
                        }
                    }
                }
            })
        }
    }

    /**
     * 保存当前流水线
     */
    private fun saveCurrentProfile() {
        val name = Messages.showInputDialog(
            project,
            "请输入 Profile 名称:",
            "保存流水线",
            Messages.getQuestionIcon()
        )
        
        if (!name.isNullOrBlank()) {
            ModuleOrderState.getInstance(project).saveProfile(name)
            mainConsoleView.print("💾 已保存流水线: $name\n", ConsoleViewContentType.SYSTEM_OUTPUT)
            refreshProfileList()
        }
    }

}
