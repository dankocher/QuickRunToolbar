package com.dilongdann.quickrun.toolbar

import com.intellij.icons.AllIcons
import com.intellij.execution.RunManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ide.DataManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Component
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import javax.swing.TransferHandler
import javax.swing.SwingUtilities
import java.awt.Dimension
import java.awt.Insets

class QuickRunEditAction : AnAction("Edit", "Edit Quick Run Buttons", AllIcons.Actions.Edit), CustomComponentAction, DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        val btn = JButton("Edit", AllIcons.Actions.Edit)
        btn.putClientProperty("ActionToolbar.smallVariant", true)
        btn.size.width = 28
        btn.addActionListener {
            val dataContext = DataManager.getInstance().getDataContext(btn)
            val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return@addActionListener
            actionPerformed(AnActionEvent.createFromAnAction(this, null, place, dataContext))
        }
        return btn
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        QuickRunConfigDialog(project).show()
    }
}

private class QuickRunConfigDialog(private val project: Project) : DialogWrapper(project, true) {

    private val runManager = RunManager.getInstance(project)
    private val iconService = IconSelectionService.getInstance(project)
    private val cfgService = QuickRunConfigService.getInstance(project)

    private val model: RowsModel
    private val table: JBTable

    init {
        title = "Quick Run: Edit Buttons"
        val rows = buildRows()
        model = RowsModel(rows, iconService)
        table = JBTable(model).apply {
            rowHeight = 28
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            columnModel.getColumn(0).width = 28
            columnModel.getColumn(1).width = 36
            columnModel.getColumn(2).preferredWidth = 300
            columnModel.getColumn(3).width = 80
            // Renderers/Editors
            columnModel.getColumn(0).cellRenderer = HandleRenderer()
            columnModel.getColumn(1).cellRenderer = IconButtonRenderer(iconService)
            columnModel.getColumn(1).cellEditor = IconButtonEditor(project, iconService, this@QuickRunConfigDialog.model)
            columnModel.getColumn(2).cellRenderer = DefaultTableCellRenderer()
            columnModel.getColumn(2).cellEditor = DefaultCellEditor(JBTextField())
            columnModel.getColumn(3).cellRenderer = getDefaultRenderer(Boolean::class.java)
            columnModel.getColumn(3).cellEditor = DefaultCellEditor(JCheckBox())
            // DnD filas
            dragEnabled = true
            dropMode = DropMode.INSERT_ROWS
            transferHandler = RowTransferHandler(this, this@QuickRunConfigDialog.model)
        }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(JScrollPane(table), BorderLayout.CENTER)
        val hint = JBLabel("Tip: drag using the left handle to reorder.").apply {
            icon = AllIcons.General.BalloonInformation
        }
        panel.add(hint, BorderLayout.SOUTH)
        return panel
    }

    override fun doOKAction() {
        try {
            val items = model.rows.map { row ->
                QuickRunConfigService.Item(row.key, if (row.displayName.isNullOrBlank()) null else row.displayName, row.enabled)
            }
            cfgService.replaceAllInOrder(items)
            super.doOKAction()
        } catch (t: Throwable) {
            Messages.showErrorDialog(project, "Failed to save changes:\n${t.message}", "Quick Run")
        }
    }

    private data class Row(
        val key: String,                 // typeId::name
        val typeId: String,
        val realName: String,
        var displayName: String?,
        var enabled: Boolean
    )

    private fun buildRows(): MutableList<Row> {
        val settings = runManager.allSettings
            .asSequence()
            .filter { !it.isTemporary }
            .toList()

        val byKey = settings.associateBy { IconSelectionService.configKey(it.name, it.type.id) }
        val saved = cfgService.getItems().toMutableList()

        // Empezamos por el orden guardado
        val out = mutableListOf<Row>()
        saved.forEach { item ->
            val s = byKey[item.key] ?: return@forEach
            out.add(Row(item.key, s.type.id, s.name, item.displayName, item.enabled))
        }
        // Añadimos los que no estén guardados al final, desactivados por defecto
        settings.forEach { s ->
            val key = IconSelectionService.configKey(s.name, s.type.id)
            if (out.none { it.key == key }) {
                out.add(Row(key, s.type.id, s.name, null, false))
            }
        }
        return out
    }

    private class RowsModel(val rows: MutableList<Row>, private val iconService: IconSelectionService) : AbstractTableModel() {
        private val columns = arrayOf(" ", "Icon", "Name", "Enabled")

        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(column: Int): String = columns[column]
        override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
            0 -> Icon::class.java
            1 -> Icon::class.java
            2 -> String::class.java
            3 -> Boolean::class.java
            else -> Any::class.java
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex != 0

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
            val r = rows[rowIndex]
            return when (columnIndex) {
                0 -> AllIcons.General.Drag
                1 -> iconService.resolveIconFromSelection(iconService.getSelection(r.key)) ?: AllIcons.Actions.Execute
                2 -> r.displayName ?: r.realName
                3 -> r.enabled
                else -> null
            }
        }

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            val r = rows[rowIndex]
            when (columnIndex) {
                2 -> r.displayName = (aValue as? String)?.takeIf { it.isNotBlank() }
                3 -> r.enabled = (aValue as? Boolean) == true
            }
            fireTableRowsUpdated(rowIndex, rowIndex)
        }

        fun moveRow(from: Int, to: Int) {
            if (from == to) return
            val item = rows.removeAt(from)
            val insertAt = if (to > from) to - 1 else to
            rows.add(insertAt, item)
            fireTableDataChanged()
        }
    }

    // Render handle
    private class HandleRenderer : TableCellRenderer {
        override fun getTableCellRendererComponent(table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
            return JBLabel(AllIcons.General.Drag).apply {
                horizontalAlignment = SwingConstants.CENTER
                verticalAlignment = SwingConstants.CENTER
                isOpaque = false
                preferredSize = Dimension(24, 24)
                minimumSize = preferredSize
                maximumSize = preferredSize
            }
        }
    }

    // Icono como botón (renderer)
    private class IconButtonRenderer(@Suppress("UNUSED_PARAMETER") private val iconService: IconSelectionService) : TableCellRenderer {
        override fun getTableCellRendererComponent(table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
            val icon = (value as? Icon) ?: AllIcons.Actions.Execute
            return JBLabel(icon).apply {
                horizontalAlignment = SwingConstants.CENTER
                verticalAlignment = SwingConstants.CENTER
                isOpaque = false
                preferredSize = Dimension(24, 24)
                minimumSize = preferredSize
                maximumSize = preferredSize
            }
        }
    }

    // Editor que abre el selector de iconos al pulsar
    private class IconButtonEditor(
        private val project: Project,
        private val iconService: IconSelectionService,
        private val model: RowsModel
    ) : AbstractCellEditor(), TableCellEditor {
        private var button: JButton? = null
        private var rowIndex: Int = -1

        override fun getTableCellEditorComponent(table: JTable, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
            rowIndex = row
            val icon = (value as? Icon) ?: AllIcons.Actions.Execute
            button = JButton(icon).apply {
                putClientProperty("ActionToolbar.smallVariant", true)
                addActionListener { SwingUtilities.invokeLater { openPopup(this) } }
            }
            return button!!
        }

        override fun getCellEditorValue(): Any? = null

        private fun openPopup(comp: JComponent) {
            // Deshabilita el botón momentáneamente y carga íconos en background
            comp.isEnabled = false
            IconItems.allAsync(project) { allItems ->
                SwingUtilities.invokeLater {
                    try {
                        val step = object : com.intellij.openapi.ui.popup.util.BaseListPopupStep<IconItem>("", allItems) {
                            override fun isSpeedSearchEnabled(): Boolean = true
                            override fun getTextFor(value: IconItem): String = value.displayName
                            override fun getIconFor(value: IconItem): Icon? = value.icon
                            override fun onChosen(selectedValue: IconItem, finalChoice: Boolean): com.intellij.openapi.ui.popup.PopupStep<*>? {
                                val r = model.rows[rowIndex]
                                val entry = when {
                                    selectedValue.key.isBlank() -> IconSelectionService.Entry(IconSelectionService.Mode.DEFAULT, null)
                                    selectedValue.key.startsWith("AllIcons") ->
                                        IconSelectionService.Entry(IconSelectionService.Mode.ALL_ICONS, selectedValue.key)
                                    selectedValue.key.startsWith("plugin:") -> {
                                        val raw = selectedValue.key.removePrefix("plugin:")
                                        IconSelectionService.Entry(IconSelectionService.Mode.PLUGIN_RESOURCE, raw)
                                    }
                                    selectedValue.key.startsWith("rcType:") -> {
                                        val id = selectedValue.key.removePrefix("rcType:")
                                        IconSelectionService.Entry(IconSelectionService.Mode.RC_TYPE, id)
                                    }
                                    else -> IconSelectionService.Entry(IconSelectionService.Mode.DEFAULT, null)
                                }
                                iconService.setSelection(r.key, entry)
                                (comp as? JButton)?.icon = iconService.resolveIconFromSelection(entry) ?: AllIcons.Actions.Execute
                                stopCellEditing()
                                return FINAL_CHOICE
                            }
                        }
                        val popup = JBPopupFactory.getInstance().createListPopup(step)
                        val dataContext = DataManager.getInstance().getDataContext(comp)
                        popup.showInBestPositionFor(dataContext)
                    } finally {
                        comp.isEnabled = true
                    }
                }
            }
        }
    }

    // Checkbox fijo
    private class FixedCheckBoxRenderer : TableCellRenderer {
        private val check = JBCheckBox().apply {
            isOpaque = false
            horizontalAlignment = SwingConstants.CENTER
            preferredSize = Dimension(20, 20)
            minimumSize = preferredSize
            maximumSize = preferredSize
            text = null
            isFocusPainted = false
        }
        override fun getTableCellRendererComponent(table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
            check.isSelected = (value as? Boolean) == true
            return check
        }
    }

    private class FixedCheckBoxEditor : AbstractCellEditor(), TableCellEditor {
        private val check = JBCheckBox().apply {
            isOpaque = false
            horizontalAlignment = SwingConstants.CENTER
            preferredSize = Dimension(20, 20)
            minimumSize = preferredSize
            maximumSize = preferredSize
            text = null
            isFocusPainted = false
            addActionListener { stopCellEditing() }
        }
        override fun getTableCellEditorComponent(table: JTable, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
            check.isSelected = (value as? Boolean) == true
            return check
        }
        override fun getCellEditorValue(): Any = check.isSelected
    }

    // DnD de filas
    private class RowTransferable(val rows: IntArray) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.stringFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.stringFlavor
        override fun getTransferData(flavor: DataFlavor): Any = rows.joinToString(",")
    }

    private class RowTransferHandler(private val table: JBTable, private val model: RowsModel) : TransferHandler() {
        private var indices: IntArray = intArrayOf()

        override fun getSourceActions(c: JComponent?): Int = MOVE

        override fun createTransferable(c: JComponent?): Transferable {
            indices = table.selectedRows
            return RowTransferable(indices)
        }

        override fun canImport(support: TransferHandler.TransferSupport): Boolean {
            return support.isDrop && support.isDataFlavorSupported(DataFlavor.stringFlavor)
        }

        override fun importData(support: TransferHandler.TransferSupport): Boolean {
            if (!canImport(support)) return false
            val dropLocation = support.dropLocation as JTable.DropLocation
            val index = dropLocation.row
            val from = indices.firstOrNull() ?: return false
            model.moveRow(from, index)
            table.selectionModel.setSelectionInterval(index, index)
            return true
        }
    }
}
