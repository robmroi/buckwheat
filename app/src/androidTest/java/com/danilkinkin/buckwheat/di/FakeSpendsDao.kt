package com.danilkinkin.buckwheat.di

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.danilkinkin.buckwheat.data.dao.BudgetDao
import com.danilkinkin.buckwheat.data.dao.TransactionDao
import com.danilkinkin.buckwheat.data.entities.Budget
import com.danilkinkin.buckwheat.data.entities.Transaction
import com.danilkinkin.buckwheat.data.entities.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FakeTransactionDao: TransactionDao {
    private val spends = mutableListOf<Transaction>()

    override fun getAll(): LiveData<List<Transaction>> {
        return MutableLiveData(spends)
    }

    override fun getAll(type: TransactionType): LiveData<List<Transaction>> {
        return MutableLiveData(spends)
    }

    override fun getPeriod(budgetId: Int, periodKey: Long): Flow<List<Transaction>> {
        return flowOf(spends.filter { it.budgetId == budgetId && it.periodKey == periodKey })
    }

    override fun getPeriod(
        budgetId: Int,
        periodKey: Long,
        type: TransactionType,
    ): Flow<List<Transaction>> {
        return flowOf(getPeriodList(budgetId, periodKey, type))
    }

    override suspend fun getPeriodOnce(
        budgetId: Int,
        periodKey: Long,
        type: TransactionType,
    ): List<Transaction> {
        return getPeriodList(budgetId, periodKey, type)
    }

    private fun getPeriodList(budgetId: Int, periodKey: Long, type: TransactionType) =
        spends.filter { it.budgetId == budgetId && it.periodKey == periodKey && it.type == type }

    override fun getAllOfBudget(budgetId: Int): Flow<List<Transaction>> {
        return flowOf(spends.filter { it.budgetId == budgetId })
    }

    override suspend fun deletePeriod(budgetId: Int, periodKey: Long) {
        spends.removeIf { it.budgetId == budgetId && it.periodKey == periodKey }
    }

    override suspend fun deleteBudget(budgetId: Int) {
        spends.removeIf { it.budgetId == budgetId }
    }

    override fun getById(uid: Int): Transaction? {
        return null
    }

    override fun insert(vararg transaction: Transaction) {
        spends.addAll(transaction)
    }

    override fun update(vararg transaction: Transaction) {

    }

    override fun deleteById(uid: Int) {
        spends.removeIf { it.uid == uid }
    }

    override fun deleteAll() {

    }

}
class FakeBudgetDao : BudgetDao {
    private val budgets = MutableStateFlow(listOf(Budget(uid = 1)))

    override fun getAll(): Flow<List<Budget>> = budgets

    override suspend fun getAllOnce(): List<Budget> = budgets.value

    override suspend fun getById(uid: Int): Budget? = budgets.value.find { it.uid == uid }

    override suspend fun insert(budget: Budget): Long {
        val uid = if (budget.uid != 0) budget.uid else (budgets.value.maxOfOrNull { it.uid } ?: 0) + 1
        budgets.value = budgets.value + budget.copy(uid = uid)

        return uid.toLong()
    }

    override suspend fun update(budget: Budget) {
        budgets.value = budgets.value.map { if (it.uid == budget.uid) budget else it }
    }

    override suspend fun deleteById(uid: Int) {
        budgets.value = budgets.value.filter { it.uid != uid }
    }
}
