package io.smartycoder.bignum.format

/**
 * Splits a formatted value so its tail -- the decimal, or a ride time's seconds -- can be drawn
 * small and raised. One rule covers both: take the last "." or ":", drop it, raise what follows.
 * The separator goes because the raised digits already say what they are, and its width is width
 * the number can have instead.
 *
 *     "34.9"    ->  "34"   + "9"
 *     "1:34:17" ->  "1:34" + "17"
 *     "34:56"   ->  "34"   + "56"
 *     "226"     ->  "226"  + ""
 */
object RaisedTail {

    // Both literal, which holds because every value here is formatted through
    // Formatters.fmt at Locale.US. Move that to the device locale and this breaks two ways at
    // once: a comma decimal stops splitting, and a grouping dot makes "1.234" render as "1" and
    // a raised "234".
    private const val SEPARATORS = ".:"

    fun split(text: String, raised: Boolean): Pair<String, String> {
        if (!raised) return text to ""
        val i = text.indexOfLast { it in SEPARATORS }
        // One branch for both misses: -1 is no separator at all, 0 is a separator with
        // nothing in front of it, and ".5" is no more a tail than "226" is.
        return if (i <= 0) text to "" else text.substring(0, i) to text.substring(i + 1)
    }

    /**
     * The same split applied to a field's width template, which is the budget the number is
     * scaled against.
     *
     * A decimal template needs care that a duration does not, and the asymmetry is the whole
     * reason this is not just [split]. [Formatters.compact] prints one decimal while it fits and
     * none once the value grows past it, so a distance field draws "99.9" and then "105" -- and a
     * budget of two full-size digits plus a raised one is too narrow for three full-size ones,
     * which would shrink the number at exactly the point it crosses 100 km. So a decimal template
     * budgets as its own digits at full size with the point dropped, which covers both forms.
     *
     * A duration has no such second form: [Formatters.time] always emits its colons, so every
     * value it produces splits, and the template budgets as that split. Dropping the colon here
     * too would look consistent and budget for a value the field never draws.
     */
    fun template(template: String, raised: Boolean): Pair<String, String> =
        if (raised && '.' in template) template.replace(".", "") to "" else split(template, raised)
}
