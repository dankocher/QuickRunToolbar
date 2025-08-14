package com.dilongdann.quickrun.toolbar

import com.intellij.icons.AllIcons
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.util.SVGLoader
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Property
import java.io.File
import javax.swing.Icon
import javax.swing.ImageIcon

@Service(Service.Level.PROJECT)
@State(
    name = "QuickRunIconSelections",
    storages = [Storage("quick_run.xml")] // Guardar junto a la configuración de Quick Run
)
class IconSelectionService(private val project: Project) : PersistentStateComponent<IconSelectionService.State> {

    companion object {
        fun getInstance(project: Project): IconSelectionService = project.getService(IconSelectionService::class.java)
        fun configKey(name: String, typeId: String): String = "$typeId::$name"
    }

    enum class Mode { DEFAULT, ALL_ICONS, FILE }

    data class Entry(
        @Attribute var mode: Mode = Mode.DEFAULT,
        @Attribute var value: String? = null // para ALL_ICONS: clave (p.ej. Actions.Execute), para FILE: ruta absoluta
    )

    class State {
        // key = typeId::name
        @get:Property(surroundWithTag = false)
        @get:MapAnnotation(entryTagName = "cfg", keyAttributeName = "id")
        var selections: MutableMap<String, Entry> = LinkedHashMap()
    }

    private var myState: State = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    fun getSelection(key: String): Entry? = myState.selections[key]

    fun setSelection(key: String, entry: Entry?) {
        if (entry == null || entry.mode == Mode.DEFAULT) {
            myState.selections.remove(key)
        } else {
            myState.selections[key] = entry
        }
    }

    fun resolveIconFromSelection(entry: Entry?): Icon? {
        if (entry == null) return null
        return when (entry.mode) {
            Mode.DEFAULT -> null
            Mode.ALL_ICONS -> entry.value?.let { resolveAllIconsKey(it) }
            Mode.FILE -> entry.value?.let { loadIconFromFile(it) }
        }
    }

    private fun loadIconFromFile(path: String): Icon? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val url = file.toURI().toURL()
            if (path.lowercase().endsWith(".svg")) {
                val img = SVGLoader.load(url, 1.0f)
                ImageIcon(img)
            } else {
                ImageIcon(url)
            }
        } catch (_: Throwable) {
            null
        }
    }

    // Acepta claves tipo "AllIcons.Actions.Execute" o "Actions.Execute"
    private fun resolveAllIconsKey(rawKey: String): Icon? {
        val key = rawKey.removePrefix("AllIcons.")
        val parts = key.split('.').filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        return try {
            var cls: Class<*> = AllIcons::class.java
            for (i in 0 until parts.size - 1) {
                val nested = "${cls.name}\$${parts[i]}"
                cls = Class.forName(nested)
            }
            val fieldName = parts.last()
            val field = cls.getField(fieldName)
            (field.get(null) as? Icon)
        } catch (_: Throwable) {
            null
        }
    }
}
