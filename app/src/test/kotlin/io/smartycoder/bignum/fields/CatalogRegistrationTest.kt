package io.smartycoder.bignum.fields

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Every field in the catalogue must also be declared in `extension_info.xml`, in the same order.
 *
 * The Karoo builds its picker from that XML, not from [FieldCatalog], so a field missing there
 * compiles, streams, and simply never appears -- which is how sunrise and sunset first shipped
 * invisible. Two hand-kept lists that have to agree, and nothing but this holds them together.
 *
 * Read as source text rather than by building the catalogue: [FieldCatalog.build] wants a live
 * KarooSystemService, which a JVM test has no way to make.
 */
class CatalogRegistrationTest {

    private val src = File("").absoluteFile
        .let { if (it.name == "app") it else File(it, "app") }
        .let { File(it, "src/main") }

    private val fields = File(src, "kotlin/io/smartycoder/bignum/fields")

    /**
     * The catalogue's ids, in picker order. Most entries carry the id at the call site; the
     * ten single-purpose classes pass it to BaseNumericField themselves, so those are looked
     * up in the class named on the line.
     */
    private val catalogIds: List<String> =
        Regex("""^\s+(\w+)\(extension, (?:"(\w+)", )?karoo""", RegexOption.MULTILINE)
            .findAll(File(fields, "FieldCatalog.kt").readText())
            .map { m ->
                m.groupValues[2].ifEmpty {
                    val own = Regex(""": BaseNumericField\(extension, "(\w+)", karoo\)""")
                        .find(File(fields, "${m.groupValues[1]}.kt").readText())
                    requireNotNull(own) { "no typeId found in ${m.groupValues[1]}.kt" }.groupValues[1]
                }
            }
            .toList()

    private val declaredIds: List<String> =
        Regex("""typeId="(\w+)"""")
            .findAll(File(src, "res/xml/extension_info.xml").readText())
            .map { it.groupValues[1] }
            .toList()

    @Test fun `both lists were actually found, so an empty pass cannot pass for agreement`() {
        assert(catalogIds.size > 50) { "only found ${catalogIds.size} catalogue ids" }
        assert(declaredIds.size > 50) { "only found ${declaredIds.size} declared ids" }
    }

    @Test fun `the picker declares every catalogue field and nothing else`() {
        assertEquals("undeclared", emptyList<String>(), catalogIds - declaredIds.toSet())
        // "hud" is declared but composes the catalogue rather than living in it; see BigNumExtension.
        assertEquals("declared but absent from the catalogue", listOf("hud"), declaredIds - catalogIds.toSet())
    }

    @Test fun `the picker lists them in the catalogue's own order`() {
        assertEquals(catalogIds, declaredIds - "hud")
    }
}
