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
        var moduleOrder: MutableList<String> = mutableListOf()
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
     * 清空顺序
     */
    fun clear() {
        myState.moduleOrder.clear()
    }

    companion object {
        fun getInstance(project: Project): ModuleOrderState =
            project.getService(ModuleOrderState::class.java)
    }
}
