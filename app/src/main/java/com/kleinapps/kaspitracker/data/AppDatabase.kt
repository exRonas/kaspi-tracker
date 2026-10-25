package com.kleinapps.kaspitracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds the `source` column (NOTIFICATION / MANUAL / RECEIPT) without losing existing rows. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN source TEXT NOT NULL DEFAULT 'NOTIFICATION'")
    }
}

/** Adds category/account columns and the budgets table. */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN category TEXT")
        db.execSQL("ALTER TABLE transactions ADD COLUMN account TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS budgets (" +
                "category TEXT NOT NULL PRIMARY KEY, " +
                "monthlyLimit REAL NOT NULL)"
        )
    }
}

/**
 * Adds learned category rules (merchant -> category the user picked) and the
 * notification de-duplication fingerprint.
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN dedupKey TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS category_rules (" +
                "merchantKey TEXT NOT NULL PRIMARY KEY, " +
                "category TEXT NOT NULL, " +
                "updatedAt INTEGER NOT NULL)"
        )
    }
}

/** Adds the card balance read from the "Доступно: … ₸" line of Kaspi pushes. */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN balance REAL")
    }
}

/**
 * Adds the "leave this out of the totals" flag, used for transfers between the
 * user's own cards.
 */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE transactions ADD COLUMN excludedFromStats " +
                "INTEGER NOT NULL DEFAULT 0"
        )
    }
}

/**
 * Drops budgets (the feature is gone) and prepares for syncing with a partner:
 * a stable uuid and shared flag on every operation, plus tables for the
 * partner's rows, per-merchant shared hints and deletions waiting to be sent.
 */
internal val MIGRATION_6_7_STATEMENTS: List<String> = listOf(
    "DROP TABLE IF EXISTS budgets",
    "ALTER TABLE transactions ADD COLUMN uuid TEXT NOT NULL DEFAULT ''",
    // A version-4 UUID per existing row, so history already on the phone gets
    // the same kind of identity new rows are born with.
    "UPDATE transactions SET uuid = lower(" +
        "hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || " +
        "substr(hex(randomblob(2)), 2) || '-' || " +
        "substr('89ab', 1 + (abs(random()) % 4), 1) || substr(hex(randomblob(2)), 2) || '-' || " +
        "hex(randomblob(6)))",
    "ALTER TABLE transactions ADD COLUMN shared INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE transactions ADD COLUMN syncedHash INTEGER",
    // The statements below are copied verbatim from what Room generates for
    // version 7, so the migrated schema validates exactly.
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_uuid` ON `transactions` (`uuid`)",
    "CREATE TABLE IF NOT EXISTS `partner_transactions` (`uuid` TEXT NOT NULL, " +
        "`timestamp` INTEGER NOT NULL, `amount` REAL, `type` TEXT NOT NULL, `merchant` TEXT, " +
        "`category` TEXT, `account` TEXT, `balance` REAL, `source` TEXT NOT NULL, " +
        "`shared` INTEGER NOT NULL, `excludedFromStats` INTEGER NOT NULL, PRIMARY KEY(`uuid`))",
    "CREATE TABLE IF NOT EXISTS `shared_rules` (`merchantKey` TEXT NOT NULL, " +
        "`shared` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`merchantKey`))",
    "CREATE TABLE IF NOT EXISTS `pending_deletes` (`uuid` TEXT NOT NULL, PRIMARY KEY(`uuid`))"
)

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_6_7_STATEMENTS.forEach(db::execSQL)
    }
}

@Database(
    entities = [
        Transaction::class,
        CategoryRule::class,
        PartnerTransaction::class,
        SharedRule::class,
        PendingDelete::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun categoryRuleDao(): CategoryRuleDao

    abstract fun partnerTransactionDao(): PartnerTransactionDao

    abstract fun sharedRuleDao(): SharedRuleDao

    abstract fun pendingDeleteDao(): PendingDeleteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kaspi_tracker.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                        MIGRATION_6_7
                    )
                    // Last-resort only: covers schema jumps with no migration path
                    // defined below. Every future version bump should add a real
                    // Migration above instead of relying on this, so history is
                    // never wiped by an app update again.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
