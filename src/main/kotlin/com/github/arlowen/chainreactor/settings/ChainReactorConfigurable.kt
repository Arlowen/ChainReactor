package com.github.arlowen.chainreactor.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/**
 * ChainReactor 设置页面
 * 在 Settings -> Tools -> ChainReactor 中显示
 */
class ChainReactorConfigurable : Configurable {

    private var mainPanel: JPanel? = null
    private var scriptNameField: JBTextField? = null
    private var timeoutSpinner: JSpinner? = null
    private var continueOnFailureCheckbox: JBCheckBox? = null

    override fun getDisplayName(): String = "ChainReactor"

    override fun createComponent(): JComponent {
        val settings = ChainReactorSettings.getInstance()

        scriptNameField = JBTextField(settings.scriptName, 30)
        timeoutSpinner = JSpinner(SpinnerNumberModel(settings.timeoutSeconds.toInt(), 30, 3600, 30))
        continueOnFailureCheckbox = JBCheckBox("失败时继续执行后续模块", settings.continueOnFailure)

        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(
                JBLabel("脚本文件名:"),
                scriptNameField!!,
                1,
                false
            )
            .addTooltip("要扫描的脚本文件名，例如: all_build.sh")
            .addLabeledComponent(
                JBLabel("执行超时 (秒):"),
                timeoutSpinner!!,
                1,
                false
            )
            .addTooltip("单个脚本的最大执行时间")
            .addComponent(continueOnFailureCheckbox!!, 1)
            .addTooltip("勾选后，即使某个模块失败也会继续执行后续模块")
            .addComponentFillVertically(JPanel(), 0)
            .panel

        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val settings = ChainReactorSettings.getInstance()
        return scriptNameField?.text != settings.scriptName ||
                (timeoutSpinner?.value as? Int)?.toLong() != settings.timeoutSeconds ||
                continueOnFailureCheckbox?.isSelected != settings.continueOnFailure
    }

    override fun apply() {
        val settings = ChainReactorSettings.getInstance()
        settings.scriptName = scriptNameField?.text ?: "all_build.sh"
        settings.timeoutSeconds = (timeoutSpinner?.value as? Int)?.toLong() ?: 300
        settings.continueOnFailure = continueOnFailureCheckbox?.isSelected ?: false
    }

    override fun reset() {
        val settings = ChainReactorSettings.getInstance()
        scriptNameField?.text = settings.scriptName
        timeoutSpinner?.value = settings.timeoutSeconds.toInt()
        continueOnFailureCheckbox?.isSelected = settings.continueOnFailure
    }

    override fun disposeUIResources() {
        mainPanel = null
        scriptNameField = null
        timeoutSpinner = null
        continueOnFailureCheckbox = null
    }
}
