package com.github.arlowen.chainreactor.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * ChainReactor 插件全局配置
 * 使用 PersistentStateComponent 实现持久化
 */
@State(
    name = "ChainReactorSettings",
    storages = [Storage("chainreactor-settings.xml")]
)
class ChainReactorSettings : PersistentStateComponent<ChainReactorSettings.State> {

    /**
     * 配置状态数据类
     */
    data class State(
        /** 要扫描的脚本文件名 */
        var scriptName: String = "all_build.sh",

        /** 执行超时时间（秒） */
        var timeoutSeconds: Long = 300,

        /** 失败时是否继续执行 */
        var continueOnFailure: Boolean = false
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    /** 脚本文件名 */
    var scriptName: String
        get() = myState.scriptName
        set(value) {
            myState.scriptName = value
        }

    /** 执行超时时间 */
    var timeoutSeconds: Long
        get() = myState.timeoutSeconds
        set(value) {
            myState.timeoutSeconds = value
        }

    /** 失败时是否继续 */
    var continueOnFailure: Boolean
        get() = myState.continueOnFailure
        set(value) {
            myState.continueOnFailure = value
        }

    companion object {
        /**
         * 获取实例
         */
        fun getInstance(): ChainReactorSettings =
            ApplicationManager.getApplication().getService(ChainReactorSettings::class.java)
    }
}
