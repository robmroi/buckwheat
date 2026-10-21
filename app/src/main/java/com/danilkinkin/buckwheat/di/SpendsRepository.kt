package com.danilkinkin.buckwheat.di

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import com.danilkinkin.buckwheat.budgetDataStore
import com.danilkinkin.buckwheat.data.RestedBudgetDistributionMethod
import com.danilkinkin.buckwheat.data.entities.Transaction
import com.danilkinkin.buckwheat.util.DAY
import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.data.dao.BudgetDao
import com.danilkinkin.buckwheat.data.dao.TransactionDao
import com.danilkinkin.buckwheat.data.entities.Budget
import com.danilkinkin.buckwheat.data.entities.BudgetPeriod
import com.danilkinkin.buckwheat.data.entities.DEFAULT_BUDGET_ID
import com.danilkinkin.buckwheat.data.entities.TransactionType
import com.danilkinkin.buckwheat.data.entities.isRecurring
import com.danilkinkin.buckwheat.errorForReport
import com.danilkinkin.buckwheat.util.countDays
import com.danilkinkin.buckwheat.util.isSameDay
import com.danilkinkin.buckwheat.util.periodBounds
import com.danilkinkin.buckwheat.util.roundToDay
import com.danilkinkin.buckwheat.util.toDate
import com.danilkinkin.buckwheat.util.toLocalDate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.lang.Long.min
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Date
import javax.inject.Inject

val currencyStoreKey = stringPreferencesKey("currency")
val restedBudgetDistributionMethodStoreKey = stringPreferencesKey("restedBudgetDistributionMethod")
val hideOverspendingWarnStoreKey = booleanPreferencesKey("hideOverspendingWarn")
val activeBudgetIdStoreKey = intPreferencesKey("activeBudgetId")

// Keys of the default budget. Kept unchanged so existing installs keep their data
val budgetStoreKey = stringPreferencesKey("budget")
val spentStoreKey = stringPreferencesKey("spent")
val dailyBudgetStoreKey = stringPreferencesKey("dailyBudget")
val spentFromDailyBudgetStoreKey = stringPreferencesKey("spentFromDailyBudget")
val lastChangeDailyBudgetDateStoreKey = longPreferencesKey("lastChangeDailyBudgetDate")
val startPeriodDateStoreKey = longPreferencesKey("startPeriodDate")
val finishPeriodDateStoreKey = longPreferencesKey("finishPeriodDate")
val finishPeriodActualDateStoreKey = longPreferencesKey("finishPeriodActualDate")

// Every budget keeps its own state in the budget data store. The default budget
// uses the original key names, other budgets get their id as a suffix
private fun scoped(name: String, budgetId: Int) =
    if (budgetId == DEFAULT_BUDGET_ID) name else "${name}_$budgetId"

fun budgetKey(budgetId: Int) = stringPreferencesKey(scoped("budget", budgetId))
fun spentKey(budgetId: Int) = stringPreferencesKey(scoped("spent", budgetId))
fun dailyBudgetKey(budgetId: Int) = stringPreferencesKey(scoped("dailyBudget", budgetId))
fun spentFromDailyBudgetKey(budgetId: Int) =
    stringPreferencesKey(scoped("spentFromDailyBudget", budgetId))
fun lastChangeDailyBudgetDateKey(budgetId: Int) =
    longPreferencesKey(scoped("lastChangeDailyBudgetDate", budgetId))
fun startPeriodDateKey(budgetId: Int) = longPreferencesKey(scoped("startPeriodDate", budgetId))
fun finishPeriodDateKey(budgetId: Int) = longPreferencesKey(scoped("finishPeriodDate", budgetId))
fun finishPeriodActualDateKey(budgetId: Int) =
    longPreferencesKey(scoped("finishPeriodActualDate", budgetId))
fun periodKeyKey(budgetId: Int) = longPreferencesKey(scoped("periodKey", budgetId))

fun Preferences.activeBudgetId() = this[activeBudgetIdStoreKey] ?: DEFAULT_BUDGET_ID

data class PeriodSummary(
    val budgetId: Int,
    val periodKey: Long,
    val startDate: Date,
    val finishDate: Date,
    val budget: BigDecimal,
    val spent: BigDecimal,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SpendsRepository @Inject constructor(
    @ApplicationContext val context: Context,
    private val transactionDao: TransactionDao,
    private val getCurrentDateUseCase: GetCurrentDateUseCase,
    private val budgetDao: BudgetDao,
) {
    private val activeScope: Flow<Pair<Int, Long>> = context.budgetDataStore.data
        .map {
            val budgetId = it.activeBudgetId()

            budgetId to (it[periodKeyKey(budgetId)] ?: 0L)
        }
        .distinctUntilChanged()

    private suspend fun currentScope() = activeScope.first()

    // Transactions of the current period of the active budget
    fun getAllTransactions(): LiveData<List<Transaction>> = activeScope
        .flatMapLatest { (budgetId, periodKey) -> transactionDao.getPeriod(budgetId, periodKey) }
        .asLiveData()

    fun getAllSpends(): LiveData<List<Transaction>> = activeScope
        .flatMapLatest { (budgetId, periodKey) ->
            transactionDao.getPeriod(budgetId, periodKey, TransactionType.SPENT)
        }
        .asLiveData()

    // Transactions of every period of the active budget
    fun getBudgetHistory(): Flow<List<Transaction>> = activeScope
        .flatMapLatest { (budgetId, _) -> transactionDao.getAllOfBudget(budgetId) }

    fun getAllTags(): LiveData<List<String>> = transactionDao.getAll().map { transactions ->
        transactions
            .asSequence()
            .filter { transaction -> transaction.comment.isNotEmpty() }
            .groupBy { it.comment }
            .map { it.key to it.value.size }
            .sortedBy { -it.second }
            .map { it.first }
            .distinct()
            .toList()
    }

    // Budgets

    fun getBudgets(): Flow<List<Budget>> = budgetDao.getAll()

    fun getActiveBudgetId() = context.budgetDataStore.data.map { it.activeBudgetId() }

    fun getActiveBudget(): Flow<Budget> = combine(getActiveBudgetId(), getBudgets()) { id, budgets ->
        budgets.find { it.uid == id } ?: Budget(uid = id)
    }

    suspend fun activeBudget(): Budget {
        val id = getActiveBudgetId().first()

        return budgetDao.getById(id) ?: Budget(uid = id)
    }

    suspend fun switchBudget(budgetId: Int) {
        context.budgetDataStore.edit {
            it[activeBudgetIdStoreKey] = budgetId
        }
    }

    suspend fun createBudget(name: String, emoji: String): Int {
        val id = budgetDao.insert(Budget(name = name, emoji = emoji)).toInt()

        switchBudget(id)

        return id
    }

    suspend fun updateBudgetInfo(budget: Budget) {
        if (budgetDao.getById(budget.uid) == null) {
            budgetDao.insert(budget)
        } else {
            budgetDao.update(budget)
        }
    }

    suspend fun deleteBudget(budgetId: Int) {
        val budgets = budgetDao.getAllOnce()

        if (budgets.size <= 1) return

        transactionDao.deleteBudget(budgetId)
        budgetDao.deleteById(budgetId)

        context.budgetDataStore.edit {
            it.remove(budgetKey(budgetId))
            it.remove(spentKey(budgetId))
            it.remove(dailyBudgetKey(budgetId))
            it.remove(spentFromDailyBudgetKey(budgetId))
            it.remove(lastChangeDailyBudgetDateKey(budgetId))
            it.remove(startPeriodDateKey(budgetId))
            it.remove(finishPeriodDateKey(budgetId))
            it.remove(finishPeriodActualDateKey(budgetId))
            it.remove(periodKeyKey(budgetId))

            if (it.activeBudgetId() == budgetId) {
                it[activeBudgetIdStoreKey] = budgets.first { budget -> budget.uid != budgetId }.uid
            }
        }
    }

    suspend fun isBudgetConfigured(budgetId: Int): Boolean {
        return context.budgetDataStore.data.first()[lastChangeDailyBudgetDateKey(budgetId)] != null
    }

    suspend fun hasAnyConfiguredBudget(): Boolean {
        return budgetDao.getAllOnce().any { isBudgetConfigured(it.uid) }
    }

    // State of the active budget

    fun getBudget() = context.budgetDataStore.data.map {
        (it[budgetKey(it.activeBudgetId())]?.toBigDecimal() ?: BigDecimal.ZERO).setScale(2)
    }

    fun getSpent() = context.budgetDataStore.data.map {
        (it[spentKey(it.activeBudgetId())]?.toBigDecimal() ?: BigDecimal.ZERO).setScale(2)
    }

    fun getDailyBudget() = context.budgetDataStore.data.map {
        (it[dailyBudgetKey(it.activeBudgetId())]?.toBigDecimal() ?: BigDecimal.ZERO).setScale(2)
    }

    fun getSpentFromDailyBudget() = context.budgetDataStore.data.map {
        (it[spentFromDailyBudgetKey(it.activeBudgetId())]?.toBigDecimal()
            ?: BigDecimal.ZERO).setScale(2)
    }

    fun getStartPeriodDate() = context.budgetDataStore.data.map {
        it[startPeriodDateKey(it.activeBudgetId())]?.let { value -> Date(value) }
            ?: getCurrentDateUseCase()
    }

    fun getFinishPeriodDate() = context.budgetDataStore.data.map {
        it[finishPeriodDateKey(it.activeBudgetId())]?.let { value -> Date(value) }
    }

    fun getFinishPeriodActualDate() = context.budgetDataStore.data.map {
        it[finishPeriodActualDateKey(it.activeBudgetId())]?.let { value -> Date(value) }
    }

    fun getLastChangeDailyBudgetDate() = context.budgetDataStore.data.map {
        it[lastChangeDailyBudgetDateKey(it.activeBudgetId())]?.let { value -> Date(value) }
    }

    // What is left in the current period of every budget, for the budget switcher
    fun getRestBudgets(): Flow<Map<Int, BigDecimal>> =
        combine(context.budgetDataStore.data, getBudgets()) { prefs, budgets ->
            budgets
                .filter { prefs[lastChangeDailyBudgetDateKey(it.uid)] != null }
                .associate { budget ->
                    fun value(key: Preferences.Key<String>) =
                        prefs[key]?.toBigDecimal() ?: BigDecimal.ZERO

                    budget.uid to (value(budgetKey(budget.uid))
                            - value(spentKey(budget.uid))
                            - value(spentFromDailyBudgetKey(budget.uid)))
                }
        }

    fun getCurrency() = context.budgetDataStore.data.map {
        it[currencyStoreKey]?.let { value ->
            ExtendCurrency.getInstance(value)
        } ?: ExtendCurrency(value = null, type = ExtendCurrency.Type.NONE)
    }

    fun getRestedBudgetDistributionMethod() = context.budgetDataStore.data.map { it ->
        it[restedBudgetDistributionMethodStoreKey]?.let {
            RestedBudgetDistributionMethod.valueOf(it)
        } ?: RestedBudgetDistributionMethod.ASK
    }

    fun getHideOverspendingWarn() = context.budgetDataStore.data.map {
        it[hideOverspendingWarnStoreKey] ?: false
    }


    suspend fun changeDisplayCurrency(currency: ExtendCurrency) {
        context.budgetDataStore.edit {
            it[currencyStoreKey] = currency.value ?: ""
        }
    }

    suspend fun changeRestedBudgetDistributionMethod(method: RestedBudgetDistributionMethod) {
        context.budgetDataStore.edit {
            it[restedBudgetDistributionMethodStoreKey] = method.toString()
        }
    }

    suspend fun hideOverspendingWarn(hide: Boolean) {
        context.budgetDataStore.edit {
            it[hideOverspendingWarnStoreKey] = hide
        }
    }

    // Starts a new period of the active budget. Transactions of previous periods are kept
    private suspend fun startPeriod(newBudget: BigDecimal, startDate: Date, newFinishDate: Date) {
        val (budgetId, previousPeriodKey) = currentScope()
        var periodKey = getCurrentDateUseCase().time

        // Restarting on the same millisecond would mix periods up
        if (periodKey == previousPeriodKey) periodKey += 1

        context.budgetDataStore.edit {
            it[budgetKey(budgetId)] = newBudget.toString()
            it[spentKey(budgetId)] = BigDecimal.ZERO.toString()
            it[dailyBudgetKey(budgetId)] = BigDecimal.ZERO.toString()
            it[spentFromDailyBudgetKey(budgetId)] = BigDecimal.ZERO.toString()
            it[lastChangeDailyBudgetDateKey(budgetId)] = roundToDay(getCurrentDateUseCase()).time
            it[startPeriodDateKey(budgetId)] = roundToDay(startDate).time
            it[finishPeriodDateKey(budgetId)] = Date(roundToDay(newFinishDate).time + DAY - 1000).time
            it[periodKeyKey(budgetId)] = periodKey
            it.remove(finishPeriodActualDateKey(budgetId))

            Log.d(
                "SpendsRepository",
                "Start period ["
                        + "budget id: $budgetId "
                        + "budget: ${it[budgetKey(budgetId)]} "
                        + "start date: ${Date(it[startPeriodDateKey(budgetId)]!!)} "
                        + "finish date: ${Date(it[finishPeriodDateKey(budgetId)]!!)}"
                        + "]"
            )
        }

        transactionDao.insert(
            Transaction(
                TransactionType.INCOME,
                newBudget,
                getCurrentDateUseCase(),
                budgetId = budgetId,
                periodKey = periodKey,
            )
        )

        setDailyBudget(whatBudgetForDay())

        hideOverspendingWarn(false)
    }

    suspend fun setBudget(newBudget: BigDecimal, newFinishDate: Date) {
        updateBudgetInfo(
            activeBudget().copy(period = BudgetPeriod.ONE_OFF, amount = newBudget)
        )

        startPeriod(newBudget, getCurrentDateUseCase(), newFinishDate)
    }

    suspend fun setRecurringBudget(
        amount: BigDecimal,
        period: BudgetPeriod,
        periodDays: Int,
        carryOver: Boolean,
    ) {
        updateBudgetInfo(
            activeBudget().copy(
                period = period,
                periodDays = periodDays,
                amount = amount,
                carryOver = carryOver,
            )
        )

        val (start, finish) = periodBounds(
            period,
            periodDays,
            getCurrentDateUseCase().toLocalDate(),
        )

        startPeriod(amount, start.toDate(), finish.toDate())
    }

    // Changes the amount of a running recurring period without restarting it
    suspend fun changeRecurringBudget(amount: BigDecimal, carryOver: Boolean) {
        val budget = activeBudget()
        val finishPeriodDate = getFinishPeriodDate().first() ?: return
        val currentPeriodBudget = getBudget().first()

        updateBudgetInfo(budget.copy(amount = amount, carryOver = carryOver))

        // Keep the carried over part of the current period
        changeBudget(currentPeriodBudget - budget.amount + amount, finishPeriodDate)
    }

    // Rolls a recurring budget over into its next period once the current one is over.
    // Returns the summary of the finished period, or null if nothing changed
    suspend fun startNextPeriodIfNeeded(): PeriodSummary? {
        val budget = activeBudget()

        if (!budget.isRecurring) return null
        if (getLastChangeDailyBudgetDate().first() == null) return null

        val finishPeriodDate = getFinishPeriodDate().first() ?: return null
        val today = getCurrentDateUseCase().toLocalDate()

        if (!today.isAfter(finishPeriodDate.toLocalDate())) return null

        val (budgetId, periodKey) = currentScope()
        val periodBudget = getBudget().first()
        val summary = PeriodSummary(
            budgetId = budgetId,
            periodKey = periodKey,
            startDate = getStartPeriodDate().first(),
            finishDate = finishPeriodDate,
            budget = periodBudget,
            spent = getSpent().first() + getSpentFromDailyBudget().first(),
        )

        var leftover = periodBudget - summary.spent
        var bounds = periodBounds(
            budget.period,
            budget.periodDays,
            finishPeriodDate.toLocalDate().plusDays(1),
        )

        // The app was not opened for a while, skip the periods that are already over
        while (today.isAfter(bounds.second)) {
            leftover += budget.amount
            bounds = periodBounds(budget.period, budget.periodDays, bounds.second.plusDays(1))
        }

        val newBudget = if (budget.carryOver) {
            (budget.amount + leftover).coerceAtLeast(BigDecimal.ZERO)
        } else {
            budget.amount
        }

        startPeriod(newBudget, bounds.first.toDate(), bounds.second.toDate())

        return summary
    }

    // Every finished period of the active budget, newest first
    fun getPastPeriods(): Flow<List<PeriodSummary>> =
        combine(getBudgetHistory(), activeScope) { transactions, (budgetId, currentPeriodKey) ->
            transactions
                .filter { it.periodKey != currentPeriodKey }
                .groupBy { it.periodKey }
                .mapNotNull { (periodKey, periodTransactions) ->
                    val income = periodTransactions
                        .find { it.type == TransactionType.INCOME } ?: return@mapNotNull null
                    val spends = periodTransactions.filter { it.type == TransactionType.SPENT }
                    val lastDate = (spends.map { it.date } + periodTransactions.last().date)
                        .maxBy { it.time }

                    PeriodSummary(
                        budgetId = budgetId,
                        periodKey = periodKey,
                        startDate = income.date,
                        finishDate = lastDate,
                        budget = income.value,
                        spent = spends.fold(BigDecimal.ZERO) { acc, spent -> acc + spent.value },
                    )
                }
                .sortedByDescending { it.startDate.time }
        }

    suspend fun changeBudget(newBudget: BigDecimal, newFinishDate: Date) {
        val (budgetId, periodKey) = currentScope()

        context.budgetDataStore.edit {
            it[budgetKey(budgetId)] = newBudget.toString()
            it[lastChangeDailyBudgetDateKey(budgetId)] = roundToDay(getCurrentDateUseCase()).time
            it[finishPeriodDateKey(budgetId)] = Date(roundToDay(newFinishDate).time + DAY - 1000).time
            it.remove(finishPeriodActualDateKey(budgetId))

            Log.d(
                "SpendsRepository",
                "Change budget ["
                        + "budget: ${it[budgetKey(budgetId)]} "
                        + "start date: ${it[startPeriodDateKey(budgetId)]?.let { date -> Date(date) }} "
                        + "finish date: ${Date(it[finishPeriodDateKey(budgetId)]!!)}"
                        + "]"
            )
        }

        transactionDao.getPeriodOnce(budgetId, periodKey, TransactionType.INCOME)
            .firstOrNull()
            ?.let { transactionDao.update(it.copy(value = newBudget).also { copy -> copy.uid = it.uid }) }

        updateDailyBudget(whatBudgetForDay())
    }

    suspend fun finishBudget(finishDate: Date) {
        context.budgetDataStore.edit {
            val budgetId = it.activeBudgetId()

            it[finishPeriodActualDateKey(budgetId)] = finishDate.time

            Log.d(
                "SpendsRepository",
                "Finish budget ["
                        + "budget: ${it[budgetKey(budgetId)]} "
                        + "actual finish date: ${Date(it[finishPeriodActualDateKey(budgetId)]!!)}"
                        + "]"
            )
        }
    }

    suspend fun updateDailyBudget(newDailyBudget: BigDecimal) {
        val (budgetId, periodKey) = currentScope()

        context.budgetDataStore.edit {
            it[dailyBudgetKey(budgetId)] = newDailyBudget.toString()
            it[lastChangeDailyBudgetDateKey(budgetId)] = roundToDay(getCurrentDateUseCase()).time

            Log.d(
                "SpendsRepository",
                "Update daily budget ["
                        + "daily budget: ${it[dailyBudgetKey(budgetId)]} "
                        + "spent: ${it[spentKey(budgetId)]}"
                        + "]"
            )
        }

        transactionDao.getPeriodOnce(budgetId, periodKey, TransactionType.SET_DAILY_BUDGET)
            .lastOrNull()
            ?.let {
                transactionDao.update(it.copy(value = newDailyBudget).also { copy -> copy.uid = it.uid })
            }
    }

    suspend fun setDailyBudget(newDailyBudget: BigDecimal) {
        val (budgetId, periodKey) = currentScope()

        context.budgetDataStore.edit {
            val spent: BigDecimal = it[spentKey(budgetId)]?.toBigDecimal() ?: BigDecimal.ZERO
            val spentFromDailyBudget: BigDecimal =
                it[spentFromDailyBudgetKey(budgetId)]?.toBigDecimal() ?: BigDecimal.ZERO

            it[dailyBudgetKey(budgetId)] = newDailyBudget.toString()
            it[spentKey(budgetId)] = (spent + spentFromDailyBudget).toString()
            it[lastChangeDailyBudgetDateKey(budgetId)] = roundToDay(getCurrentDateUseCase()).time
            it[spentFromDailyBudgetKey(budgetId)] = BigDecimal.ZERO.toString()

            Log.d(
                "SpendsRepository",
                "Set daily budget ["
                        + "daily budget: ${it[dailyBudgetKey(budgetId)]} "
                        + "spent: ${it[spentKey(budgetId)]}"
                        + "]"
            )
        }

        transactionDao.insert(
            Transaction(
                TransactionType.SET_DAILY_BUDGET,
                newDailyBudget,
                getCurrentDateUseCase(),
                budgetId = budgetId,
                periodKey = periodKey,
            )
        )
    }

    suspend fun whatBudgetForDay(
        excludeCurrentDay: Boolean = false,
        applyTodaySpends: Boolean = false,
        notCommittedSpent: BigDecimal = BigDecimal.ZERO
    ): BigDecimal {
        val budget = getBudget().first()
        val spent = getSpent().first()
        val dailyBudget = getDailyBudget().first()
        val spentFromDailyBudget = getSpentFromDailyBudget().first()
        val finishPeriodDate =
            getFinishPeriodDate().first() ?: throw Exception("Finish period date is null")


        val restDays =
            countDays(finishPeriodDate, getCurrentDateUseCase()) - if (excludeCurrentDay) 1 else 0
        var restBudget = budget - spent

        restBudget -= notCommittedSpent

        if (applyTodaySpends) {
            restBudget -= spentFromDailyBudget
        } else if (excludeCurrentDay) {
            restBudget -= dailyBudget
        }

        val whatBudgetForDay = restBudget
            .divide(
                restDays.toBigDecimal().coerceAtLeast(BigDecimal(1)),
                2,
                RoundingMode.HALF_EVEN
            )

        Log.d(
            "SpendsRepository",
            "Check what budget for day ["
                    + "date: ${getCurrentDateUseCase()} "
                    + "what budget for day: $whatBudgetForDay "
                    + "excludeCurrentDay: $excludeCurrentDay "
                    + "applyTodaySpends: $applyTodaySpends "
                    + "notCommittedSpent: $notCommittedSpent "
                    + "budget: $budget "
                    + "spent: $spent "
                    + "daily budget: $dailyBudget "
                    + "spent from daily budget: $spentFromDailyBudget "
                    + "rest budget: $restBudget "
                    + "rest days: $restDays"
                    + "]"
        )

        return whatBudgetForDay
    }

    suspend fun howMuchBudgetRest(): BigDecimal {
        val budget = getBudget().first()
        val spent = getSpent().first()
        val spentFromDailyBudget = getSpentFromDailyBudget().first()

        return budget - spent - spentFromDailyBudget
    }

    suspend fun howMuchNotSpent(
        excludeSkippedPart: Boolean = false,
    ): BigDecimal {
        val budget = getBudget().first()
        val spent = getSpent().first()
        val dailyBudget = getDailyBudget().first()
        val spentFromDailyBudget = getSpentFromDailyBudget().first()
        val finishPeriodDate =
            getFinishPeriodDate().first() ?: throw Exception("Finish period date is null")
        val lastChangeDailyBudgetDate =
            getLastChangeDailyBudgetDate().first() ?: getStartPeriodDate().first()


        val restDays = countDays(finishPeriodDate, getCurrentDateUseCase()).coerceAtLeast(0)
        val skippedDays = countDays(
            Date(min(getCurrentDateUseCase().time, finishPeriodDate.time)),
            lastChangeDailyBudgetDate
        ) - 1

        var restBudget = budget - spent

        val howMuchNotSpent = if (restDays == 0) {
            restBudget - spentFromDailyBudget
        } else if (excludeSkippedPart) {
            restBudget
                .minus(dailyBudget * skippedDays.toBigDecimal())
                .divide(
                    (restDays).coerceAtLeast(1).toBigDecimal(),
                    2,
                    RoundingMode.HALF_EVEN,
                )
                .multiply((skippedDays).coerceAtLeast(0).toBigDecimal())
                .plus(dailyBudget - spentFromDailyBudget)
        } else {
            restBudget
                .minus(dailyBudget)
                .divide(
                    (restDays + skippedDays - 1).coerceAtLeast(1).toBigDecimal(),
                    2,
                    RoundingMode.HALF_EVEN,
                )
                .multiply((skippedDays).coerceAtLeast(0).toBigDecimal())
                .plus(dailyBudget - spentFromDailyBudget)
        }

        Log.d(
            "SpendsRepository",
            "How much not spent check ["
                    + "how much not spent: $howMuchNotSpent "
                    + "rest budget: $restBudget "
                    + "restDays: $restDays "
                    + "skippedDays: $skippedDays "
                    + "lastChangeDailyBudgetDate: $lastChangeDailyBudgetDate "
                    + "getCurrentDateUseCase: ${getCurrentDateUseCase()} "
                    + "dailyBudget: $dailyBudget "
                    + "spentFromDailyBudget: $spentFromDailyBudget "
                    + "]"
        )

        return howMuchNotSpent
    }

    suspend fun nextDayBudget(
        excludeSkippedPart: Boolean = false,
    ): BigDecimal {
        val budget = getBudget().first()
        val spent = getSpent().first()
        val dailyBudget = getDailyBudget().first()
        val spentFromDailyBudget = getSpentFromDailyBudget().first()
        val finishPeriodDate =
            getFinishPeriodDate().first() ?: throw Exception("Finish period date is null")
        val lastChangeDailyBudgetDate =
            getLastChangeDailyBudgetDate().first() ?: getStartPeriodDate().first()


        val restDays = countDays(finishPeriodDate, getCurrentDateUseCase()).coerceAtLeast(0)
        val skippedDays = countDays(
            Date(min(getCurrentDateUseCase().time, finishPeriodDate.time)),
            lastChangeDailyBudgetDate
        ) - 1

        var restBudget = budget - spent

        val nextDailyBudget = if (restDays == 0) {
            restBudget - spentFromDailyBudget
        } else if (excludeSkippedPart) {
            restBudget
                .minus(dailyBudget * skippedDays.toBigDecimal())
                .divide(
                    (restDays).coerceAtLeast(1).toBigDecimal(),
                    2,
                    RoundingMode.HALF_EVEN,
                )
        } else {
            restBudget
                .minus(dailyBudget)
                .divide(
                    (restDays + skippedDays - 1).coerceAtLeast(1).toBigDecimal(),
                    2,
                    RoundingMode.HALF_EVEN,
                )
        }

        Log.d(
            "SpendsRepository",
            "Next day budget ["
                    + "next daily budget: $nextDailyBudget "
                    + "rest budget: $restBudget "
                    + "restDays: $restDays "
                    + "skippedDays: $skippedDays "
                    + "lastChangeDailyBudgetDate: $lastChangeDailyBudgetDate "
                    + "getCurrentDateUseCase: ${getCurrentDateUseCase()} "
                    + "dailyBudget: $dailyBudget "
                    + "spentFromDailyBudget: $spentFromDailyBudget "
                    + "]"
        )

        return nextDailyBudget
    }


    suspend fun addSpent(newTransaction: Transaction) {
        val (budgetId, periodKey) = currentScope()

        this.transactionDao.insert(
            newTransaction.copy(budgetId = budgetId, periodKey = periodKey)
                .also { it.uid = newTransaction.uid }
        )

        context.budgetDataStore.edit {
            try {
                if (isSameDay(newTransaction.date, getCurrentDateUseCase())) {
                    val spentFromDailyBudget = it[spentFromDailyBudgetKey(budgetId)]?.toBigDecimal()!!
                    it[spentFromDailyBudgetKey(budgetId)] =
                        (spentFromDailyBudget + newTransaction.value).toString()
                } else {
                    val finishPeriodDate =
                        it[finishPeriodDateKey(budgetId)]?.let { value -> Date(value) }!!
                    val dailyBudget = it[dailyBudgetKey(budgetId)]?.toBigDecimal()!!
                    val spent = it[spentKey(budgetId)]?.toBigDecimal()!!

                    val spreadDeltaSpentPerRestDays = newTransaction.value
                        .divide(
                            countDays(finishPeriodDate, getCurrentDateUseCase()).toBigDecimal(),
                            2,
                            RoundingMode.HALF_EVEN,
                        )

                    Log.d(
                        "SpendsRepository",
                        "Add spent for previous day ["
                                + "spent: $spent "
                                + "dailyBudget: $dailyBudget "
                                + "spreadDeltaSpentPerRestDays: $spreadDeltaSpentPerRestDays "
                                + "spentDate: ${newTransaction.date} "
                                + "getCurrentDateUseCase: ${getCurrentDateUseCase()} "
                                + "countDays: ${
                            countDays(
                                finishPeriodDate,
                                getCurrentDateUseCase()
                            )
                        } "
                                + "]"
                    )

                    it[dailyBudgetKey(budgetId)] = (dailyBudget - spreadDeltaSpentPerRestDays).toString()
                    it[spentKey(budgetId)] = (spent + newTransaction.value).toString()
                }
            } catch (e: Exception) {
                context.errorForReport = e.stackTraceToString()
            }
        }
    }

    suspend fun removeSpent(transactionForRemove: Transaction) {
        val (budgetId, _) = currentScope()

        this.transactionDao.deleteById(transactionForRemove.uid)

        context.budgetDataStore.edit {
            if (isSameDay(transactionForRemove.date, getCurrentDateUseCase())) {
                val spentFromDailyBudget = it[spentFromDailyBudgetKey(budgetId)]?.toBigDecimal()!!

                it[spentFromDailyBudgetKey(budgetId)] =
                    (spentFromDailyBudget - transactionForRemove.value).toString()
            } else {
                val finishPeriodDate = it[finishPeriodDateKey(budgetId)]?.let { value -> Date(value) }!!
                val dailyBudget = it[dailyBudgetKey(budgetId)]?.toBigDecimal()!!
                val spent = it[spentKey(budgetId)]?.toBigDecimal()!!

                val restDays = countDays(finishPeriodDate, getCurrentDateUseCase())
                val spreadDeltaSpentPerRestDays = transactionForRemove.value
                    .divide(
                        restDays.toBigDecimal(),
                        2,
                        RoundingMode.HALF_EVEN,
                    )

                Log.d(
                    "SpendsRepository",
                    "Remove spent from previous day { "
                            + transactionForRemove
                            + " } ["
                            + "spent: $spent "
                            + "dailyBudget: $dailyBudget "
                            + "spreadDeltaSpentPerRestDays: $spreadDeltaSpentPerRestDays "
                            + "spentDate: ${transactionForRemove.date} "
                            + "getCurrentDateUseCase: ${getCurrentDateUseCase()} "
                            + "countDays: $restDays "
                            + "]"
                )

                it[dailyBudgetKey(budgetId)] = (dailyBudget + spreadDeltaSpentPerRestDays).toString()
                it[spentKey(budgetId)] = (spent - transactionForRemove.value).toString()
            }
        }
    }
}
