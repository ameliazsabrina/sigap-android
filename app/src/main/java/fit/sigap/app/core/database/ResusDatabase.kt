package fit.sigap.app.core.database

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "resus_entries", indices = [Index("incident_id")])
data class ResusEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "incident_id") val incidentId: String,
    val seq: Long?,
    @ColumnInfo(name = "entry_type") val entryType: String,
    val payload: String,
    @ColumnInfo(name = "voids_entry_id") val voidsEntryId: String?,
    @ColumnInfo(name = "actor_user_id") val actorUserId: String,
    @ColumnInfo(name = "actor_name") val actorName: String?,
    @ColumnInfo(name = "tapped_at") val tappedAt: Long,
    @ColumnInfo(name = "clock_offset_ms") val clockOffsetMs: Long,
    @ColumnInfo(name = "tapped_at_suspect") val tappedAtSuspect: Boolean,
    @ColumnInfo(name = "received_at") val receivedAt: Long?,
    @ColumnInfo(name = "rejected_reason") val rejectedReason: String?,
)

@Entity(tableName = "resus_incidents")
data class ResusIncidentEntity(
    @PrimaryKey @ColumnInfo(name = "incident_id") val incidentId: String,
    @ColumnInfo(name = "last_seq") val lastSeq: Long?,
)

@Dao
interface ResusDao {
    @Query("SELECT * FROM resus_entries WHERE incident_id = :incidentId")
    fun entries(incidentId: String): Flow<List<ResusEntryEntity>>

    @Query("SELECT * FROM resus_incidents WHERE incident_id = :incidentId")
    fun incident(incidentId: String): Flow<ResusIncidentEntity?>

    @Query("SELECT * FROM resus_incidents WHERE incident_id = :incidentId")
    suspend fun incidentNow(incidentId: String): ResusIncidentEntity?

    @Query(
        "SELECT * FROM resus_entries WHERE seq IS NULL AND rejected_reason IS NULL AND actor_user_id = :userId " +
            "ORDER BY tapped_at",
    )
    fun pending(userId: String): Flow<List<ResusEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: ResusEntryEntity)

    @Upsert
    suspend fun upsertEntries(entries: List<ResusEntryEntity>)

    @Upsert
    suspend fun upsertIncident(incident: ResusIncidentEntity)

    @Query("UPDATE resus_entries SET rejected_reason = :reason WHERE id = :id AND seq IS NULL")
    suspend fun reject(id: String, reason: String)

    @Query("DELETE FROM resus_entries WHERE seq IS NOT NULL AND tapped_at < :before")
    suspend fun pruneSent(before: Long)

    @Transaction
    suspend fun applyServer(entries: List<ResusEntryEntity>, incident: ResusIncidentEntity) {
        upsertEntries(entries)
        upsertIncident(incident)
    }
}

@Database(entities = [ResusEntryEntity::class, ResusIncidentEntity::class], version = 1, exportSchema = true)
abstract class ResusDatabase : RoomDatabase() {
    abstract fun resus(): ResusDao

    companion object {
        fun create(context: Context): ResusDatabase =
            Room.databaseBuilder(context, ResusDatabase::class.java, "resus-log.db").build()
    }
}
