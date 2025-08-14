package com.dilongdann.quickruntoolbar.toolbar

import com.intellij.execution.Executor
import com.intellij.execution.configuration.RunConfigurationExtensionBase
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class IconSelectorRunConfigurationExtension :
    com.intellij.execution.configuration.RunConfigurationExtensionBase<RunConfigurationBase<*>>() {

    override fun getEditorTitle(): String = "Icon"

    override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean = true

    override fun isEnabledFor(applicableConfiguration: RunConfigurationBase<*>, runnerSettings: RunnerSettings?): Boolean = true

    override fun patchCommandLine(
        configuration: RunConfigurationBase<*>,
        runnerSettings: RunnerSettings?,
        cmdLine: GeneralCommandLine,
        runnerId: String
    ) {
        // no-op
    }

    override fun patchCommandLine(
        configuration: RunConfigurationBase<*>,
        runnerSettings: RunnerSettings?,
        cmdLine: GeneralCommandLine,
        runnerId: String,
        executor: Executor
    ) {
        // no-op
    }

    override fun <P : RunConfigurationBase<*>> createEditor(configuration: P): SettingsEditor<P>? {
        @Suppress("UNCHECKED_CAST")
        return IconSelectorEditor(configuration.project, configuration) as SettingsEditor<P>
    }

    private class IconSelectorEditor<T : RunConfigurationBase<*>>(
        private val project: Project,
        private val configuration: T
    ) : com.intellij.openapi.options.SettingsEditor<T>() {

        private val allItems: List<IconItem> = IconItems.all()
        private var selected: IconItem = allItems.first()

        private val iconTrigger = JButton() // muestra icono+nombre y abre el popup
        private val fileField = TextFieldWithBrowseButton()
        private val preview = JBLabel()

        private val root: JPanel = JPanel(BorderLayout())

        init {
            // File chooser para SVG/PNG
            fileField.addBrowseFolderListener(
                null,
                null,
                project,
                FileChooserDescriptorFactory.createSingleFileDescriptor()
                    .withFileFilter { vf -> vf.extension?.lowercase() in setOf("svg", "png") }
            )

            iconTrigger.horizontalAlignment = JLabel.LEFT
            iconTrigger.addActionListener { showIconPopup() }
            updateIconTrigger()

            fileField.textField.document.addDocumentListener(SimpleDocListener { updatePreview() })

            val form = FormBuilder.createFormBuilder()
                .addLabeledComponent(JBLabel("Icon:"), iconTrigger, 1, false)
                .addLabeledComponent(JBLabel("Custom (SVG/PNG):"), fileField, 1, false)
                .addLabeledComponent(JBLabel("Preview:"), preview, 1, false)
                .panel

            root.add(form, BorderLayout.CENTER)
            updatePreview()
        }

        private fun key(): String {
            val typeId = configuration.type.id
            val name = configuration.name
            return IconSelectionService.configKey(name, typeId)
        }

        private fun showIconPopup() {
            val step = object : com.intellij.openapi.ui.popup.util.BaseListPopupStep<IconItem>("", allItems) {
                override fun isSpeedSearchEnabled(): Boolean = true
                override fun getTextFor(value: IconItem): String = value.displayName
                override fun getIconFor(value: IconItem): Icon? = value.icon
                override fun onChosen(selectedValue: IconItem, finalChoice: Boolean): PopupStep<*>? {
                    selected = selectedValue
                    updateIconTrigger()
                    updatePreview()
                    return PopupStep.FINAL_CHOICE
                }
            }
            val popup = JBPopupFactory.getInstance().createListPopup(step)
            popup.showUnderneathOf(iconTrigger)
        }

        private fun updateIconTrigger() {
            iconTrigger.icon = selected.icon
            iconTrigger.text = selected.displayName
        }

        private fun updatePreview() {
            val service = IconSelectionService.getInstance(project)
            val filePath = fileField.text.trim()
            val icon = if (filePath.isNotEmpty()) {
                service.resolveIconFromSelection(IconSelectionService.Entry(IconSelectionService.Mode.FILE, filePath))
            } else {
                if (selected.key.isNotBlank()) {
                    service.resolveIconFromSelection(IconSelectionService.Entry(IconSelectionService.Mode.ALL_ICONS, selected.key))
                } else null
            }
            preview.icon = icon
        }

        override fun resetEditorFrom(s: T) {
            val service = IconSelectionService.getInstance(project)
            val entry = service.getSelection(key())
            when (entry?.mode) {
                IconSelectionService.Mode.ALL_ICONS -> {
                    val found = allItems.firstOrNull { it.key == (entry.value ?: "") }
                    selected = found ?: allItems.first()
                    fileField.text = ""
                }
                IconSelectionService.Mode.FILE -> {
                    selected = allItems.first()
                    fileField.text = entry.value ?: ""
                }
                else -> {
                    selected = allItems.first()
                    fileField.text = ""
                }
            }
            updateIconTrigger()
            updatePreview()
        }

        override fun applyEditorTo(s: T) {
            val service = IconSelectionService.getInstance(project)
            val filePath = fileField.text.trim()
            val entry = if (filePath.isNotEmpty()) {
                IconSelectionService.Entry(IconSelectionService.Mode.FILE, filePath)
            } else if (selected.key.isNotBlank()) {
                IconSelectionService.Entry(IconSelectionService.Mode.ALL_ICONS, selected.key)
            } else {
                IconSelectionService.Entry(IconSelectionService.Mode.DEFAULT, null)
            }
            service.setSelection(key(), entry)
        }

        override fun createEditor(): JComponent = root
    }
}

// Document listener simple para reaccionar a cambios en texto
private class SimpleDocListener(private val onChange: () -> Unit) : javax.swing.event.DocumentListener {
    override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
    override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
    override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
}
