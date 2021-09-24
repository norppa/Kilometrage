package com.ducksoup.kilometrage

import android.content.Context
import androidx.recyclerview.widget.DiffUtil
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.util.*
import kotlin.math.abs

@Entity(tableName = "records")
data class Record(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val name: String
) {
    class Comparator : DiffUtil.ItemCallback<Record>() {
        override fun areItemsTheSame(x: Record, y: Record): Boolean = x == y
        override fun areContentsTheSame(x: Record, y: Record): Boolean = x.name == y.name
    }
}

@Entity(
    tableName = "entries", foreignKeys = [ForeignKey(
        entity = Record::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("recordId"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val distance: Double,
    val date: LocalDateTime?,
    @ColumnInfo(index = true)
    val recordId: Int
) {
    constructor(id:Int): this(id, 0.0, LocalDateTime.now(), 0)

    class Comparator : DiffUtil.ItemCallback<Entry>() {
        override fun areItemsTheSame(x: Entry, y: Entry): Boolean = x.id == y.id
        override fun areContentsTheSame(x: Entry, y: Entry): Boolean {
            return abs(x.distance - y.distance) < 0.000001 && x.date == y.date
        }
    }
}

class Converters {
    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? {
        return value?.let { LocalDateTime.parse(value) }
    }

    @TypeConverter
    fun fromLocalDateTime(date: LocalDateTime?): String? {
        return date?.toString()
    }
}

@Dao
interface DAO {

    @Query("SELECT * FROM records")
    fun getRecords(): Flow<List<Record>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: Record)

    @Query("DELETE FROM records")
    suspend fun deleteAllRecords()

    @Delete
    suspend fun deleteRecord(record: Record)

    @Update
    suspend fun updateRecord(record: Record)

    suspend fun getEntries(id:Int, observable: Boolean = true) {}

    @Query("SELECT * FROM entries WHERE recordId = :id ORDER BY date DESC")
    fun getEntries(id: Int): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE recordId = :id ORDER BY date DESC")
    suspend fun getEntryList(id: Int): List<Entry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: Entry)

    @Delete
    suspend fun deleteEntry(entry: Entry)
}

@Database(entities = [Record::class, Entry::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class DB : RoomDatabase() {
    abstract fun dao(): DAO

    companion object {
        @Volatile
        private var INSTANCE: DB? = null

        fun getDao(context: Context): DAO {
            val instance = INSTANCE ?: synchronized(this) {
                val instance = Room
                    .databaseBuilder(context.applicationContext, DB::class.java, "db")
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
            return instance.dao()
        }

    }
}