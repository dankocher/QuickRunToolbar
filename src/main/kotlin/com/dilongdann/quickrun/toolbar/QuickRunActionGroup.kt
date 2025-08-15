    import com.intellij.execution.RunManager
    import com.intellij.icons.AllIcons
    import com.intellij.openapi.actionSystem.ActionGroup
    import com.intellij.openapi.actionSystem.ActionUpdateThread
    import com.intellij.openapi.actionSystem.AnAction
    import com.intellij.openapi.actionSystem.AnActionEvent
    import com.intellij.openapi.project.DumbAware
    import com.dilongdann.quickrun.toolbar.IconSelectionService
    import com.dilongdann.quickrun.toolbar.QuickRunAction
    import com.dilongdann.quickrun.toolbar.QuickRunConfigService
    import com.dilongdann.quickrun.toolbar.QuickRunEditAction

    class QuickRunActionGroup : ActionGroup(), DumbAware {

            private data class Model(val actualName: String, val displayName: String, val showName: Boolean)

            @Volatile
            private var cachedModel: List<Model> = emptyList()
            @Volatile
            private var cachedActions: Array<AnAction> = emptyArray()
    private val editAction = QuickRunEditAction()

    override fun getActionUpdateThread(): ActionUpdateThread =
        ActionUpdateThread.BGT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val project = e?.project ?: return emptyArray()
        val runManager = RunManager.getInstance(project)
        val cfgService = QuickRunConfigService.getInstance(project)

        val byKey = runManager.allSettings
            .asSequence()
            .filter { !it.isTemporary }
            .associateBy { IconSelectionService.configKey(it.name, it.type.id) }

        // Modelo: solo habilitados y existentes, en orden guardado
        val model: List<Model> = buildList {
            cfgService.getItems().forEach { item ->
                if (!item.enabled) return@forEach
                val settings = byKey[item.key] ?: return@forEach
                val displayName = item.displayName ?: settings.name
                add(Model(settings.name, displayName, item.showName))
            }
        }

        if (model == cachedModel) {
            return cachedActions
        }

        val actions = ArrayList<AnAction>(model.size + 1)
        model.forEach { m ->
            actions.add(QuickRunAction(m.actualName, m.displayName, m.showName))
        }
        actions.add(editAction)

        cachedModel = model
        cachedActions = actions.toTypedArray()
        return cachedActions
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
        e.presentation.icon = AllIcons.Actions.Execute
    }
}
