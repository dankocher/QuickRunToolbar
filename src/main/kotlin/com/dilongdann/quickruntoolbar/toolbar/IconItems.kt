package com.dilongdann.quickruntoolbar.toolbar

import com.intellij.icons.AllIcons
import javax.swing.Icon

data class IconItem(val key: String, val icon: Icon?, val displayName: String)

object IconItems {
    // Construye la lista: "Default icon" + todos los AllIcons.<Group>.<Name> con su Icon
    fun all(): List<IconItem> {
        val result = mutableListOf<IconItem>()
        result.add(IconItem("", null, "Default icon")) // Default icon
        result.addAll(collectAllIcons())
        return result
    }

    private fun collectAllIcons(): List<IconItem> {
        val list = mutableListOf<IconItem>()
        fun traverse(clazz: Class<*>, prefix: String) {
            // Campos públicos tipo Icon
            runCatching {
                clazz.fields.forEach { f ->
                    if (Icon::class.java.isAssignableFrom(f.type)) {
                        runCatching {
                            val icon = f.get(null) as? Icon
                            val key = if (prefix.isEmpty()) f.name else "$prefix.${f.name}"
                            val nameFromPath = icon?.let { tryGetOriginalPath(it) }?.let { pathToHumanName(it) }
                            val display = nameFromPath ?: humanizeSimpleName(f.name)
                            list.add(IconItem(key, icon, display))
                        }
                    }
                }
            }
            // Clases anidadas
            runCatching {
                clazz.declaredClasses.forEach { nested ->
                    val p = if (prefix.isEmpty()) nested.simpleName ?: "" else "$prefix.${nested.simpleName}"
                    traverse(nested, p)
                }
            }
        }
        traverse(AllIcons::class.java, "AllIcons")
        // Orden alfabético por nombre legible
        return list.sortedBy { it.displayName }
    }

    // Intenta recuperar la ruta original del recurso (por ejemplo: /icons/actions/execute.svg)
    private fun tryGetOriginalPath(icon: Icon): String? {
        return try {
            val m = icon.javaClass.methods.firstOrNull { it.name == "getOriginalPath" && it.parameterCount == 0 }
            (m?.invoke(icon) as? String)
        } catch (_: Throwable) {
            // algunas implementaciones usan campo privado
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
        // extrae el nombre de archivo sin extensión y lo humaniza
        val file = path.substringAfterLast('/').substringBeforeLast('.')
        return humanizeSimpleName(file)
    }

    private fun humanizeSimpleName(raw: String): String {
        // Reemplaza separadores comunes y divide camelCase
        var s = raw
            .replace('-', ' ')
            .replace('_', ' ')
        s = s.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ")
        s = s.replace(Regex("(?<=[A-Z])(?=[A-Z][a-z])"), " ")
        val acronyms = setOf("API", "ADB", "CPU", "GPU", "USB", "HTTP", "HTTPS", "XML", "SQL", "SDK", "APK", "JVM", "JRE", "JDK")
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
