package com.dilongdann.quickrun.toolbar

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import java.awt.event.ActionListener
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import com.intellij.util.ui.JBUI

class QuickRunAction(private val actualName: String, private val displayName: String, private val showName: Boolean) :
    com.intellij.openapi.actionSystem.AnAction(displayName, "Run \"$actualName\"", AllIcons.Actions.Execute),
    CustomComponentAction,
    DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.icon = resolveIcon(project, actualName)
        e.presentation.text = if (showName) displayName else ""
        e.presentation.description = "Run \"$actualName\""
        e.presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        runConfigurationByName(project, actualName)
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        val hasText = showName
        val button = if (hasText) {
            JButton(toHtmlPreserveCase(displayName), AllIcons.Actions.Execute)
        } else {
            JButton(AllIcons.Actions.Execute).apply { text = null }
        }
        button.toolTipText = presentation.description ?: "Run \"$actualName\""
        button.putClientProperty("ActionToolbar.smallVariant", true)
        button.iconTextGap = JBUI.scale(2)
        if (!hasText) {
            button.horizontalAlignment = JButton.CENTER
            button.horizontalTextPosition = JButton.CENTER
        }
        button.addActionListener(ActionListener {
            val dataContext = DataManager.getInstance().getDataContext(button)
            val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return@ActionListener
            runConfigurationByName(project, actualName)
        })
        return button
    }

    override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
        (component as? JButton)?.let { btn ->
            if (showName) {
                btn.text = toHtmlPreserveCase(presentation.text ?: displayName)
            } else {
                btn.text = null
                btn.horizontalAlignment = JButton.CENTER
                btn.horizontalTextPosition = JButton.CENTER
            }
            btn.icon = presentation.icon ?: AllIcons.Actions.Execute
            btn.toolTipText = presentation.description
            btn.iconTextGap = JBUI.scale(2)
        }
    }

    private fun runConfigurationByName(project: Project, name: String) {
        val runManager = RunManager.getInstance(project)
        val settings = runManager.allSettings.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }

    private fun resolveIcon(project: Project?, name: String): Icon {
        if (project != null) {
            val runManager = RunManager.getInstance(project)
            val settings = runManager.allSettings.firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (settings != null) {
                // 1) Icono elegido por el usuario en nuestro editor
                val key = IconSelectionService.configKey(settings.name, settings.type.id)
                val fromService = IconSelectionService.getInstance(project).resolveIconFromSelection(
                    IconSelectionService.getInstance(project).getSelection(key)
                )
                if (fromService != null) return fromService

                // 2) Intento de icono provisto por la configuración (si implementa getIcon())
                val configIcon = runCatching {
                    val cfg = settings.configuration
                    val m = cfg.javaClass.methods.firstOrNull { it.name == "getIcon" && it.parameterCount == 0 }
                    m?.invoke(cfg) as? Icon
                }.getOrNull()
                if (configIcon != null) return configIcon

                // 3) Fallback: icono del tipo de configuración
                settings.type.icon?.let { return it }
            }
        }
        // 4) Fallback final
        return AllIcons.Actions.Execute
    }

    private fun htmlEscape(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun toHtmlPreserveCase(text: String): String {
        return "<html><span>${htmlEscape(text)}</span></html>"
    }
}