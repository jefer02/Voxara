package com.example.voxara.core.ai

/**
 * A minimal JSON reader/writer — just what the coach needs (objects, arrays, strings, numbers,
 * booleans, null). Pure Kotlin so the request/response contract is tested on the JVM; org.json is
 * not available in local unit tests.
 */
object MiniJson {

    // ------------------------------------------------------------------ writing

    fun write(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

    private fun append(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> quote(sb, v)
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v)
            is Double -> if (v.isFinite()) sb.append(if (v == Math.rint(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()) else sb.append("null")
            is Float -> append(sb, v.toDouble())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(',')
                    first = false
                    quote(sb, k.toString())
                    sb.append(':')
                    append(sb, value)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (e in v) {
                    if (!first) sb.append(',')
                    first = false
                    append(sb, e)
                }
                sb.append(']')
            }
            else -> quote(sb, v.toString())
        }
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    // ------------------------------------------------------------------ reading

    class ParseException(message: String) : Exception(message)

    /** Parses a JSON document: Map<String, Any?>, List<Any?>, String, Double, Boolean or null. */
    fun parse(text: String): Any? {
        val p = Parser(text)
        val v = p.value()
        p.skipWs()
        if (!p.done()) throw ParseException("trailing characters at ${p.i}")
        return v
    }

    private class Parser(val s: String) {
        var i = 0
        fun done() = i >= s.length
        fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            skipWs()
            if (done()) throw ParseException("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw ParseException("unexpected '$c' at $i")
            }
        }
        fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) throw ParseException("bad literal at $i")
            i += word.length
            return v
        }
        fun num(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDoubleOrNull() ?: throw ParseException("bad number at $start")
        }
        fun str(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (done()) throw ParseException("unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (done()) throw ParseException("bad escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw ParseException("bad unicode escape")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4
                            }
                            else -> throw ParseException("bad escape '$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun arr(): List<Any?> {
            i++
            val out = ArrayList<Any?>()
            skipWs()
            if (!done() && s[i] == ']') { i++; return out }
            while (true) {
                out += value()
                skipWs()
                if (done()) throw ParseException("unterminated array")
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return out
                    else -> throw ParseException("expected , or ] at ${i - 1}")
                }
            }
        }
        fun obj(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            skipWs()
            if (!done() && s[i] == '}') { i++; return out }
            while (true) {
                skipWs()
                if (done() || s[i] != '"') throw ParseException("expected key at $i")
                val k = str()
                skipWs()
                if (done() || s[i++] != ':') throw ParseException("expected : at ${i - 1}")
                out[k] = value()
                skipWs()
                if (done()) throw ParseException("unterminated object")
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return out
                    else -> throw ParseException("expected , or } at ${i - 1}")
                }
            }
        }
    }
}
