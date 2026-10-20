package com.danilkinkin.buckwheat.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.danilkinkin.buckwheat.data.entities.Transaction
import com.danilkinkin.buckwheat.data.entities.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date ASC")
    fun getAll(): LiveData<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY date ASC")
    fun getAll(type: TransactionType): LiveData<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE budgetId = :budgetId AND periodKey = :periodKey ORDER BY date ASC")
    fun getPeriod(budgetId: Int, periodKey: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE budgetId = :budgetId AND periodKey = :periodKey AND type = :type ORDER BY date ASC")
    fun getPeriod(budgetId: Int, periodKey: Long, type: TransactionType): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE budgetId = :budgetId AND periodKey = :periodKey AND type = :type ORDER BY date ASC")
    suspend fun getPeriodOnce(budgetId: Int, periodKey: Long, type: TransactionType): List<Transaction>

    @Query("SELECT * FROM transactions WHERE budgetId = :budgetId ORDER BY date ASC")
    fun getAllOfBudget(budgetId: Int): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE uid = :uid")
    fun getById(uid: Int): Transaction?

    @Insert
    fun insert(vararg transaction: Transaction)

    @Update(entity = Transaction::class, onConflict = OnConflictStrategy.REPLACE)
    fun update(vararg transaction: Transaction)

    @Query("DELETE FROM transactions WHERE uid = :uid")
    fun deleteById(uid: Int)

    @Query("DELETE FROM transactions WHERE budgetId = :budgetId AND periodKey = :periodKey")
    suspend fun deletePeriod(budgetId: Int, periodKey: Long)

    @Query("DELETE FROM transactions WHERE budgetId = :budgetId")
    suspend fun deleteBudget(budgetId: Int)

    @Query("DELETE FROM transactions")
    fun deleteAll()
}
