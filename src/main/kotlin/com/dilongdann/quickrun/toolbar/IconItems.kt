package com.dilongdann.quickrun.toolbar

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IconLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile
import javax.swing.Icon

data class IconItem(val key: String, val icon: Icon?, val displayName: String)

object IconItems {
    // Caché simple para no recalcular en cada apertura del popup
    @Volatile
    private var cached: List<IconItem>? = null
    private val building = AtomicBoolean(false)

    // Clave sentinela para lanzar el selector de archivo desde el editor
    const val CHOOSE_FILE_KEY: String = "file:CHOOSE"

    // Construye la lista: "Default icon" + AllIcons + iconos de plugins (carpetas /icons/**)
    fun all(): List<IconItem> {
        cached?.let { return it }
        // Entradas fijadas al principio
        val top = listOf(
            IconItem("", null, "Default icon"),
            IconItem(CHOOSE_FILE_KEY, null, "Choose custom icon…")
        )
        // Resto de entradas
        val rest = mutableListOf<IconItem>()
        rest.addAll(collectAllIcons())
        rest.addAll(collectRunConfigurationIcons())
        rest.addAll(collectOwnPluginIcons())
        val orderedRest = rest
            .distinctBy { it.key to it.displayName }
            .sortedBy { it.displayName }
        return buildList {
            addAll(top)
            addAll(orderedRest)
        }
    }

    // Carga rápida inmediata (Default + AllIcons) y construcción completa cacheada en background
    fun allAsync(project: Project?, onReady: (List<IconItem>) -> Unit) {
        cached?.let { onReady(it); return }

        // Entradas fijadas al principio
        val top = listOf(
            IconItem("", null, "Default icon"),
            IconItem(CHOOSE_FILE_KEY, null, "Choose custom icon…")
        )

        // Entrega inmediata: no bloquea el popup
        val quickRest = mutableListOf<IconItem>().apply {
            addAll(collectAllIcons())
            addAll(collectRunConfigurationIcons())
            addAll(collectOwnPluginIcons())
        }.distinctBy { it.key to it.displayName }
         .sortedBy { it.displayName }
        val quick = buildList {
            addAll(top)
            addAll(quickRest)
        }
        onReady(quick)

        // Construcción completa (incluye iconos de plugins) en background para próximas aperturas
        if (!building.compareAndSet(false, true)) {
            return
        }
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Loading IDE and plugin icons", false) {
            private fun build(): List<IconItem> {
                val topBg = listOf(
                    IconItem("", null, "Default icon"),
                    IconItem(CHOOSE_FILE_KEY, null, "Choose custom icon…")
                )
                val rest = mutableListOf<IconItem>()
                rest.addAll(collectAllIcons())
                rest.addAll(collectRunConfigurationIcons())
                // Asegurar que los iconos del propio plugin estén disponibles aunque falle el escaneo global
                rest.addAll(collectOwnPluginIcons())
                rest.addAll(collectPluginIcons())
                val orderedRest = rest
                    .distinctBy { it.key to it.displayName }
                    .sortedBy { it.displayName }
                return buildList {
                    addAll(topBg)
                    addAll(orderedRest)
                }
            }

            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Scanning plugin icons…"
                val list = build()
                cached = list
            }

            override fun onFinished() {
                building.set(false)
            }

            override fun onSuccess() {
                // Intencionadamente no invocamos onReady aquí para evitar abrir un segundo popup.
                // La próxima vez que se abra el selector, se usará 'cached' con la lista completa.
            }
        })
    }

    private fun collectRunConfigurationIcons(): List<IconItem> {
        return runCatching {
            val list = mutableListOf<IconItem>()
            val types = com.intellij.execution.configurations.ConfigurationType.CONFIGURATION_TYPE_EP.extensionList
            for (type in types) {
                val id = type.id
                val name = type.displayName
                val icon = runCatching { type.icon }.getOrNull()
                list.add(IconItem("rcType:$id", icon, name))
            }
            list
        }.getOrElse { emptyList() }
    }

    // ID del propio plugin para ubicar sus recursos empaquetados
    private const val THIS_PLUGIN_ID = "com.dilongdann.quickrun"

    private fun collectOwnPluginIcons(): List<IconItem> {
        return runCatching {
            val descriptor = PluginManagerCore.getPlugin(PluginId.getId(THIS_PLUGIN_ID)) ?: return emptyList()
            val pluginPath = descriptor.pluginPath
            if (Files.isDirectory(pluginPath)) {
                val list = mutableListOf<IconItem>()
                list += scanIconsInDirectory(THIS_PLUGIN_ID, pluginPath)
                // En runIde los recursos están dentro de jars en /lib: incluirlos
                list += scanIconsInPluginLibJars(THIS_PLUGIN_ID, pluginPath)
                list
            } else {
                scanIconsInZip(THIS_PLUGIN_ID, pluginPath)
            }
        }.getOrElse { emptyList() }
    }

    private fun collectAllIcons(): List<IconItem> {
        val list = mutableListOf<IconItem>()
        fun traverse(clazz: Class<*>, prefix: String) {
            runCatching {
                clazz.fields.forEach { f ->
                    if (Icon::class.java.isAssignableFrom(f.type)) {
                        runCatching {
                            val icon = f.get(null) as? Icon
                            val key = if (prefix.isEmpty()) f.name else "$prefix.${f.name}" // p.ej. AllIcons.Actions.Execute
                            val nameFromPath = icon?.let { tryGetOriginalPath(it) }?.let { pathToHumanName(it) }
                            val display = nameFromPath ?: humanizeSimpleName(f.name)
                            list.add(IconItem(key, icon, display))
                        }
                    }
                }
            }
            runCatching {
                clazz.declaredClasses.forEach { nested ->
                    val p = if (prefix.isEmpty()) nested.simpleName ?: "" else "$prefix.${nested.simpleName}"
                    traverse(nested, p)
                }
            }
        }
        traverse(AllIcons::class.java, "AllIcons")
        return list
    }

    private fun collectPluginIcons(): List<IconItem> {
        // Para evitar uso de APIs internas/sensibles entre versiones, no se escanean iconos de otros plugins.
        // La lista de iconos mostrará AllIcons, iconos de tipos de Run Configuration y los del propio plugin.
        return emptyList()
    }

    private fun scanIconsInDirectory(pluginId: String, root: Path): List<IconItem> {
        val res = mutableListOf<IconItem>()
        runCatching {
            Files.walk(root).use { stream ->
                stream.filter { Files.isRegularFile(it) }.forEach { p ->
                    val name = p.fileName.toString().lowercase()
                    if ((name.endsWith(".svg") || name.endsWith(".png")) && p.toString().contains("${java.io.File.separator}icons${java.io.File.separator}")) {
                        val rel = root.relativize(p).toString().replace(java.io.File.separatorChar, '/')
                        val path = if (rel.startsWith("/")) rel else "/$rel"
                        val key = "plugin:$pluginId::$path"
                        val icon = runCatching {
                            // Cargar icono desde el classloader del plugin si existe, si no, desde URL
                            val cl = PluginManagerCore.getPlugin(PluginId.getId(pluginId))?.pluginClassLoader
                            if (cl != null) IconLoader.getIcon(path, cl) else null
                        }.getOrNull()
                        val display = pathToHumanName(path)
                        res.add(IconItem(key, icon, display))
                    }
                }
            }
        }
        return res
    }

    // Escanea los jars del plugin (por ejemplo en runIde: <pluginPath>/lib/*.jar) buscando /icons/**.{svg,png}
    private fun scanIconsInPluginLibJars(pluginId: String, root: Path): List<IconItem> {
        val res = mutableListOf<IconItem>()
        val libDir = root.resolve("lib")
        if (!Files.isDirectory(libDir)) return res
        runCatching {
            Files.list(libDir).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.fileName.toString().lowercase().endsWith(".jar") }
                    .forEach { jar ->
                        res += scanIconsInZip(pluginId, jar)
                    }
            }
        }
        return res
    }

    private fun scanIconsInZip(pluginId: String, root: Path): List<IconItem> {
        val res = mutableListOf<IconItem>()
        runCatching {
            ZipFile(root.toFile()).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    val name = e.name.lowercase()
                    if (!e.isDirectory && (name.endsWith(".svg") || name.endsWith(".png")) && name.contains("icons/")) {
                        val path = if (e.name.startsWith("/")) e.name else "/${e.name}"
                        val key = "plugin:$pluginId::$path"
                        val icon = runCatching {
                            val cl = PluginManagerCore.getPlugin(PluginId.getId(pluginId))?.pluginClassLoader
                            if (cl != null) IconLoader.getIcon(path, cl) else null
                        }.getOrNull()
                        val display = pathToHumanName(path)
                        res.add(IconItem(key, icon, display))
                    }
                }
            }
        }
        return res
    }

    // Intenta recuperar la ruta original del recurso (por ejemplo: /icons/actions/execute.svg)
    private fun tryGetOriginalPath(icon: Icon): String? {
        return try {
            val m = icon.javaClass.methods.firstOrNull { it.name == "getOriginalPath" && it.parameterCount == 0 }
            (m?.invoke(icon) as? String)
        } catch (_: Throwable) {
            try {
                val f = icon.javaClass.declaredFields.firstOrNull { it.name.contains("OriginalPath", ignoreCase = true) }
                f?.let {
                    it.isAccessible = true
                    it.get(icon) as? String
                }
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun pathToHumanName(path: String): String {
        val file = path.substringAfterLast('/').substringBeforeLast('.')
        return humanizeSimpleName(file)
    }

    private fun humanizeSimpleName(raw: String): String {
        var s = raw
            .replace('-', ' ')
            .replace('_', ' ')
        s = s.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ")
        s = s.replace(Regex("(?<=[A-Z])(?=[A-Z][a-z])"), " ")
        val acronyms = setOf("API", "ADB", "CPU", "GPU", "USB", "HTTP", "HTTPS", "XML", "SQL", "SDK", "APK", "JVM", "JRE", "JDK", "NPM")
        val words = s.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.map { w ->
            val up = w.uppercase()
            when {
                acronyms.contains(up) -> up
                w.equals("wifi", ignoreCase = true) -> "WiFi"
                else -> w.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }
        return words.joinToString(" ")
    }
}
