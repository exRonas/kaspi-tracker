# Статистика + приложение для пары — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** удалить бюджеты, добавить вкладку «Статистика», пометку «своё/общее» с массовым выбором и синхронизацию пары через Supabase с видами «Я / Она / Вместе».

**Architecture:** локальная Room-база остаётся главным источником. Свои операции получают стабильный `uuid` и хэш последней отправленной версии (`syncedHash`) — «грязные» строки находятся сравнением хэша, без правки каждого места записи. Операции партнёра лежат в отдельной таблице `partner_transactions` только для чтения. Все экраны со статистикой читают «ленту» `LedgerEntry` для выбранного `ViewScope`. Облако — Supabase через прямой REST (PostgREST + GoTrue) на `HttpURLConnection` + `org.json`; логика синхронизации — чистый `SyncEngine` за интерфейсом `CloudApi`, тестируется на фейке.

**Tech Stack:** Kotlin 2.0.21, Compose BOM 2024.09.03 / Material 3, Room 2.6.1 (KSP), WorkManager 2.9.1, org.json, Supabase (Postgres + RLS + anonymous auth), JUnit 4 + sqlite-jdbc для теста миграции.

**Spec:** `docs/superpowers/specs/2026-10-26-couple-stats-redesign-design.md`

**Вне этого плана:** редизайн по макетам Claude Design и онбординг (часть 4 спеки) — отдельный план, когда придут макеты. Здесь новые экраны собираются на текущих компонентах (`AppCard`, `SectionHeader`, `BarChart`, …).

## Отклонения от спеки (решено при планировании)

- **supabase-kt не используется.** Актуальные версии собраны под Kotlin 2.2–2.4, проект на 2.0.21; обновление тулчейна ради одного клиента — риск больше пользы. Нужно ~10 HTTP-вызовов — пишем сами.
- **Код связки — 8 символов (`ABCD-EFGH`), а не 6 цифр**, плюс лимит 10 неудачных попыток в час на пользователя. 6 цифр за 10 минут перебираются анонимными аккаунтами, а удачный перебор = чужой видит всю историю.
- **QR не делаем** — без сканера он бесполезен; ввод кода обязателен по спеке.
- **Анонимный вход — при первом действии на экране «Пара»**, а не на первом запуске: без пары сеть не нужна вовсе.

## Global Constraints

- Сборка (Git Bash): `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && "$JAVA_HOME/bin/java.exe" -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain <task>` из корня репозитория. Тесты — задача `testDebugUnitTest`, APK — `assembleDebug`.
- Каждая смена схемы Room — явная `Migration` в `addMigrations(...)`. `fallbackToDestructiveMigration()` остаётся, поэтому миграция 6→7 обязана быть зарегистрирована.
- Перед установкой сборки с миграцией на живой телефон (Samsung A36, `RFGL126BZDJ`) — снять копию `kaspi_tracker.db` + `-wal` через `adb exec-out run-as com.kleinapps.kaspitracker cat databases/…` (в Git Bash — `MSYS_NO_PATHCONV=1`).
- Все итоги считаются только по операциям с суммой и без `excludedFromStats`; трата = `PURCHASE`/`TRANSFER_OUT`, доход = `TRANSFER_IN`.
- В облако **не** уходят `rawText`, `title`, `dedupKey`, правила категорий.
- Облако используется только пока есть пара. Отвязка удаляет облачные операции обоих.
- Ключи Supabase — только в `local.properties` (`supabase.url`, `supabase.anonKey`) → `BuildConfig`; без них приложение собирается и работает, экран «Пара» говорит «синхронизация не настроена».
- Тексты UI — по-русски, деньги через `formatMoney`/`formatMoneyWhole`.
- Новые тесты с датами — в `TimeZone.getTimeZone("Asia/Almaty")`.
- Работаем в ветке `feature/couple-stats`, коммит после каждой задачи.

## Review Focus

1. **Миграция на реальной базе пользователя** (тысячи строк, есть строки без суммы): после 6→7 все строки на месте, у каждой уникальный `uuid`, Room открывает базу без падения и без стирания. — тест Task 1 + проверка на копии живой базы в Task 1.
2. **Массовая первая выгрузка** (3 000 строк одной пачкой получают одинаковый `updated_at`): партнёр должен получить все, а не первую страницу. — keyset-курсор `(updated_at, id)`, тест Task 7.
3. **Удаление операции после синхронизации**: у партнёра она исчезает; удаление до первой синхронизации не порождает лишнего запроса. — тесты Task 4 и Task 7.
4. **Отвязка с другой стороны**: партнёр отвязал — у меня при следующей синхронизации пропадают его данные и переключатель, мои строки снова «не отправлены». — тест Task 7.
5. **Повторный импорт бэкапа с тем же `uuid`** при изменённом тексте операции не падает на уникальном индексе. — тест Task 1.

---

### Task 1: Удалить бюджеты, миграция схемы 6→7, бэкап с uuid/shared

**Files:**
- Delete: `app/src/main/java/com/kleinapps/kaspitracker/data/Budget.kt`, `service/BudgetAlerts.kt`, `ui/screens/BudgetsScreen.kt`, `ui/screens/BudgetsViewModel.kt`
- Create: `data/PartnerTransaction.kt`, `data/SharedRule.kt`, `data/PendingDelete.kt`
- Modify: `data/Transaction.kt`, `data/TransactionDao.kt`, `data/AppDatabase.kt`, `data/TransactionRepository.kt`, `data/Backup.kt`, `ui/AppNavigation.kt`, `ui/screens/BackupScreen.kt`, `MainActivity.kt`, `app/build.gradle.kts`
- Test: `app/src/test/.../data/MigrationTest.kt` (new), `app/src/test/resources/schema_v6.sql`, `schema_v7.sql` (new), `BackupTest.kt`, `FakeDaos.kt`, `TransactionRepositoryTest.kt`

(Пути ниже без префикса — от `app/src/main/java/com/kleinapps/kaspitracker/`.)

**Interfaces:**
- Produces:
  - `Transaction` + поля `uuid: String = UUID.randomUUID().toString()`, `shared: Boolean = false`, `syncedHash: Int? = null`; `@Entity(tableName = "transactions", indices = [Index(value = ["uuid"], unique = true)])`.
  - `@Entity("partner_transactions") data class PartnerTransaction(@PrimaryKey val uuid: String, val timestamp: Long, val amount: Double?, val type: TxType, val merchant: String?, val category: Category?, val account: String?, val balance: Double?, val source: TxSource, val shared: Boolean, val excludedFromStats: Boolean)` + `fun PartnerTransaction.toTransaction(): Transaction` (`id = 0`, `title = null`, `rawText = ""`, тот же `uuid`).
  - `PartnerTransactionDao { @Upsert suspend fun upsertAll(rows: List<PartnerTransaction>); @Query("DELETE FROM partner_transactions WHERE uuid IN (:uuids)") suspend fun deleteByUuids(uuids: List<String>); @Query("DELETE FROM partner_transactions") suspend fun clear(); @Query("SELECT * FROM partner_transactions WHERE amount IS NOT NULL ORDER BY timestamp DESC") fun observeAll(): Flow<List<PartnerTransaction>> }`
  - `@Entity("shared_rules") data class SharedRule(@PrimaryKey val merchantKey: String, val shared: Boolean, val updatedAt: Long = System.currentTimeMillis())`; `SharedRuleDao { upsert(rule) (REPLACE); getAll(): List<SharedRule> }`.
  - `@Entity("pending_deletes") data class PendingDelete(@PrimaryKey val uuid: String)`; `PendingDeleteDao { @Insert(onConflict = IGNORE) insert(row); getAll(): List<PendingDelete>; deleteByUuids(uuids: List<String>); clear() }`.
  - `TransactionDao` + `getByIds(ids: List<Long>): List<Transaction>`, `markSynced(uuid: String, hash: Int)` (`UPDATE transactions SET syncedHash = :hash WHERE uuid = :uuid`), `clearSyncState()` (`UPDATE transactions SET syncedHash = NULL`); удалить `sumSpentInCategory`.
  - `AppDatabase` version 7, `partnerTransactionDao()`, `sharedRuleDao()`, `pendingDeleteDao()`; `internal val MIGRATION_6_7_STATEMENTS: List<String>`.
  - `TransactionRepository(dao, ruleDao, sharedRuleDao, pendingDeleteDao, partnerDao, sharedHintsEnabled: () -> Boolean = { true })` — бюджетных параметров больше нет. (В этой задаче новые DAO только принимаются; используются в Task 4/8.)
  - `Backup.Content(transactions, rules)`, `Backup.RestoreReport(added, skipped, rules)`.
  - Фейки в `FakeDaos.kt`: `FakeSharedRuleDao`, `FakePendingDeleteDao`, `FakePartnerTransactionDao` (по образцу `FakeCategoryRuleDao`, состояние в `MutableStateFlow`).

- [ ] **Step 1: Ветка и снимок схемы v6.** `git checkout -b feature/couple-stats`. Собрать `assembleDebug`, из `app/build/generated/ksp/debug/kotlin/.../data/AppDatabase_Impl.kt` скопировать SQL из `createAllTables` (все `CREATE TABLE`/`CREATE INDEX`, без `room_master_table`) в `app/src/test/resources/schema_v6.sql`, по одному оператору на строку с `;`.

- [ ] **Step 2: Удалить бюджеты.** Удалить файлы из списка Delete, вкладку и маршрут `BUDGETS` в `AppNavigation`, `BudgetWatcher`/`reportBudget`/`observeBudgets`/`setBudget`/`removeBudget`/`monthKey` из репозитория, бюджетные тесты из `TransactionRepositoryTest` (4 теста в секции «budget alerts» + `an excluded transfer never triggers a budget alert`) и `FakeBudgetDao`. `grep -rn "NotificationCompat\|NotificationManager" app/src/main/java`: если кроме удалённого `BudgetAlerts` никто не постит уведомления — убрать запрос `POST_NOTIFICATIONS` из `MainActivity` и разрешение из манифеста, иначе оставить.

- [ ] **Step 3: Новые сущности и поля** — по Interfaces. Тексты бэкапа в `BackupScreen.kt`: «Все операции и выученные правила категорий — одним JSON-файлом.», отчёт «Добавлено операций: N, уже было: M. Правил: K.»

- [ ] **Step 4: Снимок схемы v7.** Собрать, скопировать новый `createAllTables` в `schema_v7.sql` тем же способом.

- [ ] **Step 5: Написать падающий тест миграции.** `testImplementation("org.xerial:sqlite-jdbc:3.46.1.3")`. `MigrationTest`:

```kotlin
@Test fun `migration 6 to 7 keeps every row and gives each a unique uuid`() {
    val db = sqliteWith("schema_v6.sql")
    repeat(3) { db.exec("INSERT INTO transactions (timestamp, rawText, amount, type, currency, source, excludedFromStats) VALUES ($it, 'x', ${if (it == 2) "NULL" else "100.0"}, 'PURCHASE', '₸', 'NOTIFICATION', 0)") }
    db.exec("INSERT INTO budgets (category, monthlyLimit) VALUES ('GROCERIES', 1.0)")
    MIGRATION_6_7_STATEMENTS.forEach(db::exec)
    assertEquals(3, db.count("SELECT COUNT(*) FROM transactions"))
    assertEquals(3, db.count("SELECT COUNT(DISTINCT uuid) FROM transactions WHERE length(uuid) = 36"))
    assertEquals(0, db.count("SELECT COUNT(*) FROM transactions WHERE shared <> 0 OR syncedHash IS NOT NULL"))
    assertEquals(0, db.count("SELECT COUNT(*) FROM sqlite_master WHERE name = 'budgets'"))
}
@Test fun `migrated schema matches what Room creates for version 7`() {
    // PRAGMA table_info + PRAGMA index_list/index_info для каждой таблицы,
    // нормализованные в отсортированный список строк, должны совпасть
    // у базы v6+миграция и у базы, созданной из schema_v7.sql.
}
```

- [ ] **Step 6: Запустить — FAIL** (`MIGRATION_6_7_STATEMENTS` пуст / схемы расходятся).

- [ ] **Step 7: Реализовать `MIGRATION_6_7_STATEMENTS`** и `MIGRATION_6_7 = object : Migration(6, 7) { … statements.forEach(db::execSQL) }`, добавить в `addMigrations`. Состав: `DROP TABLE IF EXISTS budgets`; `ALTER TABLE transactions ADD COLUMN uuid TEXT NOT NULL DEFAULT ''`; `UPDATE transactions SET uuid = lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)),2) || '-' || substr('89ab', 1 + (abs(random()) % 4), 1) || substr(hex(randomblob(2)),2) || '-' || hex(randomblob(6)))`; `ADD COLUMN shared INTEGER NOT NULL DEFAULT 0`; `ADD COLUMN syncedHash INTEGER`; `CREATE UNIQUE INDEX` и `CREATE TABLE` для трёх новых таблиц — **дословно** из `schema_v7.sql`.

- [ ] **Step 8: Бэкап.** Падающие тесты в `BackupTest`/`TransactionRepositoryTest`:

```kotlin
@Test fun `uuid and shared survive a round trip`()            // encode→decode сохраняет uuid и shared=true
@Test fun `old backup without uuid gets a fresh uuid per row`() // JSON без "uuid": у двух строк разные непустые uuid
@Test fun `old backup with budgets still restores`()           // JSON с "budgets": [...] декодируется, rules на месте
@Test fun `restoring a row whose uuid already exists is skipped`() = runTest {
    val id = repo.insert(manual("Magnum"))
    val stored = dao.getById(id)!!
    val json = Backup.encode(Backup.Content(listOf(stored.copy(rawText = "изменён")), emptyList()))
    assertEquals(0, repo.importBackup(json).added)
    assertEquals(1, dao.rows.value.size)
}
```
Реализация: `encodeTransaction` пишет `uuid`, `shared`; `decodeTransaction` берёт `uuid` или генерирует; `syncedHash` не пишется и при импорте всегда `null`. `importBackup` пропускает строку, если её identity **или** uuid уже известны.

- [ ] **Step 9: Все тесты — PASS** (`testDebugUnitTest`), `assembleDebug` — успешно.

- [ ] **Step 10: Проверка на копии живой базы.** Снять копию базы с телефона (Global Constraints) в scratchpad, в Python `sqlite3` применить `MIGRATION_6_7_STATEMENTS` (скопировать список), сверить: число строк до/после, `COUNT(DISTINCT uuid) = COUNT(*)`. Затем установить APK на телефон, открыть приложение, убедиться, что история и суммы на Обзоре на месте (`adb exec-out screencap -p`).

- [ ] **Step 11: Commit** — `git commit -m "Drop budgets; schema 7 adds sync ids, shared flag and partner tables"`.

---

### Task 2: Единое правило подсчёта + расчёты статистики

**Files:**
- Create: `data/Ledger.kt`, `util/Stats.kt`
- Modify: `ui/screens/HomeViewModel.kt`, `ui/screens/AnalyticsViewModel.kt`
- Test: `app/src/test/.../data/LedgerTest.kt`, `app/src/test/.../util/StatsTest.kt`

**Interfaces:**
- Produces (`data/Ledger.kt`):
  - `val Transaction.isCountedSpending: Boolean` — `amount != null && !excludedFromStats && type.isSpending`.
  - `val Transaction.isCountedIncome: Boolean` — `amount != null && !excludedFromStats && type == TRANSFER_IN`.
  - `enum class Owner { ME, PARTNER }`, `enum class ViewScope { ME, PARTNER, TOGETHER }`, `data class LedgerEntry(val tx: Transaction, val owner: Owner)`.
  - `fun ledgerFor(scope: ViewScope, mine: List<Transaction>, partner: List<Transaction>): List<LedgerEntry>` — ME: все мои; PARTNER: все партнёра; TOGETHER: `shared == true` из обоих; всегда по убыванию `timestamp`.
  - `fun latestBalances(txs: List<Transaction>): List<AccountBalance>` и `fun balanceHistory(txs: List<Transaction>): Map<String, List<Double>>` — та же семантика, что `observeBalances`/`observeBalanceHistory` (последние 60 замеров, по 12 на карту, старые первыми).
- Produces (`util/Stats.kt`):
  - `data class MonthTotal(val month: Int /*0..11*/, val start: Long, val end: Long /*exclusive*/, val spent: Double, val income: Double, val isFuture: Boolean)`
  - `data class YearStats(val year: Int, val months: List<MonthTotal>, val spent: Double, val income: Double, val averageMonthSpent: Double?, val priciestMonth: MonthTotal?)`
  - `data class PeriodTotal(val start: Long, val spent: Double)`
  - `data class OverallStats(val firstDay: Long, val days: Int, val perDay: Double, val perWeek: Double, val perMonth: Double, val weekdayAverages: List<Double> /*Пн..Вс*/, val lastWeeks: List<PeriodTotal> /*12, старые первыми, последняя — текущая*/, val priciestDay: PeriodTotal?, val daysWithoutSpending: Int, val totalSpent: Double, val totalIncome: Double)`
  - `object Stats { fun years(txs: List<Transaction>, now: Long, zone: TimeZone = TimeZone.getDefault()): List<Int>; fun year(txs: List<Transaction>, year: Int, now: Long, zone: TimeZone = TimeZone.getDefault()): YearStats; fun overall(txs: List<Transaction>, now: Long, zone: TimeZone = TimeZone.getDefault()): OverallStats? }`

Правила (из спеки + решения): считаются только `isCountedSpending`/`isCountedIncome`. `years` — годы с учитываемыми операциями плюс текущий, по возрастанию. `averageMonthSpent` — среднее `spent` по месяцам `!isFuture`, где `spent > 0 || income > 0`; `null`, если таких нет. `priciestMonth` — максимум `spent` при `spent > 0`. `firstDay` — начало дня самой ранней учитываемой операции (трата или доход); `days` — число календарных дней от `firstDay` до сегодня включительно. `perDay = totalSpent / days`, `perWeek = perDay * 7`, `perMonth = perDay * 30.4375`. `weekdayAverages[i]` — сумма трат в этот день недели / сколько таких дней недели в диапазоне (0 при нуле таких дней). Недели — с понедельника. `daysWithoutSpending` — дни диапазона с тратами 0. `overall` возвращает `null`, если учитываемых операций нет.

- [ ] **Step 1: Падающие тесты** (`StatsTest`, зона Asia/Almaty, хелпер `at("2026-03-15 12:00")`):

```kotlin
@Test fun `excluded and amount-less rows are not counted`()
@Test fun `year splits spending and income by calendar month`()      // 1 000 в янв, 500 дохода в мар → months[0].spent==1000, months[2].income==500
@Test fun `months after now are future and have zero totals`()       // now = 15 мая 2026 → months[5..11].isFuture, months[4] нет
@Test fun `average month ignores empty and future months`()          // траты 1000 в янв и 3000 в мар, now=май → 2000.0
@Test fun `priciest month is the one with most spending`()           // → month == 2
@Test fun `operation at 23-59 on Jan 31 belongs to January`()        // граница месяца в зоне
@Test fun `years include current year even without operations`()     // только операции 2025, now 2026 → [2025, 2026]
@Test fun `per day divides by days since the first operation inclusive`() // первая 2026-05-01, now 2026-05-10, траты 1000 → days 10, perDay 100.0, perWeek 700.0, perMonth 3043.75
@Test fun `weekday averages divide by how many such weekdays passed`() // пн 4 и пн 11 мая по 300, now вт 12 мая → weekdayAverages[0] == 300.0
@Test fun `last weeks are twelve, oldest first, ending with this week`()
@Test fun `priciest day and days without spending`()
@Test fun `overall is null with no counted operations`()
```
`LedgerTest`:
```kotlin
@Test fun `together shows only shared rows of both people newest first`()
@Test fun `partner scope marks every entry as partner`()
@Test fun `latest balance per account comes from the newest reading`()
```

- [ ] **Step 2: Запустить — FAIL.**
- [ ] **Step 3: Реализовать `Ledger.kt` и `Stats.kt`.** Календарь — `Calendar.getInstance(zone)`.
- [ ] **Step 4: Починить подсчёт на Обзоре и в Аналитике**: в `HomeViewModel` и `AnalyticsViewModel` заменить `it.amount != null && it.type.isSpending` / `type == TRANSFER_IN` на `isCountedSpending` / `isCountedIncome` (сейчас переводы между своими картами там считаются — флаг учитывают только SQL-запросы). В `AnalyticsViewModel` убрать `byWeekday`/`buildWeekdays` и блок «По дням недели» из `AnalyticsScreen` (переезжает в Статистику).
- [ ] **Step 5: Тесты — PASS**, сборка — успешно.
- [ ] **Step 6: Commit** — `"Count own-card transfers out everywhere; add year and overall stats"`.

---

### Task 3: Вкладка «Статистика»

**Files:**
- Create: `ui/screens/StatsScreen.kt`, `ui/screens/StatsViewModel.kt`
- Modify: `ui/AppNavigation.kt` (`applyDrillDown` в `HistoryViewModel` уже принимает `category = null`)

**Interfaces:**
- Consumes: `Stats.years/year/overall`, `ViewScope`, `ledgerFor` (Task 2). До Task 8 VM читает `repo.observeAllWithAmount()` как вид ME; в Task 8 источник меняется на `repo.observeLedger(scope)`.
- Produces: `Routes.STATS = "stats"`; `Routes.history(category: Category?, from: Long, to: Long, period: String)` (категория стала nullable; при `null` параметр не пишется). `StatsUiState(val years: List<Int>, val year: YearStats?, val overall: OverallStats?)`, `StatsViewModel.selectYear(year: Int)`.

- [ ] **Step 1: Навигация.** Вкладки по порядку: «Обзор» (`Home`), «Статистика» (`Icons.Filled.Insights`), «Аналитика» (`PieChart`), «История» (`ReceiptLong`). Заголовок `STATS` — «Статистика». `Routes.history` с nullable категорией.
- [ ] **Step 2: `StatsViewModel`**: `MutableStateFlow` выбранного года (по умолчанию текущий), `combine` с источником операций → `StatsUiState`; время «сейчас» берётся при каждой эмиссии.
- [ ] **Step 3: `StatsScreen`** на текущих компонентах, сверху вниз:
  - строка выбора года `‹ 2026 ›` (стрелки неактивны на краях `years`);
  - `AppCard` «Год»: `StatTile`-ы «Потрачено», «Получено», «Разница», «Средний месяц», «Самый дорогой: <месяц>»;
  - `BarChart` трат по 12 месяцам (подписи «Янв…Дек», будущие — значение 0, но без «пика»), под ним список месяцев: название, `− трата`, `+ доход`; будущие месяцы приглушены, без цифр; тап по прошедшему месяцу → `Routes.history(null, start, end - 1, "<Месяц> <год>")`;
  - `AppCard` «В среднем»: в день / в неделю / в месяц, подпись «с <дата первой операции>»;
  - `BarChart` «По дням недели» (`weekdayAverages`, «Пн…Вс»);
  - `BarChart` «Последние 12 недель» (подпись — дата понедельника «12.10»);
  - `AppCard` «За всё время»: потрачено, получено, самый дорогой день (дата + сумма), «дней без трат: N».
  - Пустое состояние (`overall == null`): `EmptyState` «Пока нечего считать — статистика появится после первых операций».
- [ ] **Step 4: Проверка на телефоне** (`assembleDebug`, `adb install -r`, скриншоты в светлой и тёмной теме): цифры года сходятся с Историей за тот же месяц; тап по месяцу открывает Историю с чипом периода.
- [ ] **Step 5: Commit** — `"Add Statistics tab: year by month and overall averages"`.

---

### Task 4: «Своё / общее» и массовые операции в репозитории

**Files:**
- Modify: `data/TransactionRepository.kt`, `data/SharedRule.kt`
- Test: `app/src/test/.../data/TransactionRepositoryTest.kt`

**Interfaces:**
- Consumes: `SharedRuleDao`, `PendingDeleteDao` (Task 1).
- Produces (в `TransactionRepository`):
  - `suspend fun setShared(ids: Collection<Long>, shared: Boolean)`
  - `suspend fun setCategory(ids: Collection<Long>, category: Category)`
  - `suspend fun deleteMany(ids: Collection<Long>)`
  - `delete(transaction)` и `deleteMany` кладут `PendingDelete(uuid)` только для строк с `syncedHash != null`.
  - `update(transaction)`: если `shared` изменился — запоминает `SharedRule` для мерчанта.
  - Подсказка: при вставке (ручной, push, новый чек) строка с `shared == false` получает `shared = rule.shared`, если для `MerchantKey.of(merchant)` есть `SharedRule` и `sharedHintsEnabled()` — `true`. Применяется там же, где сейчас `autoCategorize` (переименовать в `applyRules`).
  - `SharedHints` (в `data/SharedRule.kt`): `object SharedHints { fun enabled(context: Context): Boolean; fun setEnabled(context: Context, value: Boolean) }` — prefs `settings`, ключ `shared_hints`, по умолчанию `true`. Передаётся в репозиторий в `TransactionRepository.get()`.

- [ ] **Step 1: Падающие тесты:**

```kotlin
@Test fun `marking several rows shared updates all of them`()
@Test fun `marking shared teaches the merchant and the next purchase arrives shared`() = runTest {
    val id = repo.insert(manual("Magnum"))
    repo.setShared(listOf(id), true)
    val next = repo.insert(manual("MAGNUM"))
    assertTrue(dao.getById(next)!!.shared)
}
@Test fun `marking back as own teaches the merchant too`()
@Test fun `hints switched off leave new rows own`()               // repo с sharedHintsEnabled = { false }
@Test fun `bulk category change learns each merchant`()
@Test fun `deleting a synced row queues a remote delete`()       // строка с syncedHash = 1 → pendingDeleteDao.getAll() содержит её uuid
@Test fun `deleting a never synced row queues nothing`()
@Test fun `deleteMany removes all selected rows`()
```
- [ ] **Step 2: FAIL.** - [ ] **Step 3: Реализовать.** - [ ] **Step 4: PASS** (все тесты).
- [ ] **Step 5: Commit** — `"Shared/own flag with per-merchant hint and bulk operations"`.

---

### Task 5: UI выбора нескольких операций и переключатели «общее»

**Files:**
- Create: `ui/components/SelectionBar.kt`
- Modify: `ui/screens/HistoryScreen.kt`, `ui/screens/HistoryViewModel.kt`, `ui/screens/HomeScreen.kt`, `ui/screens/HomeViewModel.kt`, `ui/components/Common.kt` (`TransactionRow`), `ui/components/EditTransactionSheet.kt`, `ui/screens/AddTransactionScreen.kt`, `ui/screens/AddTransactionViewModel.kt`

**Interfaces:**
- Consumes: `setShared`, `setCategory`, `deleteMany` (Task 4).
- Produces:
  - `@Composable fun SelectionBar(count: Int, total: Double, onShared: () -> Unit, onOwn: () -> Unit, onCategory: () -> Unit, onDelete: () -> Unit, onClose: () -> Unit)` — «Выбрано: N · <сумма>», кнопки «Общее», «Своё», «Категория», «Удалить».
  - `TransactionRow(tx, modifier, selected: Boolean? = null, ownerBadge: (@Composable () -> Unit)? = null)`: `selected != null` — показывать чекбокс; бейдж «общее» (`Pill`) при `tx.shared`; «не в статистике» при `excludedFromStats`.
  - `HistoryFilters.shared: Boolean?` + `HistoryViewModel.setShared(filter: Boolean?)`, чип «Своё / Общее».
  - `HistoryViewModel.markShared(ids: Set<Long>, shared: Boolean)`, `.setCategory(ids: Set<Long>, category: Category)`, `.deleteMany(ids: Set<Long>)`; те же три метода в `HomeViewModel`.

- [ ] **Step 1: История.** Состояние выбора `Set<Long>` в экране (`rememberSaveable`). Долгий тап по строке включает режим и выбирает строку; в режиме тап переключает выбор; тап по заголовку дня выбирает/снимает весь день; `BackHandler` и крестик выходят. «Категория» открывает `CategoryPickerGrid` в нижнем листе; «Удалить» — диалог «Удалить операции?» с числом. После действия режим закрывается. Старое удаление одной строки остаётся в листе редактирования.
- [ ] **Step 2: Обзор.** Тот же режим для «Последних операций».
- [ ] **Step 3: Лист редактирования**: переключатели «Общее» и «Не учитывать в статистике» (пишут `shared` / `excludedFromStats`). **Новая запись**: переключатель «Общее».
- [ ] **Step 4: Тесты/сборка — PASS; проверка на телефоне**: выбрать 3 операции → «Общее» → бейджи появились; новый Magnum приходит общим (ручной записью); удаление пачки; выход «Назад»; длинный мерчант не ломает строку с чекбоксом.
- [ ] **Step 5: Commit** — `"Multi-select in History and Home: mark shared/own, recategorize, delete"`.

---

### Task 6: Схема Supabase, RLS и живая проверка доступа

**Files:**
- Create: `supabase/schema.sql`, `supabase/check_rls.py`, `supabase/README.md`

**Interfaces:**
- Produces (контракт для Task 7):
  - таблицы `profiles(user_id uuid pk, display_name text, color int, updated_at)`, `couples(id uuid pk, user_a uuid unique, user_b uuid unique, created_at)`, `transactions(id uuid pk, user_id uuid, timestamp bigint, amount float8, type text, merchant text, category text, account text, balance float8, source text, shared bool, excluded_from_stats bool, deleted bool, updated_at timestamptz)`.
  - RPC: `create_pair_code() → json {"code": "ABCDEFGH", "expires_at": "<timestamptz>"}` (ошибка — `raise exception 'already_paired'`); `redeem_pair_code(p_code text) → text` — ровно `ok`, `code_invalid`, `code_expired`, `own_code`, `already_paired`, `too_many_attempts` (статусом, а не исключением: исключение откатило бы запись о неудачной попытке); `unpair() → void`.

`supabase/schema.sql` (ключевое — исполнитель может дописать `grant`/индексы, но не ослаблять политики):

```sql
create extension if not exists pgcrypto;

create table public.profiles (
  user_id uuid primary key references auth.users on delete cascade,
  display_name text not null default '',
  color int not null default 0,
  updated_at timestamptz not null default now()
);
create table public.couples (
  id uuid primary key default gen_random_uuid(),
  user_a uuid not null unique references auth.users on delete cascade,
  user_b uuid not null unique references auth.users on delete cascade,
  created_at timestamptz not null default now(),
  check (user_a <> user_b)
);
create table public.pair_codes (
  code text primary key,
  user_id uuid not null unique references auth.users on delete cascade,
  expires_at timestamptz not null
);
create table public.pair_attempts (
  user_id uuid not null, at timestamptz not null default now()
);
create table public.transactions (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users on delete cascade,
  timestamp bigint not null,
  amount double precision, type text not null, merchant text, category text,
  account text, balance double precision, source text not null,
  shared boolean not null default false,
  excluded_from_stats boolean not null default false,
  deleted boolean not null default false,
  updated_at timestamptz not null default clock_timestamp()
);
create index transactions_pull on public.transactions (user_id, updated_at, id);

create function public.touch_updated_at() returns trigger language plpgsql as $$
begin new.updated_at := clock_timestamp(); return new; end $$;
create trigger transactions_touch before insert or update on public.transactions
  for each row execute function public.touch_updated_at();

create function public.partner_of(uid uuid) returns uuid
  language sql stable security definer set search_path = public as $$
  select case when user_a = uid then user_b else user_a end
  from couples where uid in (user_a, user_b) $$;

alter table public.profiles enable row level security;
alter table public.couples enable row level security;
alter table public.pair_codes enable row level security;
alter table public.pair_attempts enable row level security;
alter table public.transactions enable row level security;

create policy profiles_read on public.profiles for select
  using (user_id = auth.uid() or user_id = public.partner_of(auth.uid()));
create policy profiles_write on public.profiles for insert with check (user_id = auth.uid());
create policy profiles_update on public.profiles for update
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy couples_read on public.couples for select using (auth.uid() in (user_a, user_b));
create policy tx_read on public.transactions for select
  using (user_id = auth.uid() or user_id = public.partner_of(auth.uid()));
create policy tx_insert on public.transactions for insert with check (user_id = auth.uid());
create policy tx_update on public.transactions for update
  using (user_id = auth.uid()) with check (user_id = auth.uid());
-- pair_codes, pair_attempts: без политик — только через функции ниже.

create function public.create_pair_code() returns json
  language plpgsql security definer set search_path = public as $$
declare c text; alphabet text := 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'; i int;
begin
  if auth.uid() is null then raise exception 'code_invalid'; end if;
  if exists (select 1 from couples where auth.uid() in (user_a, user_b)) then raise exception 'already_paired'; end if;
  delete from pair_codes where user_id = auth.uid() or expires_at < now();
  loop
    c := '';
    for i in 1..8 loop c := c || substr(alphabet, 1 + (get_byte(gen_random_bytes(1), 0) % length(alphabet)), 1); end loop;
    exit when not exists (select 1 from pair_codes where code = c);
  end loop;
  insert into pair_codes (code, user_id, expires_at) values (c, auth.uid(), now() + interval '10 minutes');
  return json_build_object('code', c, 'expires_at', now() + interval '10 minutes');
end $$;

create function public.redeem_pair_code(p_code text) returns text
  language plpgsql security definer set search_path = public as $$
declare owner uuid; exp timestamptz;
begin
  if auth.uid() is null then return 'code_invalid'; end if;
  if (select count(*) from pair_attempts where user_id = auth.uid() and at > now() - interval '1 hour') >= 10
    then return 'too_many_attempts'; end if;
  select user_id, expires_at into owner, exp from pair_codes where code = upper(replace(p_code, '-', ''));
  if owner is null then insert into pair_attempts (user_id) values (auth.uid()); return 'code_invalid'; end if;
  if exp < now() then return 'code_expired'; end if;
  if owner = auth.uid() then return 'own_code'; end if;
  if exists (select 1 from couples where owner in (user_a, user_b) or auth.uid() in (user_a, user_b))
    then return 'already_paired'; end if;
  insert into couples (user_a, user_b) values (owner, auth.uid());
  delete from pair_codes where user_id = owner;
  return 'ok';
end $$;

create function public.unpair() returns void
  language plpgsql security definer set search_path = public as $$
declare a uuid; b uuid;
begin
  select user_a, user_b into a, b from couples where auth.uid() in (user_a, user_b);
  if a is null then return; end if;
  delete from transactions where user_id in (a, b);
  delete from couples where user_a = a;
end $$;

revoke all on function public.create_pair_code, public.redeem_pair_code(text), public.unpair from public, anon;
grant execute on function public.create_pair_code, public.redeem_pair_code(text), public.unpair to authenticated;
```

- [ ] **Step 1: Написать `check_rls.py`** (stdlib `urllib`, env `SUPABASE_URL`, `SUPABASE_ANON_KEY`). Создаёт трёх анонимных пользователей (`POST /auth/v1/signup` с `{}`) A, B, C и проверяет `assert`-ами:
  1. A: `create_pair_code` → 8 символов из алфавита.
  2. C: `redeem_pair_code('ZZZZZZZZ')` → `code_invalid`; A: свой код → `own_code`.
  3. B: код A → `ok`; повторно другим кодом → `already_paired`.
  4. A вставляет строку; B читает `transactions?user_id=eq.<A>` → 1 строка; C → 0 строк.
  5. C вставляет строку с `user_id = A` → ошибка RLS (HTTP 401/403); B `PATCH` строку A → 0 изменённых строк.
  6. C 11 раз вводит неверный код → на 11-й `too_many_attempts`.
  7. C читает `couples`, `profiles` A, `pair_codes` → пусто.
  8. A `unpair()` → B читает строки A → 0; A читает свои → 0.
  Печатает `RLS OK` в конце.
- [ ] **Step 2: `supabase/README.md`** — по шагам для пользователя: создать новый проект; Authentication → Sign In / Providers → включить **Allow anonymous sign-ins**; SQL Editor → вставить `schema.sql` → Run; Project Settings → API → скопировать Project URL и anon key в `local.properties` как `supabase.url=` и `supabase.anonKey=`; запустить `python supabase/check_rls.py`.
- [ ] **Step 3: Блокирующий шаг — попросить пользователя** создать проект, применить SQL и прислать URL + anon-ключ (ключ — в `local.properties`, не в git, не в чат-логи коммитов).
- [ ] **Step 4: Запустить `check_rls.py`** с ключами → `RLS OK`. При провале — чинить `schema.sql`, пользователь применяет заново.
- [ ] **Step 5: Commit** — `"Supabase schema with row-level security and pairing functions"`.

> Пока ключей нет, Task 7–9 делаются полностью на фейках; живые проверки (Task 9 Step 5, Task 10) ждут Step 4.

---

### Task 7: Облачный клиент и SyncEngine

**Files:**
- Create: `sync/CloudApi.kt`, `sync/CloudMapping.kt`, `sync/SyncEngine.kt`, `sync/SupabaseCloud.kt`, `sync/CloudConfig.kt`
- Modify: `app/build.gradle.kts` (`buildConfig = true`, поля из `local.properties`), `AndroidManifest.xml` (`INTERNET`)
- Test: `app/src/test/.../sync/SyncEngineTest.kt`, `sync/FakeCloud.kt`, `sync/CloudMappingTest.kt`

**Interfaces:**
- Consumes: `Transaction.uuid/shared/syncedHash`, `PartnerTransaction`, DAO-методы из Task 1.
- Produces:

```kotlin
data class CloudTransaction(
    val uuid: String, val timestamp: Long, val amount: Double?, val type: TxType,
    val merchant: String?, val category: Category?, val account: String?, val balance: Double?,
    val source: TxSource, val shared: Boolean, val excludedFromStats: Boolean,
    val deleted: Boolean = false, val updatedAt: String? = null
)
data class PullCursor(val updatedAt: String, val uuid: String)
data class PairCode(val code: String, val expiresAt: Long)
data class Profile(val name: String, val color: Int)
enum class CloudError { CODE_INVALID, CODE_EXPIRED, OWN_CODE, ALREADY_PAIRED, TOO_MANY_ATTEMPTS, NETWORK, SERVER_UNAVAILABLE, NOT_CONFIGURED, UNKNOWN }
class CloudException(val error: CloudError, message: String? = null) : Exception(message)

interface CloudApi {
    suspend fun ensureSignedIn(): String                       // userId
    suspend fun createPairCode(): PairCode
    suspend fun redeemPairCode(code: String)                   // CloudException при статусе != "ok"
    suspend fun partnerId(): String?                           // из couples; null — пары нет
    suspend fun unpair()
    suspend fun saveProfile(profile: Profile)
    suspend fun profile(userId: String): Profile?
    suspend fun push(rows: List<CloudTransaction>)             // upsert, пачки по 500
    suspend fun markDeleted(uuids: List<String>)
    suspend fun pull(partnerId: String, after: PullCursor?, limit: Int): List<CloudTransaction> // по (updated_at, id) возр.
}

fun Transaction.toCloud(): CloudTransaction
fun Transaction.syncHash(): Int = toCloud().hashCode()
fun CloudTransaction.toPartner(): PartnerTransaction

interface SyncCursorStore { var cursor: PullCursor? }

sealed interface SyncResult {
    data class Synced(val partnerId: String, val partner: Profile?, val pushed: Int, val pulled: Int) : SyncResult
    data object Unpaired : SyncResult
}

class SyncEngine(
    private val dao: TransactionDao,
    private val partnerDao: PartnerTransactionDao,
    private val pendingDeleteDao: PendingDeleteDao,
    private val cloud: CloudApi,
    private val cursorStore: SyncCursorStore,
    private val pageSize: Int = 1000
) {
    suspend fun sync(): SyncResult
    suspend fun wipePartnerData()
}
```

`sync()`: `partnerId()`; `null` → `wipePartnerData()`, `Unpaired`. Иначе: (1) свои строки с `amount != null` и `syncHash() != syncedHash` → `push` → `markSynced(uuid, hash)` для каждой отправленной; (2) `pendingDeleteDao.getAll()` → `markDeleted` → `deleteByUuids`; (3) цикл `pull(partnerId, cursor, pageSize)`: `deleted` → `partnerDao.deleteByUuids`, остальные → `upsertAll(toPartner())`, курсор = последняя строка страницы, сохранять после каждой страницы, выход при странице короче `pageSize`; (4) `profile(partnerId)`. `wipePartnerData()`: `partnerDao.clear()`, `dao.clearSyncState()`, `pendingDeleteDao.clear()`, `cursor = null`.

`SupabaseCloud(context)` — REST:
- сессия в prefs `supabase_session` (`access_token`, `refresh_token`, `expires_at`, `user_id`); `ensureSignedIn`: есть сессия → при `expires_at - 60 s < now` `POST /auth/v1/token?grant_type=refresh_token`; нет → `POST /auth/v1/signup` с `{}`;
- заголовки `apikey: <anon>`, `Authorization: Bearer <access>`, `Content-Type: application/json`;
- `POST /rest/v1/rpc/create_pair_code`, `/rpc/redeem_pair_code` `{"p_code": …}`, `/rpc/unpair`;
- `GET /rest/v1/couples?select=user_a,user_b`;
- `POST /rest/v1/profiles` с `Prefer: resolution=merge-duplicates`; `GET /rest/v1/profiles?user_id=eq.<id>&select=display_name,color`;
- `POST /rest/v1/transactions` с `Prefer: resolution=merge-duplicates,return=minimal` (без `user_id` в теле — default `auth.uid()`);
- `PATCH /rest/v1/transactions?id=in.(<uuids>)` `{"deleted": true}`;
- `GET /rest/v1/transactions?user_id=eq.<p>&order=updated_at.asc,id.asc&limit=<n>` + при курсоре `&or=(updated_at.gt.<T>,and(updated_at.eq.<T>,id.gt.<U>))` (значения URL-кодировать);
- ошибки: `IOException` → `NETWORK`; HTTP 5xx/540 → `SERVER_UNAVAILABLE`; пустые ключи → `NOT_CONFIGURED`; статус RPC → соответствующий `CloudError`.
- JSON ↔ `CloudTransaction` — в `CloudMapping.kt` (`fun CloudTransaction.toJson(): JSONObject`, `fun cloudTransactionFrom(json: JSONObject): CloudTransaction`), поля в snake_case как в `schema.sql`.

`CloudConfig`: `object CloudConfig { val url: String; val anonKey: String; val isConfigured: Boolean }` из `BuildConfig.SUPABASE_URL` / `SUPABASE_ANON_KEY` (`local.properties` → `buildConfigField`, по умолчанию `""`).

- [ ] **Step 1: `FakeCloud`** — хранит строки обоих «пользователей» в памяти, считает `updatedAt` монотонным счётчиком (одинаковым для всей пачки `push`, чтобы воспроизвести массовую выгрузку), реализует курсор как в REST.
- [ ] **Step 2: Падающие тесты:**

```kotlin
@Test fun `only changed rows are pushed`()                         // второй sync() ничего не отправляет
@Test fun `editing a synced row pushes it again`()
@Test fun `amount-less pushes never leave the phone`()
@Test fun `pushed rows carry no raw text`()                        // CloudTransaction не имеет rawText; toCloud() не теряет shared/excluded/balance
@Test fun `partner rows land in the partner table`()
@Test fun `a big first upload arrives whole across pages`()        // партнёр выгрузил 2 500 строк одним push; pageSize = 1000 → partnerDao содержит 2 500
@Test fun `partner deletion removes the row here`()
@Test fun `queued deletes are sent once and cleared`()
@Test fun `partner unpaired wipes partner data and resets sync state`() // после Unpaired: partner пусто, у своих syncedHash == null, cursor == null
@Test fun `a failed push leaves rows dirty for the next try`()     // FakeCloud бросает NETWORK на push → syncedHash не изменился
```
`CloudMappingTest`: `json round trip keeps every field`, `unknown category from server becomes null`.
- [ ] **Step 3: FAIL.** - [ ] **Step 4: Реализовать `SyncEngine`, маппинг, `SupabaseCloud`, `CloudConfig`.** - [ ] **Step 5: PASS**, `assembleDebug` без ключей — успешно.
- [ ] **Step 6: Commit** — `"Sync engine over Supabase REST with keyset pull and tombstones"`.

---

### Task 8: Состояние пары, виды «Я / Она / Вместе» во всех экранах

**Files:**
- Create: `sync/CoupleStore.kt`, `sync/CoupleManager.kt`, `ui/components/ScopeSwitcher.kt`
- Modify: `data/TransactionRepository.kt`, `ui/screens/HomeViewModel.kt`, `HomeScreen.kt`, `StatsViewModel.kt`, `StatsScreen.kt`, `AnalyticsViewModel.kt`, `AnalyticsScreen.kt`, `HistoryViewModel.kt`, `HistoryScreen.kt`, `ui/components/Common.kt`
- Test: `app/src/test/.../sync/CoupleManagerTest.kt`, `TransactionRepositoryTest.kt`

**Interfaces:**
- Consumes: `ledgerFor`, `latestBalances`, `balanceHistory` (Task 2), `SyncEngine`, `CloudApi` (Task 7).
- Produces:

```kotlin
data class CoupleState(
    val paired: Boolean = false,
    val myName: String = "", val myColor: Int = 0,
    val partnerName: String = "", val partnerColor: Int = 0,
    val pendingCode: PairCode? = null,
    val lastSyncAt: Long = 0L, val lastError: CloudError? = null,
    val scope: ViewScope = ViewScope.ME
) { val effectiveScope: ViewScope get() = if (paired) scope else ViewScope.ME }

interface CoupleStateHolder { val state: StateFlow<CoupleState>; fun update(block: (CoupleState) -> CoupleState) }
object CoupleStore : CoupleStateHolder { fun init(context: Context) }   // prefs "couple", JSON одной строкой

class CoupleManager(private val cloud: CloudApi, private val engine: SyncEngine, private val holder: CoupleStateHolder) {
    suspend fun saveProfile(name: String, color: Int)
    suspend fun createCode(): PairCode            // ensureSignedIn, saveProfile, createPairCode, pendingCode
    suspend fun redeem(code: String)              // ensureSignedIn, saveProfile, redeemPairCode, paired = true, syncNow()
    suspend fun checkPending(): Boolean           // partnerId() != null → paired, pendingCode = null, syncNow()
    suspend fun syncNow()                         // только при paired; Synced → partnerName/Color, lastSyncAt, lastError = null; Unpaired → paired = false, scope = ME; CloudException → lastError
    suspend fun unpair()                          // cloud.unpair(), engine.wipePartnerData(), paired = false, scope = ME
    fun setScope(scope: ViewScope)
    companion object { fun get(context: Context): CoupleManager }
}

// TransactionRepository
fun observeLedger(scope: ViewScope): Flow<List<LedgerEntry>>   // combine(dao.observeAllWithAmount(), partnerDao.observeAll()) → ledgerFor

@Composable fun ScopeSwitcher(scope: ViewScope, myName: String, partnerName: String, onSelect: (ViewScope) -> Unit, modifier: Modifier = Modifier)
// SingleChoiceSegmentedButtonRow: «<моё имя или "Я">» · «<имя партнёра или "Партнёр">» · «Вместе»
```
`TransactionRow` + параметр `owner: Owner? = null` — в виде «Вместе» маленький кружок с инициалом и цветом владельца.

Поведение экранов (спека, часть 3):
- Все четыре VM: `CoupleStore.state.map { it.effectiveScope }.distinctUntilChanged().flatMapLatest { repo.observeLedger(it) }`; расчёты — по `entries.map { it.tx }`.
- Обзор: в PARTNER — остатки из `latestBalances`/`balanceHistory` её строк, без баннеров доступа/молчания/«Разобрать», без «Инструментов», «Последние операции» без выбора и без листа редактирования; в TOGETHER — блок остатков скрыт, в героической карточке подпись «из них вы: X ₸ (N%) · <имя>: Y ₸ (M%)»; в ME — как сейчас.
- История: в PARTNER нет листа редактирования и выбора; в TOGETHER выбираются и редактируются только строки `owner == ME`.
- Аналитика, Статистика: только переключатель, логика та же.
- `ScopeSwitcher` показывается сверху на Обзоре, Статистике, Аналитике, Истории только при `paired`.

- [ ] **Step 1: Падающие тесты `CoupleManagerTest`** (`FakeCloud` из Task 7, holder на `MutableStateFlow`):

```kotlin
@Test fun `redeeming a code pairs and pulls partner data`()
@Test fun `wrong code reports the reason and stays unpaired`()     // CODE_INVALID в исключении, paired == false
@Test fun `pending code turns into a pair when partner redeems it`()
@Test fun `partner unpairing is noticed on next sync and scope falls back to me`()
@Test fun `network failure is recorded and data is kept`()
@Test fun `unpair wipes partner rows locally`()
```
`TransactionRepositoryTest`: `ledger in together scope combines shared rows of both people`.
- [ ] **Step 2: FAIL.** - [ ] **Step 3: Реализовать `CoupleStore`, `CoupleManager`, `observeLedger`.** - [ ] **Step 4: PASS.**
- [ ] **Step 5: Переключить четыре VM и экраны** по «Поведение экранов». Без пары UI выглядит как раньше (проверить на телефоне).
- [ ] **Step 6: Commit** — `"Me / partner / together views across Home, Stats, Analytics, History"`.

---

### Task 9: Экран «Пара», расписание синхронизации, подключение в приложении

**Files:**
- Create: `ui/screens/PairScreen.kt`, `ui/screens/PairViewModel.kt`, `sync/SyncScheduler.kt`
- Modify: `ui/AppNavigation.kt`, `ui/screens/HomeScreen.kt`, `KaspiTrackerApp.kt`

**Interfaces:**
- Consumes: `CoupleManager`, `CoupleStore`, `SharedHints`, `CloudConfig`.
- Produces: `Routes.PAIR = "pair"` (заголовок «Пара»); `object SyncScheduler { fun schedulePeriodic(context: Context); fun requestSoon(context: Context); }` — периодически раз в 15 минут с `NetworkType.CONNECTED`, `KEEP`; `requestSoon` — `OneTimeWorkRequest` с задержкой 5 с, `ExistingWorkPolicy.REPLACE`, уникальное имя `sync_now`. `SyncWorker : CoroutineWorker` — `if (!CloudConfig.isConfigured) success`; при `pendingCode` — `checkPending()`; при `paired` — `syncNow()`; иначе ничего.

- [ ] **Step 1: `KaspiTrackerApp.onCreate`**: `CoupleStore.init`, `SyncScheduler.schedulePeriodic`; в `appScope` — `repository.observeAll().drop(1).debounce(5_000).collect { if (CoupleStore.state.value.paired) SyncScheduler.requestSoon(this) }`; при открытии `MainActivity` (onStart) — `requestSoon`, если `paired`.
- [ ] **Step 2: `PairScreen`** (на текущих компонентах), секции:
  - не настроено (`!CloudConfig.isConfigured`): `EmptyState` «Синхронизация не настроена в этой сборке».
  - «Обо мне»: поле «Моё имя», 6 цветов-кружков (палитра — `Mint, Coral, Amber, Pink, Indigo, Violet` из `Color.kt`), сохраняется по «Сохранить».
  - не связаны: тёплый пустой блок «Вы ещё не связаны» → «Показать мой код» (крупно `ABCD-EFGH`, обратный отсчёт до `expiresAt`, «Код действует 10 минут, передай его партнёру»; пока экран открыт — `checkPending()` каждые 3 с) и «Ввести код партнёра» (поле на 8 символов, автоперевод в верхний регистр, дефис игнорируется, кнопка «Связаться»).
  - ошибки по `CloudError`: `CODE_INVALID` «Такого кода нет — проверь буквы», `CODE_EXPIRED` «Код устарел — попроси новый», `OWN_CODE` «Это твой собственный код», `ALREADY_PAIRED` «Кто-то из вас уже связан с другим человеком», `TOO_MANY_ATTEMPTS` «Слишком много попыток — попробуй через час», `NETWORK` «Нет интернета», `SERVER_UNAVAILABLE` «Сервер не отвечает — возможно, проект Supabase на паузе. Разбуди его в панели Supabase», остальное «Что-то пошло не так».
  - связаны: «Вы связаны с <имя>», «Последняя синхронизация: <формат как в бэкапе>», кнопка «Синхронизировать сейчас», переключатель «Подсказывать «общее» по магазину» (`SharedHints`), «Отвязать» → диалог «Отвязать <имя>? Общие данные в облаке удалятся у обоих, на телефонах останутся только свои операции.» → `unpair()`.
- [ ] **Step 3: Плитка «Пара»** в «Инструментах» на Обзоре («Связать телефоны и смотреть траты вдвоём»; при связи — «Связаны с <имя>»).
- [ ] **Step 4: Сборка — успешно; без ключей экран показывает «не настроено».**
- [ ] **Step 5 (после Task 6 Step 4): живая связка** — телефон пользователя + второе устройство (эмулятор `emulator -avd …` или её телефон по adb): связать кодом, отметить на одном 3 операции «общими», через «Синхронизировать сейчас» увидеть их в «Вместе» на другом; удалить одну — пропала; отвязать — у второго пропали данные и переключатель. Скриншоты обоих.
- [ ] **Step 6: Commit** — `"Pair screen, background sync scheduling"`.

---

### Task 10: Документация и финальная проверка

**Files:**
- Modify: `FEATURES.md`
- Modify (memory): `kaspi-tracker-build-setup.md` — где лежат ключи Supabase и как запускать `check_rls.py`.

- [ ] **Step 1: `FEATURES.md`** — версия БД 7, новые таблицы, раздел «Пара и синхронизация» (как работает, что уходит в облако, что нет, отвязка, заморозка бесплатного проекта), «Статистика», массовый выбор; убрать бюджеты из экранов/оповещений/roadmap; обновить число тестов.
- [ ] **Step 2: Полный прогон** `testDebugUnitTest` + `assembleDebug` → оба успешно; итоговые скриншоты Обзора/Статистики/Истории в светлой и тёмной теме на телефоне.
- [ ] **Step 3: Commit** — `"Document statistics, couples and sync"`.
