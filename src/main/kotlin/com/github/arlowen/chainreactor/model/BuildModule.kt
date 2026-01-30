package com.github.arlowen.chainreactor.model

/**
 * 构建模块数据模型
 */
data class BuildModule(
    /** 唯一标识（基于路径的哈希） */
    val id: String,

    /** 显示名称（取自目录名） */
    val name: String,

    /** 脚本完整路径 */
    val scriptPath: String,

    /** 工作目录 */
    val workingDir: String,

    /** 执行顺序 */
    var order: Int = 0,

    /** 自定义执行命令，为空时使用默认脚本路径 */
    var customCommand: String? = null,

    /** 是否启用（参与构建） */
    var enabled: Boolean = true
) {
    /**
     * 获取实际执行的命令
     * 如果设置了自定义命令则使用自定义命令，否则使用脚本路径
     */
    fun getEffectiveCommand(): String =
        customCommand?.takeIf { it.isNotBlank() } ?: scriptPath
    companion object {
        /**
         * 根据脚本路径创建 BuildModule
         */
        fun fromScriptPath(scriptPath: String): BuildModule {
            val file = java.io.File(scriptPath)
            val workingDir = file.parentFile?.absolutePath ?: ""
            val name = file.parentFile?.name ?: file.name

            return BuildModule(
                id = scriptPath.hashCode().toString(),
                name = name,
                scriptPath = scriptPath,
                workingDir = workingDir
            )
        }

        /**
         * 根据目录路径创建 BuildModule
         */
        fun fromDirectory(dirPath: String): BuildModule {
            val dir = java.io.File(dirPath)
            return BuildModule(
                id = dirPath.hashCode().toString(),
                name = dir.name,
                scriptPath = "./all_build.sh", // 默认脚本
                workingDir = dirPath
            )
        }
    }

    override fun toString(): String = name
}

/**
 * 模块执行状态
 */
enum class ModuleStatus {
    /** 等待执行 */
    PENDING,

    /** 正在执行 */
    RUNNING,

    /** 执行成功 */
    SUCCESS,

    /** 执行失败 */
    FAILED,

    /** 已跳过（前置任务失败） */
    SKIPPED
}
