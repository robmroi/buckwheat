package com.danilkinkin.buckwheat.di

import androidx.room.*
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.danilkinkin.buckwheat.data.dao.BudgetDao
import com.danilkinkin.buckwheat.data.dao.StorageDao
import com.danilkinkin.buckwheat.data.dao.TransactionDao
import com.danilkinkin.buckwheat.data.entities.Budget
import com.danilkinkin.buckwheat.data.entities.Storage
import com.danilkinkin.buckwheat.data.entities.Transaction


class AutoMigration1to2 : AutoMigrationSpec

@DeleteColumn.Entries(
    DeleteColumn(
        tableName = "Spent",
        columnName = "deleted"
    )
)
class AutoMigration2to3 : AutoMigrationSpec

// Preparing for remove storage table
class AutoMigration3to4 : AutoMigrationSpec

// Rename Spent to Transaction
val AutoMigration4to5: Migration = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create the new "transactions" table
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `transactions` " +
                    "(`type` TEXT NOT NULL, " +
                    "`value` TEXT NOT NULL, " +
                    "`date` INTEGER NOT NULL, " +
                    "`comment` TEXT NOT NULL DEFAULT '', " +
                    "`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)"
        )

        // Copy data from the old "Spent" table to the new "transactions" table
        database.execSQL(
            "INSERT INTO `transactions` (`type`, `value`, `date`, `comment`) " +
                    "SELECT 'SPENT', `value`, `date`, `comment` FROM `Spent`"
        )

        // Drop the old "Spent" table
        database.execSQL("DROP TABLE IF EXISTS `Spent`")
    }
}

const val CREATE_BUDGETS_TABLE_SQL = "CREATE TABLE IF NOT EXISTS `budgets` " +
        "(`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`name` TEXT NOT NULL, " +
        "`emoji` TEXT NOT NULL, " +
        "`period` TEXT NOT NULL, " +
        "`periodDays` INTEGER NOT NULL, " +
        "`amount` TEXT NOT NULL, " +
        "`carryOver` INTEGER NOT NULL)"

const val INSERT_DEFAULT_BUDGET_SQL = "INSERT OR IGNORE INTO `budgets` " +
        "(`uid`, `name`, `emoji`, `period`, `periodDays`, `amount`, `carryOver`) " +
        "VALUES (1, '', '', 'ONE_OFF', 7, '0', 0)"

// Multiple budgets: every existing transaction belongs to the default budget
val Migration5to6: Migration = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(CREATE_BUDGETS_TABLE_SQL)
        database.execSQL(INSERT_DEFAULT_BUDGET_SQL)
        database.execSQL(
            "ALTER TABLE `transactions` ADD COLUMN `budgetId` INTEGER NOT NULL DEFAULT 1"
        )
        database.execSQL(
            "ALTER TABLE `transactions` ADD COLUMN `periodKey` INTEGER NOT NULL DEFAULT 0"
        )
    }
}

@Database(
    entities = [Transaction::class, Storage::class, Budget::class],
    version = 6,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = AutoMigration1to2::class),
        AutoMigration(from = 2, to = 3, spec = AutoMigration2to3::class),
        AutoMigration(from = 3, to = 4, spec = AutoMigration3to4::class),
    ],
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class DatabaseModule : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun storageDao(): StorageDao

    abstract fun budgetDao(): BudgetDao

    companion object {
        val MANUAL_MIGRATIONS = arrayOf<Migration>(AutoMigration4to5, Migration5to6)
    }
}
