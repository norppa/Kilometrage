package com.ducksoup.kilometrage.fragment

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ducksoup.kilometrage.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.io.FileWriter

class RecordsFragment : Fragment() {

    private val dataViewModel: DataViewModel by viewModels {
        DataViewModelFactory(DB.getDao(requireContext()))
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_records, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = "Kilometrage: Logs"

        view.findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { openNewLogDialog() }

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerview)
        val adapter = Adapter(::shareLog, ::openEditRecordDialog, ::openDeleteRecordDialog)
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

    private fun shareLog(record: Record) {
        dataViewModel.exportEntries(record) { list ->
            val ctx = requireContext()
            val filename = "${record.name}.csv"
            File.createTempFile(filename, null, ctx.filesDir)
            val file = File(ctx.filesDir, filename)
            list.forEach { file.appendText("${it.distance};${it.date}\n") }
            val uri = FileProvider.getUriForFile(ctx, "com.ducksoup.provider", file);

            val intent = Intent().apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "text/csv"
            }
            startActivity(intent)
        }
    }

    private fun openEditRecordDialog(record: Record) {
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_add_record, null)
        view.findViewById<EditText>(R.id.name).setText(record.name)
        AlertDialog.Builder(requireActivity())
            .setTitle("Rename a log")
            .setView(view)
            .setPositiveButton("Rename") { _, _ ->
                val name = view.findViewById<EditText>(R.id.name).text.toString()
                dataViewModel.updateRecord(Record(record.id, name))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openDeleteRecordDialog(record: Record) {
        AlertDialog.Builder(requireActivity())
            .setTitle("Delete log")
            .setMessage("Are you sure you want to delete log \"${record.name}\" and all its entries? This action can not be undone.")
            .setPositiveButton("Delete") { _, _ ->
                dataViewModel.deleteRecord(record)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    inner class Adapter(
        val share: (Record) -> Unit,
        val edit: (Record) -> Unit,
        val delete: (Record) -> Unit
    ) :
        ListAdapter<Record, Adapter.ViewHolder>(Record.Comparator()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.recyclerview_item_record, parent, false)
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val current = getItem(position)
            holder.name.text = current.name
            holder.share.setOnClickListener { share(current) }
            holder.delete.setOnClickListener { delete(current) }
            holder.edit.setOnClickListener { edit(current) }
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val name: TextView = itemView.findViewById(R.id.name)
            val share: ImageView = itemView.findViewById(R.id.share)
            val edit: ImageView = itemView.findViewById(R.id.edit)
            val delete: ImageView = itemView.findViewById(R.id.delete)
        }
    }
}