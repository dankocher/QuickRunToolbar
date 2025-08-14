package com.dilongdann.quickrun.toolbar

import com.intellij.icons.AllIcons
import com.intellij.execution.RunManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.project.DumbAware

class QuickRunActionGroup : ActionGroup(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val project = e?.project ?: return emptyArray()
        val runManager = RunManager.getInstance(project)
        val cfgService = QuickRunConfigService.getInstance(project)

        val byKey = runManager.allSettings
            .asSequence()
            .filter { !it.isTemporary }
            .associateBy { IconSelectionService.configKey(it.name, it.type.id) }

        val actions = mutableListOf<AnAction>()
        // Solo mostrar los habilitados, en el orden guardado
        cfgService.getItems().forEach { item ->
            if (!item.enabled) return@forEach
            val settings = byKey[item.key] ?: return@forEach
            val displayName = item.displayName ?: settings.name
            actions.add(QuickRunAction(settings.name, displayName))
        }

        // Botón Edit siempre al final
        actions.add(QuickRunEditAction())

        return actions.toTypedArray()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
        e.presentation.icon = AllIcons.Actions.Execute
    }
}