package com.dilongdann.quickrun.toolbar

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Property
import java.io.File
import javax.swing.Icon
import javax.swing.ImageIcon
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.vfs.VfsUtilCore
import javax.swing.JComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState

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

    enum class Mode { DEFAULT, ALL_ICONS, FILE, PLUGIN_RESOURCE, RC_TYPE }

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
            Mode.PLUGIN_RESOURCE -> entry.value?.let { resolvePluginResource(it) }
            Mode.RC_TYPE -> entry.value?.let { resolveRunConfigurationTypeIcon(it) }
        }
    }

    // value format: "<pluginId>::/icons/....svg"
    private fun resolvePluginResource(value: String): Icon? {
        val parts = value.split("::", limit = 2)
        if (parts.size != 2) return null
        val pluginId = parts[0]
        val path = parts[1]
        return try {
            val descriptor = PluginManagerCore.getPlugin(PluginId.getId(pluginId)) ?: return null
            val cl = descriptor.pluginClassLoader ?: return null
            IconLoader.getIcon(path, cl)
        } catch (_: Throwable) {
            null
        }
    }

    private fun loadIconFromFile(path: String): Icon? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val url = file.toURI().toURL()
            // Cargar sólo imágenes rasterizadas soportadas por ImageIcon (p. ej. PNG)
            ImageIcon(url)
        } catch (_: Throwable) {
            null
        }
    }

    private fun resolveRunConfigurationTypeIcon(id: String): Icon? {
        return try {
            val type = com.intellij.execution.configurations.ConfigurationTypeUtil.findConfigurationType(id)
            type?.icon
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

    // Abre el explorador (Finder/Explorer) para seleccionar un icono SVG o PNG y devuelve una Entry FILE
    fun chooseIconFile(parent: JComponent?): Entry? {
        val descriptor = FileChooserDescriptor(
            /* chooseFiles = */true,
            /* chooseFolders = */false,
            /* chooseJars = */false,
            /* chooseJarsAsFiles = */false,
            /* chooseJarContents = */false,
            /* chooseMultiple = */false
        ).apply {
            title = "Choose Custom Icon (PNG)"
            withFileFilter { vf ->
                val ext = vf.extension?.lowercase()
                ext == "png"
            }
        }

        var chosenPath: String? = null
        val app = ApplicationManager.getApplication()
        val task = {
            val vf = FileChooser.chooseFile(descriptor, parent, project, null)
            if (vf != null) {
                chosenPath = VfsUtilCore.virtualToIoFile(vf).absolutePath
            }
        }

        if (app.isDispatchThread) {
            task()
        } else {
            app.invokeAndWait(task, ModalityState.any())
        }

        return chosenPath?.let { Entry(Mode.FILE, it) }
    }

    // Convierte la clave seleccionada en Entry; si es "Choose custom icon…" abre el explorador
    fun buildEntryFromKeyInteractive(parent: JComponent?, selectedKey: String): Entry? {
        return when {
            selectedKey.isEmpty() -> Entry(Mode.DEFAULT, null)
            selectedKey == IconItems.CHOOSE_FILE_KEY -> chooseIconFile(parent)
            selectedKey.startsWith("plugin:") -> {
                // El valor esperado por PLUGIN_RESOURCE es "<pluginId>::/ruta"
                val v = selectedKey.removePrefix("plugin:")
                Entry(Mode.PLUGIN_RESOURCE, v)
            }
            selectedKey.startsWith("rcType:") -> {
                Entry(Mode.RC_TYPE, selectedKey.removePrefix("rcType:"))
            }
            else -> {
                // Por defecto, tratar como AllIcons.<...>
                Entry(Mode.ALL_ICONS, selectedKey)
            }
        }
    }
}
