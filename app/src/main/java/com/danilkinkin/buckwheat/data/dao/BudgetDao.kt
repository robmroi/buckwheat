package com.danilkinkin.buckwheat.data.dao

import androidx.room.*
import com.danilkinkin.buckwheat.data.entities.Budget
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY uid ASC")
    fun getAll(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets ORDER BY uid ASC")
    suspend fun getAllOnce(): List<Budget>

    @Query("SELECT * FROM budgets WHERE uid = :uid")
    suspend fun getById(uid: Int): Budget?

    @Insert
    suspend fun insert(budget: Budget): Long

    @Update
    suspend fun update(budget: Budget)

    @Query("DELETE FROM budgets WHERE uid = :uid")
    suspend fun deleteById(uid: Int)
}
