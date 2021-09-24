package com.ducksoup.kilometrage

import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime
import java.util.*

class DataViewModel(private val dao: DAO) : ViewModel() {

    val selectedRecordId: MutableLiveData<Int> = MutableLiveData()

    val records: LiveData<List<Record>> = dao.getRecords().asLiveData()
    val entries: LiveData<List<Entry>> = Transformations.switchMap(selectedRecordId) {
        dao.getEntries(it).asLiveData()
    }


    fun insert(record: Record) = viewModelScope.launch { dao.insertRecord(record) }
//    fun insert(distance: Double, date: Date) {
//        val recordId = selectedRecordId.value
//            ?: throw Exception("selected record id must not be null")
//        insert(distance, date, recordId)
//    }

    fun insert(distance: Double, date: LocalDateTime, recordId: Int) = viewModelScope.launch {
        dao.insertEntry(Entry(0, distance, date, recordId))
    }

    fun deleteAllRecords() = viewModelScope.launch { dao.deleteAllRecords() }
    fun updateRecord(record: Record) = viewModelScope.launch { dao.updateRecord(record) }
    fun deleteRecord(record: Record) = viewModelScope.launch { dao.deleteRecord(record) }
    fun deleteEntry(id: Int) = viewModelScope.launch { dao.deleteEntry(Entry(id)) }

    fun exportEntries(record: Record, callback: (List<Entry>) -> Unit) {
        viewModelScope.launch {
            callback(dao.getEntryList(record.id))
        }
    }
}

class DataViewModelFactory(private val dao: DAO) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DataViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DataViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}