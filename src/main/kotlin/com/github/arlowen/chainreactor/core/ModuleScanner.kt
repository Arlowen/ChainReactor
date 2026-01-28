package com.github.arlowen.chainreactor.core

import com.github.arlowen.chainreactor.model.BuildModule
import com.github.arlowen.chainreactor.settings.ChainReactorSettings
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * 模块扫描器
 * 使用 FilenameIndex 扫描项目中的脚本文件
 */
class ModuleScanner(private val project: Project) {

    companion object {
        private val LOG = thisLogger()
    }

    /**
     * 扫描项目中的构建模块
     * @param scriptName 要扫描的脚本文件名（如果为空则使用设置中的值）
     * @return 扫描到的模块列表
     */
    fun scan(scriptName: String? = null): List<BuildModule> {
        val targetScriptName = scriptName ?: ChainReactorSettings.getInstance().scriptName

        LOG.info("开始扫描项目中的 '$targetScriptName' 文件...")

        val modules = mutableListOf<BuildModule>()

        try {
            // 使用 ReadAction 执行索引查询
            val virtualFiles = ReadAction.compute<Collection<VirtualFile>, Throwable> {
                FilenameIndex.getVirtualFilesByName(
                    targetScriptName,
                    GlobalSearchScope.projectScope(project)
                )
            }

            LOG.info("找到 ${virtualFiles.size} 个 '$targetScriptName' 文件")

            virtualFiles.forEachIndexed { index, virtualFile ->
                val module = BuildModule(
                    id = virtualFile.path.hashCode().toString(),
                    name = virtualFile.parent?.name ?: virtualFile.name,
                    scriptPath = virtualFile.path,
                    workingDir = virtualFile.parent?.path ?: "",
                    order = index
                )
                modules.add(module)
                LOG.info("  发现模块: ${module.name} -> ${module.scriptPath}")
            }

        } catch (e: Exception) {
            LOG.error("扫描模块时发生错误", e)
        }

        LOG.info("扫描完成，共找到 ${modules.size} 个模块")
        return modules.sortedBy { it.name }
    }

    /**
     * 刷新扫描（重新扫描并返回结果）
     */
    fun refresh(): List<BuildModule> = scan()
}
