package com.ducksoup.kilometrage

import androidx.lifecycle.*
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class DataViewModel(private val dao: DAO) : ViewModel() {

    val selectedRecordId: MutableLiveData<Int> = MutableLiveData()

    val records: LiveData<List<Record>> = dao.getRecords().asLiveData()
    val entries: LiveData<List<Entry>> = Transformations.switchMap(selectedRecordId) {
        dao.getEntries(it).asLiveData()
    }

    val allEntries: LiveData<List<RecordWithEntries>> = dao.getAllEntries().asLiveData()


    fun insert(record: Record) = viewModelScope.launch { dao.insertRecord(record) }

    fun insert(distance: Double, date: LocalDateTime, recordId: Int) = viewModelScope.launch {
        dao.insertEntry(Entry(0, distance, date, recordId))
    }

    fun updateRecord(record: Record) = viewModelScope.launch { dao.updateRecord(record) }
    fun deleteRecord(record: Record) = viewModelScope.launch { dao.deleteRecord(record) }

    fun deleteEntry(id: Int) = viewModelScope.launch { dao.deleteEntry(Entry(id)) }

    fun exportEntries(records: List<Record>, callback: (List<Entry>) -> Unit) {
        viewModelScope.launch {
            val queryString = DB.entriesQueryString(records.size)
            val values = records.map { it.id }.toTypedArray()
            callback(dao.getEntryList(SimpleSQLiteQuery(queryString, values)))
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