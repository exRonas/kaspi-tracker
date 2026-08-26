package com.kleinapps.kaspitracker.data

/**
 * Spending category. Stored in the DB by enum name; auto-assigned by
 * [CategoryClassifier] and editable by the user afterwards.
 */
enum class Category(val label: String) {
    GROCERIES("Продукты"),
    CAFE("Кафе и еда"),
    TRANSPORT("Транспорт"),
    SHOPPING("Покупки"),
    HEALTH("Здоровье"),
    ENTERTAINMENT("Развлечения"),
    UTILITIES("Связь и коммуналка"),
    EDUCATION("Образование"),
    TRANSFER("Переводы"),
    INCOME("Доходы"),
    OTHER("Прочее");

    companion object {
        /** Categories that make sense as spending budgets (everything except income). */
        val budgetable: List<Category> = entries.filter { it != INCOME }
    }
}

/**
 * Keyword-based auto-categorization, plus rules learned from the user's own
 * corrections ([CategoryRule]).
 *
 * Matching order:
 *  1. learned rules — whatever the user picked for this merchant last time wins,
 *     including for outgoing transfers the keyword rules deliberately skip;
 *  2. transfer defaults (incoming money is income, outgoing needs a human);
 *  3. keyword lists below, tuned for Kazakhstan merchants.
 *
 * Keywords match only at a word start (so "оси" no longer fires inside
 * "гОСномер"), case-insensitively, against merchant + raw notification/receipt
 * text. A keyword written with a trailing space must also end on a word
 * boundary ("газ " matches "Газ", not "газета").
 */
object CategoryClassifier {

    private val RULES: List<Pair<Category, List<String>>> = listOf(
        Category.GROCERIES to listOf(
            "magnum", "small", "anvar", "galmart", "arbuz", "продукт", "супермаркет",
            "гастроном", "мясн", "овощ", "фрукт", "birlik", "toimart", "клевер", "fix price",
            // Regulars from the history that used to be re-categorized by hand.
            "inmart", "низкоцен", "скиф трейд", "дом хлеба", "хлеб", "молоч", "бакале"
        ),
        Category.CAFE to listOf(
            "kfc", "burger", "mcdonald", "wolt", "glovo", "chocofood", "кофе", "coffee",
            "ресторан", "кафе", "столов", "пицц", "pizza", "суши", "sushi", "bahandi",
            "salam bro", "hardee", "донер", "doner", "шаурм", "чайхан", "bistro", "starbucks",
            "готовая еда", "фаст-фуд", "фастфуд", "куры-гриль", "кондитер"
        ),
        Category.TRANSPORT to listOf(
            "onay", "яндекс", "yandex", "indrive", "такси", "taxi", "автобус", "метро",
            "азс", "азc", "helios", "sinooil", "qazaq oil", "petrol", "газпром", "бензин",
            "parking", "парков", "cdek",
            // Public transport / train receipts: the merchant line is a per-ride
            // receipt number, so these have to be recognized by the service name.
            "avtobys", "общественный транспорт", "оплата проезда", "проезд",
            "поезд", "жд ", "вокзал", "ктж", "ktzh", "temir zholy", "темир жол",
            // Bare "билет" is left out on purpose — cinema tickets are not transport.
            "билет avtobys", "авиабилет", "жд билет", "kaspi travel", "перевозк"
        ),
        Category.SHOPPING to listOf(
            "wildberries", "ozon", "aliexpress", "lc waikiki", "zara", "h&m", "sulpak",
            "technodom", "mechta", "dns", "одежд", "обув", "магазин", "маркет", "market",
            "leroy", "ikea", "jysk", "kaspi магазин", "meloman", "marwin", "postomat"
        ),
        Category.HEALTH to listOf(
            "аптек", "europharm", "садыхан", "биосфера", "клиник", "медцентр", "стоматолог",
            "invivo", "olymp", "kdl", "больниц", "поликлиник", "оптика", "здоров",
            "фитнес", "тренаж", "спортзал", "бассейн"
        ),
        Category.ENTERTAINMENT to listOf(
            "кино", "kinopark", "chaplin", "cinema", "театр", "боулинг", "бильярд",
            "playstation", "steam", "игр", "spotify", "netflix", "yandex plus", "квест",
            "аттракцион", "парк развл", "зоопарк", "каток"
        ),
        Category.UTILITIES to listOf(
            "казахтелеком", "beeline", "activ", "kcell", "tele2", "altel", "izi",
            // "оси "/"кск " need the trailing boundary: without it they matched
            // inside "гОСномер"/"кСКолько" and sent bus tickets to Utilities.
            "коммунал", "кск ", "оси ", "электро", "энерг", "газ ", "водоканал", "алсеко",
            "ерц ", "интернет", "домофон", "мусор", "отоплен"
        ),
        Category.EDUCATION to listOf(
            "универ", "школ", "колледж", "курс", "обучен", "образован", "coursera",
            "udemy", "репетитор", "секци", "кружок"
        )
    )

    /** Keyword -> compiled matcher, built once. */
    private val COMPILED: List<Pair<Category, List<Regex>>> =
        RULES.map { (category, keywords) -> category to keywords.map(::compile) }

    /** Learned rules only match inside free text when the key is this long. */
    private const val MIN_TEXT_MATCH_LENGTH = 4

    private fun compile(keyword: String): Regex {
        val needsTrailingBoundary = keyword != keyword.trimEnd()
        val body = Regex.escape(keyword.trim())
        val tail = if (needsTrailingBoundary) "(?![\\p{L}\\p{N}])" else ""
        return Regex("(?<![\\p{L}\\p{N}])$body$tail", RegexOption.IGNORE_CASE)
    }

    /**
     * Returns the category, or null when the app can't tell and the user should
     * be asked: an unrecognized merchant, or an outgoing transfer with no learned
     * rule (those pay for anything — food, gifts, rent — so they need a human
     * pick the first time; after that the learned rule takes over).
     */
    fun classify(
        merchant: String?,
        rawText: String,
        type: TxType,
        learned: List<CategoryRule> = emptyList()
    ): Category? {
        matchLearned(merchant, rawText, learned)?.let { return it }

        if (type == TxType.TRANSFER_IN) return Category.INCOME
        if (type == TxType.TRANSFER_OUT) return null

        val haystack = listOfNotNull(merchant, rawText).joinToString(" ")
        COMPILED.forEach { (category, matchers) ->
            if (matchers.any { it.containsMatchIn(haystack) }) return category
        }
        return null
    }

    /**
     * A user-taught rule wins over everything else: exact merchant match first,
     * then the longest rule key that appears in the merchant/raw text (catches
     * notifications where the merchant field itself was never parsed out).
     */
    private fun matchLearned(
        merchant: String?,
        rawText: String,
        learned: List<CategoryRule>
    ): Category? {
        if (learned.isEmpty()) return null

        MerchantKey.of(merchant)?.let { key ->
            learned.firstOrNull { it.merchantKey == key }?.let { return it.category }
        }

        val haystack = MerchantKey.of(listOfNotNull(merchant, rawText).joinToString(" "))
            ?.let { " $it " }
            ?: return null
        return learned
            .filter {
                it.merchantKey.length >= MIN_TEXT_MATCH_LENGTH &&
                    haystack.contains(" ${it.merchantKey} ")
            }
            .maxByOrNull { it.merchantKey.length }
            ?.category
    }
}
