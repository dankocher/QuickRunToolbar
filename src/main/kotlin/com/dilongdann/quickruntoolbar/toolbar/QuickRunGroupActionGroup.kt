package com.dilongdann.quickruntoolbar.toolbar

import com.intellij.icons.AllIcons
import com.intellij.execution.RunManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.project.DumbAware

class QuickRunActionGroup : com.intellij.openapi.actionSystem.ActionGroup(), DumbAware {

    @Volatile
    private var cachedNames: List<String> = emptyList()
    @Volatile
    private var cachedActions: Array<AnAction> = emptyArray()

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val project = e?.project ?: return emptyArray()

        val runManager = RunManager.getInstance(project)

        // Obtiene configuraciones permanentes y compartidas conservando el orden del RunManager (orden del UI).
        val names = runManager.allSettings
            .asSequence()
            .filter { !it.isTemporary }
            .filter { it.isShared }
            .map { it.name }
            .distinct()
            .toList()

        if (names.isEmpty()) {
            cachedNames = emptyList()
            cachedActions = emptyArray()
            return emptyArray()
        }

        if (names == cachedNames) {
            return cachedActions
        }

        val actions: Array<AnAction> = names.map { name -> QuickRunAction(name) as AnAction }.toTypedArray()
        cachedNames = names
        cachedActions = actions
        return actions
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
        e.presentation.icon = AllIcons.Actions.Execute
    }
}