package com.ducksoup.kilometrage.fragment

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.ContextMenu.ContextMenuInfo
import android.widget.*
import androidx.core.view.MenuCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ducksoup.kilometrage.*
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*


class MainFragment : Fragment() {

    private lateinit var adapter: Adapter
    private var records = listOf<Record>()
    private lateinit var tabLayout: TabLayout

    private val dataViewModel: DataViewModel by viewModels {
        DataViewModelFactory(DB.getDao(requireContext()))
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu, menu)
        MenuCompat.setGroupDividerEnabled(menu, true)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.manage_logs -> {
                findNavController().navigate(R.id.action_mainFragment_to_recordsFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        activity?.title = "Kilometrage"
        view.findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            openAddEntryDialog()
        }
        tabLayout = view.findViewById(R.id.tablayout)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val id = tab?.id ?: throw Exception("Tab must not be null")
                dataViewModel.selectedRecordId.value = id
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerview)
        adapter = Adapter()
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        dataViewModel.records.observe(viewLifecycleOwner, { observed ->
            observed?.let { records ->
                Log.i("DEBUG", "records ${records.size}")
                if (records.isEmpty()) {
                    openFirstRecordDialog()
                } else {
                    this.records = records
                    tabLayout.removeAllTabs()
                    records.forEach {
                        tabLayout.addTab(tabLayout.newTab().setText(it.name).setId(it.id))
                    }
                }
            }

        })

        dataViewModel.entries.observe(viewLifecycleOwner, { entries ->
            entries?.let { adapter.submitList(it) }
        })
    }


    private fun openFirstRecordDialog() {
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_add_record, null)
        AlertDialog.Builder(requireActivity())
            .setTitle("Create your first log")
            .setMessage("You do not have any logs yet. Start using Kilometrage by creating your first log.")
            .setView(view)
            .setPositiveButton("Create") { _, _ ->
                val name = view.findViewById<EditText>(R.id.name).text.toString()
                dataViewModel.insert(Record(0, name))
            }
            .setCancelable(false)
            .show()
    }


    private fun openAddEntryDialog() {
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_add_entry, null)
        val distanceText = view.findViewById<TextInputEditText>(R.id.distance)
        val dropdown = view.findViewById<AutoCompleteTextView>(R.id.log_text)
        val dateTextLayout = view.findViewById<TextInputLayout>(R.id.date_text_layout)
        val dateText = view.findViewById<TextInputEditText>(R.id.date_text)
        var date = LocalDateTime.now()

        fun openCalendar() {
            dateText.clearFocus()
            MaterialDatePicker.Builder
                .datePicker()
                .setSelection(System.currentTimeMillis())
                .build().apply {
                    addOnPositiveButtonClickListener {
                        date = LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
                        dateText.setText(DateTimeFormatter.ofPattern("dd.MM.yyyy").format(date))
                    }
                }
                .show(
                    requireActivity().supportFragmentManager,
                    MaterialDatePicker::class.java.canonicalName
                )
        }


        dateText.setText(DateTimeFormatter.ofPattern("dd.MM.yyyy").format(date))
        dateTextLayout.setEndIconOnClickListener { openCalendar() }
        dateText.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) openCalendar() }

        dropdown.setAdapter(
            ArrayAdapter(requireContext(), R.layout.dropdown_item, records.map { it.name })
        )
        dropdown.setText(
            records.find { it.id == dataViewModel.selectedRecordId.value }?.name,
            false
        )

        AlertDialog.Builder(requireActivity())
            .setTitle("Add a new entry")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                dataViewModel.insert(
                    distanceText.text.toString().toDouble(),
                    date,
                    records.find { it.name == dropdown.text.toString() }!!.id
                )
                distanceText.setText("")
            }
            .setNegativeButton("Cancel", null)
            .show()


    }

    inner class Adapter : ListAdapter<Entry, Adapter.ViewHolder>(Entry.Comparator()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.recyclerview_item_entry, parent, false)
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val current = getItem(position)
            holder.name.text = current.distance.toString()
            holder.date.text = DateTimeFormatter.ofPattern("dd.MM.yyyy").format(current.date)
            holder.item.setOnLongClickListener {
                val popup = PopupMenu(context, holder.item)
                popup.menuInflater.inflate(R.menu.recyclerview_context_menu, popup.menu)
                popup.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.menu_delete -> {
                            Log.i("DEBUG", "delete item ${holder.name.text}")
                            dataViewModel.deleteEntry(current.id)
                            true
                        }
                        else -> true
                    }
                }
                popup.show()
                true
            }
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val item: LinearLayout = itemView.findViewById(R.id.recyclerview_item)
            val name: TextView = itemView.findViewById(R.id.distance)
            val date: TextView = itemView.findViewById(R.id.date)
        }
    }
}