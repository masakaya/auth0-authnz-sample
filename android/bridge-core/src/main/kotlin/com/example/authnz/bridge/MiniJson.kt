package com.example.authnz.bridge

/**
 * Reader and writer for the flat JSON documents the bridge exchanges.
 *
 * The bridge contract only needs objects whose members are strings, numbers and booleans, so a
 * small hand written codec is enough and keeps this module free of third party dependencies. That
 * matters because the same classes are compiled into the Android application, where an extra JSON
 * runtime would either clash with the platform one or drag in a Kotlin version of its own.
 *
 * Every reader entry point reports malformed input by returning `null`; nothing here throws at the
 * caller, because the bridge must stay silent on input it cannot understand.
 */
internal object MiniJson {

    /** Returns the members of a JSON object, or `null` when [text] is not exactly one object. */
    fun parseObject(text: String): Map<String, Any?>? = try {
        val reader = JsonReader(text)
        reader.skipWhitespace()
        val members = reader.readObject()
        reader.skipWhitespace()
        if (reader.atEnd()) members else null
    } catch (_: MalformedJson) {
        null
    }

    /** Serialises [members] in the given order. Values may be null, String, Boolean, Int or Long. */
    fun writeObject(members: List<Pair<String, Any?>>): String = buildString {
        append('{')
        members.forEachIndexed { index, (name, value) ->
            if (index > 0) append(',')
            writeString(name)
            append(':')
            writeValue(value)
        }
        append('}')
    }

    private fun StringBuilder.writeValue(value: Any?) {
        when (value) {
            null -> append("null")
            is String -> writeString(value)
            is Boolean -> append(if (value) "true" else "false")
            is Int -> append(value.toString())
            is Long -> append(value.toString())
            else -> throw IllegalArgumentException("unsupported JSON value type: ${value::class.java.name}")
        }
    }

    private fun StringBuilder.writeString(value: String) {
        append('"')
        for (character in value) {
            when {
                character == '"' -> append("\\\"")
                character == '\\' -> append("\\\\")
                character == '\n' -> append("\\n")
                character == '\r' -> append("\\r")
                character == '\t' -> append("\\t")
                character < ' ' || character == ' ' || character == ' ' ->
                    append("\\u").append(character.code.toString(16).padStart(4, '0'))
                else -> append(character)
            }
        }
        append('"')
    }
}

/** Signals malformed input. Never escapes [MiniJson]. */
private class MalformedJson : Exception(null, null, false, false)

private class JsonReader(private val text: String) {

    private var index = 0

    fun atEnd(): Boolean = index >= text.length

    fun skipWhitespace() {
        while (index < text.length) {
            val character = text[index]
            if (character == ' ' || character == '\t' || character == '\n' || character == '\r') {
                index++
            } else {
                return
            }
        }
    }

    fun readObject(): Map<String, Any?> {
        expect('{')
        val members = LinkedHashMap<String, Any?>()
        skipWhitespace()
        if (peek() == '}') {
            index++
            return members
        }
        while (true) {
            skipWhitespace()
            val name = readString()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            members[name] = readValue()
            skipWhitespace()
            when (read()) {
                ',' -> Unit
                '}' -> return members
                else -> throw MalformedJson()
            }
        }
    }

    private fun readArray(): List<Any?> {
        expect('[')
        val elements = ArrayList<Any?>()
        skipWhitespace()
        if (peek() == ']') {
            index++
            return elements
        }
        while (true) {
            skipWhitespace()
            elements.add(readValue())
            skipWhitespace()
            when (read()) {
                ',' -> Unit
                ']' -> return elements
                else -> throw MalformedJson()
            }
        }
    }

    private fun readValue(): Any? = when (val character = peek()) {
        '{' -> readObject()
        '[' -> readArray()
        '"' -> readString()
        't' -> readLiteral("true", true)
        'f' -> readLiteral("false", false)
        'n' -> readLiteral("null", null)
        else -> if (character == '-' || character in '0'..'9') readNumber() else throw MalformedJson()
    }

    private fun readLiteral(literal: String, value: Any?): Any? {
        if (!text.startsWith(literal, index)) throw MalformedJson()
        index += literal.length
        return value
    }

    private fun readNumber(): Any {
        val start = index
        if (peek() == '-') index++
        var digits = 0
        while (index < text.length && text[index] in '0'..'9') {
            index++
            digits++
        }
        if (digits == 0) throw MalformedJson()
        var fractional = false
        if (index < text.length && text[index] == '.') {
            fractional = true
            index++
            var fractionDigits = 0
            while (index < text.length && text[index] in '0'..'9') {
                index++
                fractionDigits++
            }
            if (fractionDigits == 0) throw MalformedJson()
        }
        if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
            fractional = true
            index++
            if (index < text.length && (text[index] == '+' || text[index] == '-')) index++
            var exponentDigits = 0
            while (index < text.length && text[index] in '0'..'9') {
                index++
                exponentDigits++
            }
            if (exponentDigits == 0) throw MalformedJson()
        }
        val literal = text.substring(start, index)
        return if (fractional) {
            literal.toDoubleOrNull() ?: throw MalformedJson()
        } else {
            literal.toLongOrNull() ?: literal.toDoubleOrNull() ?: throw MalformedJson()
        }
    }

    private fun readString(): String {
        expect('"')
        val builder = StringBuilder()
        while (true) {
            val character = read()
            when {
                character == '"' -> return builder.toString()
                character == '\\' -> builder.append(readEscape())
                character < ' ' -> throw MalformedJson()
                else -> builder.append(character)
            }
        }
    }

    private fun readEscape(): Char = when (val character = read()) {
        '"' -> '"'
        '\\' -> '\\'
        '/' -> '/'
        'b' -> '\b'
        'f' -> '\u000C'
        'n' -> '\n'
        'r' -> '\r'
        't' -> '\t'
        'u' -> {
            if (index + 4 > text.length) throw MalformedJson()
            val code = text.substring(index, index + 4).toIntOrNull(16) ?: throw MalformedJson()
            index += 4
            code.toChar()
        }
        else -> throw MalformedJson()
    }

    private fun peek(): Char = if (index < text.length) text[index] else throw MalformedJson()

    private fun read(): Char = peek().also { index++ }

    private fun expect(character: Char) {
        if (read() != character) throw MalformedJson()
    }
}
