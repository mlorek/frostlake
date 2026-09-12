/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Array HOLES, the empty document and the lone decimal point — three places PARSE_JSON reads a document a
 * strict parser will not.
 *
 * <p>★ A HOLE IS AN {@code undefined} ELEMENT, not a refusal. The array keeps its shape around the
 * elision, so the element after a hole is untouched, and a hole reads back as SQL NULL exactly like the
 * one ARRAY_CONSTRUCT(1,NULL,2) makes.
 *
 * <p>★ THE TRAILING COMMA GROWS THE ARRAY. This is the cell that matters beyond the reader: Frostlake
 * already ACCEPTED {@code [1,2,]} and answered a TWO-element array, so the fix is a stored value changing
 * length rather than a refusal becoming an answer. Live counts three.
 *
 * <p>★ AN ARRAY OF ONLY COMMAS COUNTS ONE ELEMENT PER COMMA. {@code [,]} is ONE element, not two, which
 * is what separates the closing bracket's hole from the comma's: the bracket only closes a hole where the
 * array has held a real element.
 *
 * <p>★ AN EMPTY DOCUMENT IS SQL NULL, not the JSON null — IS NULL is true and TYPEOF is SQL NULL, where
 * PARSE_JSON('null') answers false and 'NULL_VALUE'.
 *
 * <p>An OBJECT has no holes: both engines refuse a misplaced comma, and both forgive a trailing one.
 */
public class JsonArrayHoleTest extends BaseDatabaseTest {

    /** The one-line rendering of a parsed document. */
    private String json(final String document) {
        final ResultSet rs = engine.executeQuery("SELECT TO_JSON(PARSE_JSON('" + document + "'))");
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** How many elements the parsed array holds. */
    private String size(final String document) {
        final ResultSet rs = engine.executeQuery("SELECT ARRAY_SIZE(PARSE_JSON('" + document + "'))");
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** A yes/no answer, which spells the same on both engines where a boolean does not. */
    private String yesNo(final String predicate) {
        final ResultSet rs = engine.executeQuery("SELECT IFF(" + predicate + ", 'yes', 'no')");
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    @Test
    void middleHoleIsAnUndefinedElement() {
        assertEquals("[1,undefined,2]", json("[1,,2]"));
        assertEquals("3", size("[1,,2]"));
    }

    @Test
    void doubledHoleCountsTwice() {
        assertEquals("[1,undefined,undefined,2]", json("[1,,,2]"));
        assertEquals("4", size("[1,,,2]"));
    }

    @Test
    void leadingHoleIsAnElement() {
        assertEquals("[undefined,1]", json("[,1]"));
        assertEquals("2", size("[,1]"));
    }

    @Test
    void trailingCommaGrowsTheArray() {
        assertEquals("[1,2,undefined]", json("[1,2,]"));
        assertEquals("3", size("[1,2,]"));
    }

    @Test
    void anArrayOfOnlyCommasCountsOnePerComma() {
        assertEquals("[undefined]", json("[,]"));
        assertEquals("1", size("[,]"));
        assertEquals("[undefined,undefined]", json("[,,]"));
        assertEquals("2", size("[,,]"));
    }

    @Test
    void aNestedArrayKeepsItsOwnHoles() {
        assertEquals("[[1,undefined,2],3]", json("[[1,,2],3]"));
        assertEquals("2", size("[[1,,2],3]"));
    }

    @Test
    void aHoleReadsAsSqlNullAndLeavesItsNeighboursAlone() {
        assertEquals("yes", yesNo("GET(PARSE_JSON('[1,,2]'), 1) IS NULL"));
        assertEquals("yes", yesNo("TYPEOF(GET(PARSE_JSON('[1,,2]'), 1)) IS NULL"));
        final ResultSet rs = engine.executeQuery("SELECT GET(PARSE_JSON('[1,,2]'), 2)");
        rs.next();
        assertEquals("2", String.valueOf(rs.getValue(0)));
    }

    @Test
    void aHoleIsTheSameElementArrayConstructMakes() {
        assertEquals(json("[1,,2]"), json("[1,null,2]").replace("null", "undefined"));
        final ResultSet rs = engine.executeQuery(
            "SELECT IFF(TO_JSON(PARSE_JSON('[1,,2]')) = TO_JSON(ARRAY_CONSTRUCT(1, NULL, 2)), 'yes', 'no')");
        rs.next();
        assertEquals("yes", String.valueOf(rs.getValue(0)));
    }

    @Test
    void anArrayWithoutHolesIsUntouched() {
        assertEquals("[]", json("[]"));
        assertEquals("0", size("[]"));
        assertEquals("[1,2]", json("[1,2]"));
        assertEquals("2", size("[1,2]"));
        assertEquals("[1,null,2]", json("[1,null,2]"));
    }

    @Test
    void aStringHoldingCommasIsNotAHole() {
        assertEquals("[\"a,,b\"]", json("[\"a,,b\"]"));
        assertEquals("1", size("[\"a,,b\"]"));
    }

    @Test
    void anObjectHasNoHoles() {
        final RuntimeException valueless = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT PARSE_JSON('{\"a\":,\"b\":1}')");
            }
        });
        assertTrue(String.valueOf(valueless.getMessage()).contains("misplaced comma"),
            "expected a misplaced-comma refusal, got: " + valueless.getMessage());
        final RuntimeException keyless = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT PARSE_JSON('{,\"a\":1}')");
            }
        });
        assertTrue(String.valueOf(keyless.getMessage()).contains("misplaced comma"),
            "expected a misplaced-comma refusal, got: " + keyless.getMessage());
    }

    @Test
    void anObjectStillForgivesATrailingComma() {
        assertEquals("{\"a\":1}", json("{\"a\":1,}"));
    }

    @Test
    void anEmptyDocumentIsSqlNull() {
        assertEquals("yes", yesNo("PARSE_JSON('') IS NULL"));
        assertEquals("yes", yesNo("PARSE_JSON('   ') IS NULL"));
        assertEquals("yes", yesNo("TYPEOF(PARSE_JSON('')) IS NULL"));
        assertEquals("yes", yesNo("IS_NULL_VALUE(PARSE_JSON('')) IS NULL"));
    }

    @Test
    void theJsonNullIsStillDistinctFromSqlNull() {
        assertEquals("no", yesNo("PARSE_JSON('null') IS NULL"));
        assertEquals("yes", yesNo("TYPEOF(PARSE_JSON('null')) = 'NULL_VALUE'"));
        assertEquals("yes", yesNo("IS_NULL_VALUE(PARSE_JSON('null'))"));
    }

    @Test
    void aLoneDecimalPointIsZero() {
        final ResultSet rs = engine.executeQuery("SELECT PARSE_JSON('.')");
        rs.next();
        assertEquals("0", String.valueOf(rs.getValue(0)));
    }

    @Test
    void theLeadingDecimalPointStillReadsItsDigits() {
        final ResultSet rs = engine.executeQuery("SELECT PARSE_JSON('.5')");
        rs.next();
        assertEquals("0.5", String.valueOf(rs.getValue(0)));
    }

    @Test
    void aLoneMinusIsStillRefused() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT PARSE_JSON('-')");
            }
        });
    }
}
