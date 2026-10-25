package com.kleinapps.kaspitracker.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs the 6 -> 7 migration on real SQLite. Room on a phone validates the
 * migrated tables against what the entities expect and, failing that, the
 * destructive fallback wipes the history — so the migrated schema has to be
 * exactly what Room itself would create.
 */
class MigrationTest {

    private val open = mutableListOf<Connection>()

    @After
    fun tearDown() = open.forEach(Connection::close)

    private fun sqliteWith(schemaResource: String): Connection {
        val db = DriverManager.getConnection("jdbc:sqlite::memory:").also { open += it }
        val sql = javaClass.classLoader!!.getResource(schemaResource)!!.readText()
        sql.split(';').map(String::trim).filter(String::isNotEmpty).forEach { db.exec(it) }
        return db
    }

    private fun Connection.exec(sql: String) {
        createStatement().use { it.execute(sql) }
    }

    private fun Connection.count(sql: String): Int =
        createStatement().use { st -> st.executeQuery(sql).use { it.next(); it.getInt(1) } }

    private fun Connection.rows(sql: String): List<String> =
        createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                val columns = rs.metaData.columnCount
                buildList {
                    while (rs.next()) add((1..columns).joinToString("|") { rs.getString(it) ?: "null" })
                }
            }
        }

    /**
     * Columns (name, type, not-null, pk position) and indices (name, unique,
     * columns) of every app table. Defaults are left out: Room ignores them for
     * entity fields that declare none.
     */
    private fun Connection.shape(): List<String> {
        val tables = rows(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name NOT LIKE 'sqlite_%' ORDER BY name"
        )
        return tables.flatMap { table ->
            val columns = rows("SELECT name, type, \"notnull\", pk FROM pragma_table_info('$table')")
                .map { "$table.col:$it" }
            val indices = rows("SELECT name, \"unique\" FROM pragma_index_list('$table') WHERE origin = 'c'")
                .map { index ->
                    val name = index.substringBefore('|')
                    val cols = rows("SELECT name FROM pragma_index_info('$name')").joinToString(",")
                    "$table.idx:$index:$cols"
                }
            columns + indices
        }.sorted()
    }

    @Test
    fun `migration 6 to 7 keeps every row and gives each a unique uuid`() {
        val db = sqliteWith("schema_v6.sql")
        repeat(3) { i ->
            val amount = if (i == 2) "NULL" else "100.0"
            db.exec(
                "INSERT INTO transactions (timestamp, rawText, amount, type, currency, source, " +
                    "excludedFromStats) VALUES ($i, 'x', $amount, 'PURCHASE', '₸', 'NOTIFICATION', 0)"
            )
        }
        db.exec("INSERT INTO budgets (category, monthlyLimit) VALUES ('GROCERIES', 1.0)")

        MIGRATION_6_7_STATEMENTS.forEach { db.exec(it) }

        assertEquals(3, db.count("SELECT COUNT(*) FROM transactions"))
        assertEquals(
            3,
            db.count("SELECT COUNT(DISTINCT uuid) FROM transactions WHERE length(uuid) = 36")
        )
        assertEquals(
            0,
            db.count("SELECT COUNT(*) FROM transactions WHERE shared <> 0 OR syncedHash IS NOT NULL")
        )
        assertEquals(0, db.count("SELECT COUNT(*) FROM sqlite_master WHERE name = 'budgets'"))
    }

    @Test
    fun `migrated schema matches what Room creates for version 7`() {
        val migrated = sqliteWith("schema_v6.sql")
        MIGRATION_6_7_STATEMENTS.forEach { migrated.exec(it) }
        val fresh = sqliteWith("schema_v7.sql")

        assertEquals(fresh.shape(), migrated.shape())
    }
}
