package com.dilongdann.quickrun.toolbar

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag

@Service(Service.Level.PROJECT)
@State(
    name = "QuickRunConfigService",
    storages = [Storage("quick_run.xml")]
)
class QuickRunConfigService(private val project: Project) : PersistentStateComponent<QuickRunConfigService.State> {

    companion object {
        fun getInstance(project: Project): QuickRunConfigService = project.getService(QuickRunConfigService::class.java)
    }

    class State {
        @Tag("items")
        var items: MutableList<Item> = mutableListOf()
    }

    class Item(
        @Attribute var key: String = "",                 // typeId::name
        @Attribute var displayName: String? = null,      // null => usar nombre real
        @Attribute var enabled: Boolean = false,         // visible en la toolbar
        @Attribute var showName: Boolean = true          // mostrar el nombre junto al icono
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    fun getItems(): MutableList<Item> = myState.items

    fun upsertOrAppend(key: String, displayName: String?, enabled: Boolean) {
        val found = myState.items.indexOfFirst { it.key == key }
        if (found >= 0) {
            myState.items[found].displayName = displayName
            myState.items[found].enabled = enabled
        } else {
            // showName queda a true por defecto
            myState.items.add(Item(key, displayName, enabled))
        }
    }

    fun replaceAllInOrder(newItems: List<Item>) {
        myState.items.clear()
        myState.items.addAll(newItems)
    }
}
