package com.example

import java.text.Normalizer
import java.util.regex.Pattern

object GridExcelHeaderNormalizer {

    private val DIACRITICS_PATTERN: Pattern =
        Pattern.compile("\\p{InCombiningDiacriticalMarks}+")

    fun removeBomAndTrim(text: String?): String {
        if (text == null) return ""
        return text.replace("\uFEFF", "")
            .replace("\u200B", "")
            .trim()
    }

    fun normalizeHeader(rawHeader: String?): String {
        val clean = removeBomAndTrim(rawHeader)
        if (clean.isEmpty()) return ""

        // Normalize unicode NFC/NFD
        val nfd = Normalizer.normalize(clean, Normalizer.Form.NFD)
        val withoutDiacritics = DIACRITICS_PATTERN.matcher(nfd).replaceAll("")
            .replace('đ', 'd')
            .replace('Đ', 'd')

        // Lowercase and collapse multiple spaces/underscores
        return withoutDiacritics.lowercase()
            .replace("[_\\-\\s]+".toRegex(), " ")
            .trim()
    }

    fun findColumnIndex(headers: Map<String, Int>, vararg aliases: String): Int? {
        for (alias in aliases) {
            val normalizedAlias = normalizeHeader(alias)
            headers[normalizedAlias]?.let { return it }
        }
        return null
    }

    fun detectFileType(headerMap: Map<String, Int>, defaultTypeHint: GridExcelFileType? = null): GridExcelFileType {
        val normalizedKeys = headerMap.keys

        val hasSubstationCode = findColumnIndex(headerMap, "ma tram", "matram", "substation_code", "substation code") != null
        val hasSubstationName = findColumnIndex(headerMap, "ten tram", "tentram", "substation_name", "substation name") != null
        val hasFeederCode = findColumnIndex(headerMap, "ma phat tuyen", "maphattuyen", "feeder_code", "feeder code") != null
        val hasFeederSubstationRef = findColumnIndex(headerMap, "tram 110kv", "tram110kv", "substation", "tram") != null
        val hasDeviceId = findColumnIndex(headerMap, "device_id", "device id", "ma thiet bi", "mathietbi") != null

        if (hasDeviceId && hasFeederCode) {
            // It's a Device file (LBS or REC)
            if (defaultTypeHint == GridExcelFileType.LBS || defaultTypeHint == GridExcelFileType.REC) {
                return defaultTypeHint
            }
            return GridExcelFileType.LBS // will be refined by device_type column in parser
        }

        if (hasFeederCode && (hasFeederSubstationRef || hasSubstationName || hasSubstationCode)) {
            return GridExcelFileType.FEEDER
        }

        if (hasSubstationCode && hasSubstationName) {
            return GridExcelFileType.SUBSTATION
        }

        return GridExcelFileType.UNKNOWN
    }
}
