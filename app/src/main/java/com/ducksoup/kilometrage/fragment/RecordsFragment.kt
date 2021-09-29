package com.ducksoup.kilometrage.fragment

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ducksoup.kilometrage.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.time.ZoneId
import java.util.*

class RecordsFragment : Fragment() {
    private val selectedRecords = mutableListOf<Record>()
    private lateinit var addButton: FloatingActionButton
    private lateinit var deleteButton: FloatingActionButton
    private lateinit var editButton: FloatingActionButton
    private lateinit var exportButton: FloatingActionButton

    private val dataViewModel: DataViewModel by viewModels {
        DataViewModelFactory(DB.getDao(requireContext()))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_records, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = "Kilometrage: Logs"

        addButton = view.findViewById(R.id.addButton)
        deleteButton = view.findViewById(R.id.deleteButton)
        editButton = view.findViewById(R.id.editButton)
        exportButton = view.findViewById(R.id.exportButton)

        addButton.setOnClickListener {
            println("SELECTED: $selectedRecords")
            openNewLogDialog()
        }
        deleteButton.setOnClickListener { openDeleteSelectedRecordsDialog() }
        editButton.setOnClickListener { openEditRecordDialog() }
        exportButton.setOnClickListener { exportSelected() }

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerview)
        val adapter = Adapter()
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        dataViewModel.records.observe(viewLifecycleOwner, { observed ->
            observed?.let { records ->
                view.findViewById<TextView>(R.id.header).visibility =
                    if (records.isEmpty()) View.VISIBLE else View.GONE
                adapter.submitList(records)
            }
        })
    }

    private fun openNewLogDialog() {
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_add_record, null)
        AlertDialog.Builder(requireActivity())
            .setTitle("Add a new log")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                val name = view.findViewById<EditText>(R.id.name).text.toString()
                dataViewModel.insert(Record(0, name))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportSelected() {
        dataViewModel.exportEntries(selectedRecords) { list ->
            val ctx = requireContext()
            val workbook = XSSFWorkbook()
            val style = workbook.createCellStyle()
            style.dataFormat = workbook.creationHelper.createDataFormat().getFormat("m/d/yy")

            selectedRecords.forEach { record ->
                val sheet = workbook.createSheet(record.name)
                list.filter { it.recordId == record.id }
                    .sortedByDescending { it.date }
                    .forEachIndexed { index, entry ->
                        val row = sheet.createRow(index)
                        val distanceCell = row.createCell(0)
                        distanceCell.setCellValue(entry.distance)

                        val dateCell = row.createCell(1)
                        val date =
                            Date.from(entry.date?.atZone(ZoneId.systemDefault())?.toInstant())
                        dateCell.setCellValue(date)
                        dateCell.cellStyle = style
                    }
                sheet.setColumnWidth(1, 10 * 256)
            }

            val filename = "kilometrage_logs.xls"
            File.createTempFile(filename, null, ctx.filesDir)
            val file = File(ctx.filesDir, filename)
            val fileOut = FileOutputStream(file)
            workbook.write(fileOut)
            fileOut.close()

            val uri = FileProvider.getUriForFile(ctx, "com.ducksoup.provider", file)

            val intent = Intent().apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "text/csv"
            }
            startActivity(intent)
        }
    }

    private fun openEditRecordDialog() {
        val record = selectedRecords[0]
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_add_record, null)
        view.findViewById<EditText>(R.id.name).setText(record.name)
        AlertDialog.Builder(requireActivity())
            .setTitle("Rename a log")
            .setView(view)
            .setPositiveButton("Rename") { _, _ ->
                val name = view.findViewById<EditText>(R.id.name).text.toString()
                dataViewModel.updateRecord(Record(record.id, name))
                selectedRecords.clear()
                updateButtonVisibility()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openDeleteSelectedRecordsDialog() {
        val message = "Are you sure you want to delete following logs and all their entries?\n" +
                selectedRecords.fold("", { acc, cur -> "$acc\n- ${cur.name}" }) +
                "\nThis action can not be undone."
        AlertDialog.Builder(requireActivity())
            .setTitle("Delete logs")
            .setMessage(message)
            .setPositiveButton("Delete")
            { _, _ ->
                selectedRecords.forEach { record ->
                    dataViewModel.deleteRecord(record)
                }
                selectedRecords.clear()
                updateButtonVisibility()

            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateButtonVisibility() {
        when (selectedRecords.size) {
            0 -> {
                deleteButton.visibility = View.GONE
                editButton.visibility = View.GONE
                exportButton.visibility = View.GONE
            }
            1 -> {
                deleteButton.visibility = View.VISIBLE
                editButton.visibility = View.VISIBLE
                exportButton.visibility = View.VISIBLE
            }
            else -> {
                deleteButton.visibility = View.VISIBLE
                editButton.visibility = View.GONE
                exportButton.visibility = View.VISIBLE
            }
        }

    }

    inner class Adapter : ListAdapter<Record, Adapter.ViewHolder>(Record.Comparator()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.recyclerview_item_record, parent, false)
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val current = getItem(position)
            holder.checkbox.isChecked = selectedRecords.contains(current)
            holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedRecords.add(current)
                } else {
                    selectedRecords.remove(current)
                }
                updateButtonVisibility()
            }
            holder.name.text = current.name
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
            val name: TextView = itemView.findViewById(R.id.name)
        }
    }
}