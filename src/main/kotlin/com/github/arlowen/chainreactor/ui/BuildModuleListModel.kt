package com.github.arlowen.chainreactor.ui

import com.github.arlowen.chainreactor.model.BuildModule
import javax.swing.DefaultListModel

/**
 * 构建模块列表模型
 * 支持拖拽排序和状态更新
 */
class BuildModuleListModel : DefaultListModel<BuildModule>() {

    /**
     * 设置模块列表
     */
    fun setModules(modules: List<BuildModule>) {
        clear()
        modules.forEach { addElement(it) }
    }

    /**
     * 获取所有模块（按当前顺序）
     */
    fun getModules(): List<BuildModule> {
        val modules = mutableListOf<BuildModule>()
        for (i in 0 until size()) {
            modules.add(getElementAt(i))
        }
        return modules
    }

    /**
     * 获取模块 ID 列表（按当前顺序）
     */
    fun getModuleIds(): List<String> = getModules().map { it.id }

    /**
     * 移动模块位置
     */
    fun moveModule(fromIndex: Int, toIndex: Int) {
        if (fromIndex < 0 || fromIndex >= size() || toIndex < 0 || toIndex >= size()) {
            return
        }

        val module = remove(fromIndex)
        add(toIndex, module)

        // 更新顺序索引
        updateOrderIndices()
    }

    /**
     * 更新所有模块的顺序索引
     */
    private fun updateOrderIndices() {
        for (i in 0 until size()) {
            getElementAt(i).order = i
        }
    }

    /**
     * 根据 ID 查找模块
     */
    fun findModuleById(id: String): BuildModule? {
        for (i in 0 until size()) {
            val module = getElementAt(i)
            if (module.id == id) {
                return module
            }
        }
        return null
    }

    /**
     * 根据保存的顺序应用排序
     */
    fun applyOrder(savedOrder: List<String>) {
        if (savedOrder.isEmpty()) return

        val modules = getModules()
        val orderedModules = mutableListOf<BuildModule>()
        val remainingModules = modules.toMutableList()

        // 按照保存的顺序添加模块
        for (id in savedOrder) {
            val module = remainingModules.find { it.id == id }
            if (module != null) {
                orderedModules.add(module)
                remainingModules.remove(module)
            }
        }

        // 添加未在保存顺序中的新模块
        orderedModules.addAll(remainingModules)

        // 更新列表
        setModules(orderedModules)
    }
}
