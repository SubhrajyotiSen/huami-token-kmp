package org.huamitoken

/**
 * Minimal JSON reader for the flat server responses this app consumes.
 * Handles objects, nested objects/arrays by balanced-bracket extraction,
 * strings with escapes (including \uXXXX), and numbers/booleans/null.
 */
object JsonLite {
    fun stringField(json: String, key: String): String? {
        val raw = rawValue(json, key) ?: return null
        return if (raw.startsWith("\"")) unescape(raw.substring(1, raw.length - 1)) else raw
    }

    fun longField(json: String, key: String): Long? {
        val raw = rawValue(json, key) ?: return null
        val v = if (raw.startsWith("\"")) unescape(raw.substring(1, raw.length - 1)) else raw
        return v.toLongOrNull()
    }

    fun objectField(json: String, key: String): String? = balancedValue(json, key, '{', '}')

    fun arrayField(json: String, key: String): String? = balancedValue(json, key, '[', ']')

    fun splitObjects(arrayJson: String): List<String> {
        val t = arrayJson.trim()
        require(t.startsWith("[") && t.endsWith("]"))
        val out = mutableListOf<String>()
        var depth = 0
        var inStr = false
        var esc = false
        var start = -1
        for (i in 1 until t.length - 1) {
            val c = t[i]
            if (inStr) {
                if (esc) esc = false else if (c == '\\') esc = true else if (c == '"') inStr = false
            } else {
                when (c) {
                    '"' -> inStr = true
                    '{' -> { if (depth == 0) start = i; depth++ }
                    '}' -> { depth--; if (depth == 0 && start >= 0) out.add(t.substring(start, i + 1)) }
                }
            }
        }
        return out
    }

    private fun keyIndex(json: String, key: String): Int {
        val needle = "\"$key\""
        var idx = json.indexOf(needle)
        while (idx >= 0) {
            var j = idx + needle.length
            while (j < json.length && json[j].isWhitespace()) j++
            if (j < json.length && json[j] == ':') return j + 1
            idx = json.indexOf(needle, idx + 1)
        }
        return -1
    }

    private fun rawValue(json: String, key: String): String? {
        var i = keyIndex(json, key)
        if (i < 0) return null
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length) return null
        if (json[i] == '"') {
            var j = i + 1
            var esc = false
            while (j < json.length) {
                val c = json[j]
                if (esc) esc = false else if (c == '\\') esc = true else if (c == '"') break
                j++
            }
            if (j >= json.length) return null
            return json.substring(i, j + 1)
        }
        var j = i
        while (j < json.length && json[j] != ',' && json[j] != '}' && json[j] != ']') j++
        return json.substring(i, j).trim().ifEmpty { null }
    }

    private fun balancedValue(json: String, key: String, open: Char, close: Char): String? {
        var i = keyIndex(json, key)
        if (i < 0) return null
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i] != open) return null
        var depth = 0
        var inStr = false
        var esc = false
        for (j in i until json.length) {
            val c = json[j]
            if (inStr) {
                if (esc) esc = false else if (c == '\\') esc = true else if (c == '"') inStr = false
            } else {
                when (c) {
                    '"' -> inStr = true
                    open -> depth++
                    close -> {
                        depth--
                        if (depth == 0) return json.substring(i, j + 1)
                    }
                }
            }
        }
        return null
    }

    fun unescape(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\' || i + 1 >= s.length) {
                sb.append(c); i++
            } else {
                when (s[i + 1]) {
                    '"', '\\', '/' -> { sb.append(s[i + 1]); i += 2 }
                    'b' -> { sb.append('\b'); i += 2 }
                    'f' -> { sb.append(''); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'u' -> {
                        val hex = s.substring(i + 2, i + 6)
                        sb.append(hex.toInt(16).toChar()); i += 6
                    }
                    else -> { sb.append(c); i++ }
                }
            }
        }
        return sb.toString()
    }
}
