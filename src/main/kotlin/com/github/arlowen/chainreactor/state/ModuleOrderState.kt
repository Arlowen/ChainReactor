package com.github.arlowen.chainreactor.state

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

/**
 * 模块顺序持久化状态
 * 保存用户拖拽排序后的模块顺序
 */
@Service(Service.Level.PROJECT)
@State(
    name = "ChainReactorModuleOrder",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class ModuleOrderState : PersistentStateComponent<ModuleOrderState.State> {

/**
     * 状态数据类
     */
    data class State(
        /** 按顺序存储的模块 ID 列表 */
        var moduleOrder: MutableList<String> = mutableListOf(),
        /** 模块 ID -> 自定义命令的映射 */
        var moduleCommands: MutableMap<String, String> = mutableMapOf(),
        /** 禁用的模块 ID 集合 */
        var disabledModules: MutableSet<String> = mutableSetOf(),
        /** 手动添加的项目路径集合 */
        var manualProjects: MutableSet<String> = mutableSetOf(),
        /** 已移除的项目 ID 集合 */
        var removedProjects: MutableSet<String> = mutableSetOf()
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    /**
     * 获取模块顺序
     */
    fun getOrder(): List<String> = myState.moduleOrder.toList()

    /**
     * 设置模块顺序
     */
    fun setOrder(order: List<String>) {
        myState.moduleOrder = order.toMutableList()
    }

    /**
     * 获取模块的顺序索引
     * @return 如果模块不在列表中，返回 Int.MAX_VALUE
     */
    fun getOrderIndex(moduleId: String): Int {
        val index = myState.moduleOrder.indexOf(moduleId)
        return if (index >= 0) index else Int.MAX_VALUE
    }

    /**
     * 获取模块的自定义命令
     */
    fun getCommand(moduleId: String): String? = myState.moduleCommands[moduleId]

    /**
     * 设置模块的自定义命令
     */
    fun setCommand(moduleId: String, command: String?) {
        if (command.isNullOrBlank()) {
            myState.moduleCommands.remove(moduleId)
        } else {
            myState.moduleCommands[moduleId] = command
        }
    }

    /**
     * 检查模块是否启用
     */
    fun isEnabled(moduleId: String): Boolean = moduleId !in myState.disabledModules

    /**
     * 设置模块启用状态
     */
    fun setEnabled(moduleId: String, enabled: Boolean) {
        if (enabled) {
            myState.disabledModules.remove(moduleId)
        } else {
            myState.disabledModules.add(moduleId)
        }
    }

    /**
     * 清空顺序
     */
    fun clear() {
        myState.moduleOrder.clear()
    }

    /**
     * 添加手动项目
     */
    fun addManualProject(path: String) {
        myState.manualProjects.add(path)
        // 从移除列表中清除（如果存在）
        val id = path.hashCode().toString()
        myState.removedProjects.remove(id)
    }

    /**
     * 获取手动添加的项目
     */
    fun getManualProjects(): Set<String> = myState.manualProjects.toSet()

    /**
     * 移除项目（添加到移除列表）
     */
    fun removeProject(moduleId: String) {
        myState.removedProjects.add(moduleId)
        // 同时也尝试从手动列表中移除（如果它是手动添加的）
        // 注意：这里无法反向查找 ID 对应的路径，所以只能在扫描时处理，或者手动维护映射
        // 简单处理：如果是手动添加的，我们希望能彻底删除。
        // 但由于 ID 是 hashCode，可能有冲突风险，但在本项目中 id = path.hashCode()
        // 我们可以遍历 manualProjects 计算 hash 来移除
        val iterator = myState.manualProjects.iterator()
        while (iterator.hasNext()) {
            val path = iterator.next()
            if (path.hashCode().toString() == moduleId) {
                iterator.remove()
                break
            }
        }
    }

    /**
     * 检查模块是否已被移除
     */
    fun isRemoved(moduleId: String): Boolean = moduleId in myState.removedProjects

    /**
     * 重置移除列表（用于重新发现所有模块）
     */
    fun resetRemovedProjects() {
        myState.removedProjects.clear()
    }

    companion object {
        fun getInstance(project: Project): ModuleOrderState =
            project.getService(ModuleOrderState::class.java)
    }
}
