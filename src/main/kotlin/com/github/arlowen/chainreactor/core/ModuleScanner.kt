package com.github.arlowen.chainreactor.core

import com.github.arlowen.chainreactor.model.BuildModule
import com.github.arlowen.chainreactor.state.ModuleOrderState
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File

/**
 * 模块扫描器
 * 扫描项目目录构建模块
 */
class ModuleScanner(private val project: Project) {

    companion object {
        private val LOG = thisLogger()
    }

    /**
     * 扫描项目中的构建模块
     * 1. 扫描项目根目录下的子模块
     * 2. 合并手动添加的模块
     * 3. 过滤已移除的模块
     */
    fun scan(): List<BuildModule> {
        val state = ModuleOrderState.getInstance(project)
        val modules = mutableListOf<BuildModule>()
        
        // 1. 扫描项目目录
        val projectBasePath = project.basePath
        if (projectBasePath != null) {
            val rootDir = File(projectBasePath)
            val subDirs = rootDir.listFiles()?.filter { it.isDirectory && isProjectDirectory(it) } ?: emptyList()
            
            subDirs.forEach { dir ->
                val module = BuildModule.fromDirectory(dir.absolutePath)
                if (!state.isRemoved(module.id)) {
                    modules.add(module)
                }
            }
        }
        
        // 2. 添加手动项目
        state.getManualProjects().forEach { path ->
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                // 避免重复添加 (已扫描到的)
                if (modules.none { it.workingDir == path }) {
                     val module = BuildModule.fromDirectory(path)
                     if (!state.isRemoved(module.id)) {
                         modules.add(module)
                     }
                }
            }
        }
        
        LOG.info("扫描完成，共找到 ${modules.size} 个模块")
        return modules.sortedBy { it.name }
    }

    private fun isProjectDirectory(dir: File): Boolean {
        // 判断是否为项目目录：包含构建文件
        return dir.resolve("pom.xml").exists() || 
               dir.resolve("build.gradle").exists() || 
               dir.resolve("build.gradle.kts").exists()
    }
}
