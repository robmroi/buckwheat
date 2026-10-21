package com.danilkinkin.buckwheat.di

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Room validates the migrated database against the exported schema on startup,
// so the hand written migration must create exactly what Room expects
class MigrationSchemaTest {
    private val schema by lazy {
        File("schemas/com.danilkinkin.buckwheat.di.DatabaseModule/6.json").readText()
    }

    @Test
    fun budgetsTableMatchesExportedSchema() {
        val expected = CREATE_BUDGETS_TABLE_SQL
            .replace("CREATE TABLE IF NOT EXISTS `budgets`", "CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}`")

        assertTrue("Exported schema:\n$schema", schema.contains(expected))
    }

    @Test
    fun transactionColumnsHaveMigrationDefaults() {
        val compact = schema.replace(Regex("\\s"), "")

        assertTrue(compact.contains("\"columnName\":\"budgetId\",\"affinity\":\"INTEGER\",\"notNull\":true,\"defaultValue\":\"1\""))
        assertTrue(compact.contains("\"columnName\":\"periodKey\",\"affinity\":\"INTEGER\",\"notNull\":true,\"defaultValue\":\"0\""))
    }
}
