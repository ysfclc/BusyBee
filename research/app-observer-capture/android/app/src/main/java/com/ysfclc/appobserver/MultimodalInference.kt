package com.ysfclc.appobserver

import java.util.Locale
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

data class OcrLineEvidence(
    val text: String,
    val left: Int?,
    val top: Int?,
    val right: Int?,
    val bottom: Int?
)

data class VisualLabelEvidence(val label: String, val confidence: Float)

data class AccessibilityElementEvidence(
    val className: String,
    val resourceId: String,
    val region: NormalisedRegion?,
    val clickable: Boolean,
    val scrollable: Boolean,
    val editable: Boolean
)

data class LearningEvidence(
    val packageName: String,
    val screenshotId: String,
    val timestampEpochMs: Long,
    val width: Int,
    val height: Int,
    val lines: List<OcrLineEvidence>,
    val visualLabels: List<VisualLabelEvidence>,
    val accessibilityClasses: List<String>,
    val resourceIds: List<String>,
    val accessibilityElements: List<AccessibilityElementEvidence> = emptyList(),
    val clickableCount: Int,
    val scrollableCount: Int,
    val editableCount: Int,
    val interactionType: String,
    val notificationCategories: List<String>,
    val notificationCount: Int,
    val networkPacketCount: Int,
    val networkBytes: Long,
    val networkProtocols: List<String>,
    val networkPorts: List<Int>,
    val authorisedText: List<String>
)

data class NormalisedRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class ConceptHypothesis(
    val concept: String,
    val confidence: Float,
    val score: Float,
    val sources: List<String>,
    val region: NormalisedRegion?
)

object MultimodalInference {
    val concepts = listOf(
        "chat_conversation", "price_or_fare", "map_view", "route_summary",
        "service_offer", "trip_status", "confirmation_result", "search_interface",
        "list_or_feed", "form_or_input", "authentication", "payment",
        "notification_surface", "profile_or_account"
    )

    private val phrases = mapOf(
        "chat_conversation" to listOf(
            "chat", "message", "messages", "send message", "typing", "conversation",
            "wiadomość", "wiadomości", "rozmowa", "czat", "wyślij", "pisze",
            "mesaj", "mesajlar", "sohbet", "gönder"
        ),
        "price_or_fare" to listOf(
            "price", "total", "subtotal", "fare", "earnings", "your earnings",
            "cena", "kwota", "stawka", "zarobek", "łącznie", "suma",
            "fiyat", "ücret", "kazanç", "toplam", "tutar"
        ),
        "map_view" to listOf("map", "mapa", "harita", "google maps", "street view"),
        "route_summary" to listOf(
            "route", "directions", "distance", "estimated arrival", "arrival time",
            "trasa", "dojazd", "odległość", "czas przyjazdu",
            "rota", "mesafe", "tahmini varış", "navigacja", "navigation"
        ),
        "service_offer" to listOf(
            "accept", "accepted", "new ride", "new order", "offer", "request",
            "pickup", "odbierz", "przyjmij", "nowy kurs", "nowe zlecenie",
            "kabul et", "yeni yolculuk", "teklif", "istek"
        ),
        "trip_status" to listOf(
            "on the way", "arrived", "picked up", "in progress", "drop off",
            "completed", "trip started", "trip ended", "w drodze", "dojechałeś",
            "pasażer odebrany", "zakończono", "w trakcie", "yolculuk başladı",
            "yolcu alındı", "tamamlandı", "varış"
        ),
        "confirmation_result" to listOf(
            "confirmed", "confirmation", "success", "successful", "completed",
            "done", "saved", "cancelled", "canceled", "potwierdzono", "potwierdź",
            "sukces", "zapisano", "gotowe", "anulowano",
            "onaylandı", "başarılı", "kaydedildi", "iptal edildi", "tamam"
        ),
        "search_interface" to listOf(
            "search", "find", "filter", "szukaj", "wyszukaj", "filtruj",
            "ara", "bul", "filtrele"
        ),
        "list_or_feed" to listOf(
            "recent", "history", "recommended", "latest", "nearby",
            "ostatnie", "historia", "polecane", "w pobliżu", "son", "geçmiş", "yakınımda"
        ),
        "form_or_input" to listOf(
            "enter", "submit", "continue", "next", "required", "wprowadź",
            "dalej", "wyślij", "devam et", "giriniz", "gerekli"
        ),
        "authentication" to listOf(
            "sign in", "log in", "login", "password", "verification code",
            "zaloguj", "hasło", "kod weryfikacyjny",
            "giriş yap", "şifre", "doğrulama kodu"
        ),
        "payment" to listOf(
            "payment", "payout", "card", "bank account", "withdraw",
            "płatność", "wypłata", "karta", "konto bankowe",
            "ödeme", "banka hesabı", "para çek"
        ),
        "notification_surface" to listOf(
            "notifications", "notification", "powiadomienia", "bildirimler", "bildirim"
        ),
        "profile_or_account" to listOf(
            "profile", "account", "settings", "ustawienia", "konto", "profil",
            "hesap", "profilim", "ayarlar"
        )
    )

    private val moneyPattern = Regex(
        """(?i)(?:\d{1,3}(?:[ .]\d{3})*(?:[,.]\d{1,2})?|\d+(?:[,.]\d{1,2})?)\s*(?:zł|pln|eur|€|usd|\$|gbp|£)|(?:zł|pln|eur|€|usd|\$|gbp|£)\s*\d+(?:[,.]\d{1,2})?"""
    )

    fun classify(evidence: LearningEvidence): List<ConceptHypothesis> {
        data class MutableCandidate(
            var score: Float = 0f,
            val sources: MutableSet<String> = linkedSetOf(),
            val lines: MutableList<OcrLineEvidence> = mutableListOf()
        )
        val candidates = concepts.associateWith { MutableCandidate() }
        val allText = (evidence.lines.map { it.text } + evidence.authorisedText)
            .joinToString(" ").lowercase(Locale.ROOT)
        val normalised = allText.replace('ł', 'l').replace('ą', 'a').replace('ę', 'e')
            .replace('ś', 's').replace('ć', 'c').replace('ż', 'z').replace('ź', 'z')
            .replace('ń', 'n').replace('ó', 'o').replace('ı', 'i').replace('ş', 's')
            .replace('ğ', 'g').replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')

        phrases.forEach { (concept, terms) ->
            val matches = terms.filter { term ->
                containsTerm(normalised, term)
            }
            if (matches.isNotEmpty()) {
                val candidate = candidates.getValue(concept)
                candidate.score += min(2.6f, matches.distinct().size * 0.9f)
                candidate.sources += "on_device_ocr"
                evidence.lines.filter { line ->
                    val lineText = normalise(line.text)
                    matches.any { containsTerm(lineText, it) }
                }.forEach(candidate.lines::add)
            }
        }

        if (moneyPattern.containsMatchIn(allText)) {
            candidates.getValue("price_or_fare").apply {
                score += 2.0f
                sources += "ocr_currency_pattern"
                evidence.lines.filter { moneyPattern.containsMatchIn(it.text) }.forEach(lines::add)
            }
        }

        evidence.visualLabels.filter { it.confidence >= 0.42f }.forEach { label ->
            val name = label.label.lowercase(Locale.ROOT)
            val mapped = when {
                name.contains("map") || name.contains("road") || name.contains("street") ||
                    name.contains("navigation") -> "map_view"
                name.contains("vehicle") || name.contains("car") || name.contains("transport") -> "service_offer"
                name.contains("receipt") || name.contains("money") || name.contains("cash") -> "price_or_fare"
                name.contains("chat") || name.contains("conversation") -> "chat_conversation"
                name.contains("document") || name.contains("form") -> "form_or_input"
                name.contains("payment") || name.contains("credit card") -> "payment"
                else -> null
            }
            if (mapped != null) candidates.getValue(mapped).apply {
                score += 0.45f + label.confidence.coerceIn(0f, 1f) * 0.55f
                sources += "on_device_image_labeling"
            }
        }

        val classes = evidence.accessibilityClasses.joinToString(" ").lowercase(Locale.ROOT)
        val ids = evidence.resourceIds.joinToString(" ").lowercase(Locale.ROOT)
        fun structural(concept: String, matched: Boolean, weight: Float, reason: String) {
            if (matched) candidates.getValue(concept).apply {
                score += weight
                sources += reason
            }
        }
        structural("chat_conversation",
            (classes.contains("recyclerview") || classes.contains("listview") ||
                classes.contains("lazycolumn")) && evidence.editableCount > 0, 1.25f, "accessibility_structure")
        structural("map_view",
            classes.contains("mapview") || classes.contains("mapfragment") ||
                ids.contains("map") || ids.contains("navigation_map"), 1.8f, "accessibility_structure")
        structural("search_interface",
            ids.contains("search") || ids.contains("query") ||
                (evidence.editableCount > 0 && ids.contains("search")), 1.2f, "accessibility_resource_ids")
        structural("form_or_input", evidence.editableCount >= 2, 0.9f, "accessibility_structure")
        structural("list_or_feed",
            (classes.contains("recyclerview") || classes.contains("listview") ||
                classes.contains("lazycolumn")) && evidence.scrollableCount > 0, 1.0f, "accessibility_structure")
        structural("service_offer",
            ids.contains("accept") || ids.contains("pickup") || ids.contains("offer") ||
                ids.contains("request"), 1.4f, "accessibility_resource_ids")
        structural("price_or_fare",
            ids.contains("price") || ids.contains("fare") || ids.contains("amount") ||
                ids.contains("earning"), 1.3f, "accessibility_resource_ids")
        structural("confirmation_result",
            ids.contains("success") || ids.contains("confirmation") || ids.contains("completed"), 1.4f,
            "accessibility_resource_ids")

        val categories = evidence.notificationCategories.map { it.lowercase(Locale.ROOT) }
        if (categories.any { it.contains("message") || it.contains("msg") }) {
            candidates.getValue("chat_conversation").apply {
                score += 1.65f
                sources += "notification_listener_metadata"
            }
        }
        if (evidence.notificationCount > 0) {
            candidates.getValue("notification_surface").apply {
                score += 0.65f + min(1f, evidence.notificationCount / 4f)
                sources += "notification_listener_metadata"
            }
        }
        // Network and interaction signals corroborate concepts supported by visual/UI evidence;
        // they must never invent a semantic category on their own.
        if (evidence.networkPacketCount >= 3) {
            candidates.values.filter { it.score > 0f }.forEach {
                it.score += 0.18f
                it.sources += "correlated_network_activity"
            }
        }
        if (evidence.interactionType.contains("clicked") || evidence.interactionType.contains("selected") ||
            evidence.interactionType.contains("scrolled")) {
            candidates.values.filter { it.score > 0f }.forEach {
                it.score += 0.10f
                it.sources += "interaction_timeline"
            }
        }

        return candidates.mapNotNull { (concept, candidate) ->
            if (candidate.score <= 0f) return@mapNotNull null
            val coverage = candidate.sources.size
            val base = (1f - exp(-candidate.score * 0.26f))
            val corroboration = 1f + min(3, max(0, coverage - 1)) * 0.075f
            val confidence = (base * corroboration).coerceIn(0.05f, 0.97f)
            val region = mergeRegion(candidate.lines, evidence.width, evidence.height)
                ?: structuralRegion(concept, evidence.accessibilityElements)
            ConceptHypothesis(
                concept, confidence, candidate.score,
                candidate.sources.sorted(), region
            )
        }.filter { it.confidence >= 0.22f }
            .sortedByDescending { it.confidence }
            .take(8)
    }

    /** A privacy-safe feature vector: no arbitrary OCR words or personal text are retained. */
    fun featureVector(evidence: LearningEvidence): List<String> {
        val text = (evidence.lines.map { it.text } + evidence.authorisedText)
            .joinToString(" ").lowercase(Locale.ROOT)
        val normalised = normalise(text)
        val features = linkedSetOf<String>()
        phrases.forEach { (concept, terms) ->
            if (terms.any { containsTerm(normalised, it) }) features += "ocr:$concept"
        }
        if (moneyPattern.containsMatchIn(text)) features += "ocr:currency_amount"
        if (Regex("""\b\d{1,2}[:.]\d{2}\b""").containsMatchIn(text)) features += "ocr:time_value"
        val positionedLines = evidence.lines.filter {
            it.top != null && it.bottom != null && it.left != null && it.right != null
        }
        if (positionedLines.size >= 12) features += "layout:dense_text"
        if (positionedLines.size >= 5) features += "layout:multi_line_content"
        val topText = positionedLines.count {
            ((it.top!! + it.bottom!!) / 2f) < evidence.height * 0.18f
        }
        val bottomText = positionedLines.count {
            ((it.top!! + it.bottom!!) / 2f) > evidence.height * 0.82f
        }
        if (topText > 0) features += "layout:top_text"
        if (bottomText > 0) features += "layout:bottom_text"
        val currencyLine = positionedLines.firstOrNull { moneyPattern.containsMatchIn(it.text) }
        if (currencyLine != null && evidence.height > 0) {
            val centre = (currencyLine.top!! + currencyLine.bottom!!) / 2f / evidence.height
            features += when {
                centre < 0.30f -> "layout:currency_top"
                centre > 0.70f -> "layout:currency_bottom"
                else -> "layout:currency_centre"
            }
        }
        if (positionedLines.size >= 4) {
            val centres = positionedLines.map { (it.top!! + it.bottom!!) / 2f }.sorted()
            val gaps = centres.zipWithNext { first, second -> second - first }.filter { it > 0f }
            if (gaps.size >= 3) {
                val avg = gaps.average().toFloat()
                val regular = gaps.count { kotlin.math.abs(it - avg) <= max(4f, avg * 0.30f) }
                if (regular >= 3) features += "layout:repeated_rows"
            }
        }
        if (evidence.visualLabels.any { it.confidence >= 0.42f &&
                (it.label.contains("map", true) || it.label.contains("road", true)) }) {
            features += "vision:map_or_road"
        }
        if (evidence.visualLabels.any { it.confidence >= 0.42f &&
                (it.label.contains("vehicle", true) || it.label.contains("car", true)) }) {
            features += "vision:vehicle"
        }
        val classes = evidence.accessibilityClasses.joinToString(" ").lowercase(Locale.ROOT)
        val ids = evidence.resourceIds.joinToString(" ").lowercase(Locale.ROOT)
        if (classes.contains("recyclerview") || classes.contains("listview") || classes.contains("lazycolumn")) {
            features += "ui:repeating_list"
        }
        if (classes.contains("mapview") || ids.contains("map")) features += "ui:map_control"
        if (evidence.editableCount > 0) features += "ui:editable_control"
        if (evidence.clickableCount >= 3) features += "ui:clickable_dense"
        if (evidence.scrollableCount > 0) features += "ui:scrollable"
        evidence.accessibilityElements.forEach { element ->
            val id = element.resourceId.lowercase(Locale.ROOT)
            val className = element.className.lowercase(Locale.ROOT)
            if (className.contains("map") || id.contains("map")) features += "ui:map_region"
            if (listOf("price", "fare", "amount", "earning", "total", "cost").any(id::contains)) {
                features += "ui:price_region"
            }
            if (element.clickable && listOf("accept", "offer", "request", "pickup", "confirm").any(id::contains)) {
                features += "ui:action_control"
            }
            if (element.editable) features += "ui:input_region"
            if (element.scrollable) features += "ui:scroll_region"
        }
        if (evidence.interactionType.contains("clicked")) features += "event:click"
        if (evidence.interactionType.contains("scrolled")) features += "event:scroll"
        if (evidence.notificationCategories.any { it.contains("message", true) || it.contains("msg", true) }) {
            features += "notification:message"
        }
        if (evidence.notificationCount > 0) features += "notification:present"
        if (evidence.networkPacketCount >= 3) features += "network:burst"
        if (evidence.networkProtocols.any { it.equals("tcp", true) }) features += "network:tcp"
        if (evidence.networkPorts.any { it == 443 || it == 80 }) features += "network:web_port"
        return features.toList().sorted().take(48)
    }

    fun extractEntities(evidence: LearningEvidence): List<Pair<String, NormalisedRegion?>> {
        val result = linkedMapOf<String, NormalisedRegion?>()
        evidence.lines.forEach { line ->
            val matches = moneyPattern.findAll(line.text)
            matches.forEach { match ->
                val amount = match.value.replace(Regex("\\s+"), " ").trim().take(32)
                result.putIfAbsent(amount, lineRegion(line, evidence.width, evidence.height))
            }
        }
        return result.entries.take(12).map { it.key to it.value }
    }

    private fun structuralRegion(
        concept: String,
        elements: List<AccessibilityElementEvidence>
    ): NormalisedRegion? {
        val selected = elements.filter { element ->
            val className = element.className.lowercase(Locale.ROOT)
            val id = element.resourceId.lowercase(Locale.ROOT)
            when (concept) {
                "map_view" -> className.contains("map") || id.contains("map") ||
                    id.contains("navigation_canvas")
                "price_or_fare" -> listOf("price", "fare", "amount", "earning", "total", "cost")
                    .any(id::contains)
                "chat_conversation" -> className.contains("recyclerview") ||
                    className.contains("listview") || id.contains("conversation") || id.contains("message_list")
                "service_offer" -> element.clickable &&
                    listOf("accept", "offer", "request", "pickup", "confirm").any(id::contains)
                "search_interface" -> element.editable ||
                    id.contains("search") || id.contains("query")
                "form_or_input", "authentication" -> element.editable
                "list_or_feed" -> element.scrollable ||
                    className.contains("recyclerview") || className.contains("listview")
                "confirmation_result" -> listOf("success", "confirmation", "completed", "result")
                    .any(id::contains)
                "route_summary" -> listOf("route", "distance", "duration", "arrival", "eta")
                    .any(id::contains)
                "payment" -> listOf("payment", "payout", "card", "bank", "wallet")
                    .any(id::contains)
                else -> false
            }
        }.mapNotNull { it.region }
        if (selected.isEmpty()) return null
        return NormalisedRegion(
            selected.minOf { it.left }.coerceIn(0f, 1f),
            selected.minOf { it.top }.coerceIn(0f, 1f),
            selected.maxOf { it.right }.coerceIn(0f, 1f),
            selected.maxOf { it.bottom }.coerceIn(0f, 1f)
        )
    }

    private fun mergeRegion(lines: List<OcrLineEvidence>, width: Int, height: Int): NormalisedRegion? {
        if (lines.isEmpty() || width <= 0 || height <= 0) return null
        val boxes = lines.filter { it.left != null && it.top != null && it.right != null && it.bottom != null }
        if (boxes.isEmpty()) return null
        return NormalisedRegion(
            (boxes.minOf { it.left!! } / width.toFloat()).coerceIn(0f, 1f),
            (boxes.minOf { it.top!! } / height.toFloat()).coerceIn(0f, 1f),
            (boxes.maxOf { it.right!! } / width.toFloat()).coerceIn(0f, 1f),
            (boxes.maxOf { it.bottom!! } / height.toFloat()).coerceIn(0f, 1f)
        )
    }

    private fun lineRegion(line: OcrLineEvidence, width: Int, height: Int): NormalisedRegion? {
        if (width <= 0 || height <= 0 || line.left == null || line.top == null ||
            line.right == null || line.bottom == null) return null
        return NormalisedRegion(
            (line.left / width.toFloat()).coerceIn(0f, 1f),
            (line.top / height.toFloat()).coerceIn(0f, 1f),
            (line.right / width.toFloat()).coerceIn(0f, 1f),
            (line.bottom / height.toFloat()).coerceIn(0f, 1f)
        )
    }

    private fun containsTerm(normalisedText: String, term: String): Boolean {
        val phrase = normalise(term)
        if (phrase.isBlank()) return false
        val pattern = Regex("(?<![\\\\p{L}\\\\p{N}])" + Regex.escape(phrase) +
            "(?![\\\\p{L}\\\\p{N}])")
        return pattern.containsMatchIn(normalisedText)
    }

    private fun normalise(value: String): String = value.lowercase(Locale.ROOT)
        .replace('ł', 'l').replace('ą', 'a').replace('ę', 'e')
        .replace('ś', 's').replace('ć', 'c').replace('ż', 'z')
        .replace('ź', 'z').replace('ń', 'n').replace('ó', 'o')
        .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
        .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')

    private fun normalise(value: CharSequence): String = normalise(value.toString())
}
