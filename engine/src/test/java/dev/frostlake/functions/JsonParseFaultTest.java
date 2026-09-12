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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What PARSE_JSON says when the document will not parse. Frostlake answered
 * "Invalid JSON: &lt;the whole document&gt;" for every fault alike, where live NAMES the fault and says
 * where the parser stands when it gives up.
 *
 * <p>THE POSITION IS 1-BASED and falls in one of two places, which is the part worth stating plainly
 * because it looks inconsistent until it is written down. A fault about a TOKEN THAT WAS READ reports
 * the index just PAST it: a five-character keyword is pos 6, and a document that simply ran out
 * reports the index past its last character. A fault about a CHARACTER THAT WAS NOT reports that
 * character's own index: a misplaced brace at index 4 is pos 5. A duplicate key reports its CLOSING
 * QUOTE, which is the first rule again — just past the key's content.
 *
 * <p>What follows a COMPLETE document decides between the two sentences that carry no position at
 * all: another object, array or string is "more than one document in the input", and anything else is
 * "garbage after valid input document". A document that opens with a comma is garbage as well, since
 * an empty prefix is itself a valid (null) document.
 *
 * <p>CHECK_JSON answers the same sentence WITHOUT the prefix, and NULL for a document that parses.
 *
 * <p>Not asserted here: the documents live ACCEPTS and Frostlake does not — unquoted keys, NaN,
 * a leading zero or plus, a bare leading or trailing dot — which are the reader's leniency rather
 * than its wording, and are tracked on their own.
 */
public class JsonParseFaultTest extends BaseDatabaseTest {

    /** The refusal PARSE_JSON raises over {@code document}, or the value it answers. */
    private String fault(final String document) {
        try {
            final ResultSet rs = engine.executeQuery("SELECT PARSE_JSON('" + document + "')");
            return "accepted " + (rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>");
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** A bare word that names no JSON value, reported just PAST the word. */
    @Test
    public void anUnknownKeywordIsNamedAndMeasured() {
        assertEquals("Error parsing JSON: unknown keyword \"cdefg\", pos 6", fault("cdefg"));
        assertEquals("Error parsing JSON: unknown keyword \"nul\", pos 4", fault("nul"));
        assertEquals("Error parsing JSON: unknown keyword \"tru\", pos 4", fault("tru"));
        assertEquals("Error parsing JSON: unknown keyword \"e\", pos 2", fault("e"));
        assertEquals("Error parsing JSON: unknown keyword \"a\", pos 2", fault("a:1"));
        assertEquals("Error parsing JSON: unknown keyword \"-a\", pos 3", fault("-a"),
            "the sign is part of the word once no digit follows it");
        assertEquals("Error parsing JSON: unknown keyword \"x\", pos 15",
            fault("{\"a\":[1,{\"b\":x}]}"), "and it is found at depth");
    }

    /** A closer where a value or a key belonged, reported at the closer's OWN index. */
    @Test
    public void aMisplacedCloserIsReportedWhereItStands() {
        assertEquals("Error parsing JSON: misplaced }, pos 1", fault("}"));
        assertEquals("Error parsing JSON: misplaced ], pos 1", fault("]"));
        assertEquals("Error parsing JSON: misplaced }, pos 5", fault("{bad}"));
        assertEquals("Error parsing JSON: misplaced }, pos 5", fault("{\"a\"}"));
        assertEquals("Error parsing JSON: misplaced }, pos 6", fault("{\"a\":}"));
        assertEquals("Error parsing JSON: misplaced }, pos 3", fault("[1}"));
        assertEquals("Error parsing JSON: misplaced ], pos 7", fault("{\"a\":1]"));
        assertEquals("Error parsing JSON: misplaced }, pos 11", fault("{\"a\":1,\"b\"}"));
        assertEquals("Error parsing JSON: misplaced comma, pos 8", fault("{\"a\":1,,}"));
    }

    /** A document that ran out names the container it ran out inside, at the index past its end. */
    @Test
    public void anIncompleteDocumentNamesItsContainer() {
        assertEquals("Error parsing JSON: incomplete object value, pos 2", fault("{"));
        assertEquals("Error parsing JSON: incomplete array value, pos 2", fault("["));
        assertEquals("Error parsing JSON: incomplete object value, pos 7", fault("{\"a\":1"));
        assertEquals("Error parsing JSON: incomplete array value, pos 5", fault("[1,2"));
        assertEquals("Error parsing JSON: incomplete array value, pos 10", fault("{\"a\":[1,2"),
            "the INNERMOST container is the one named");
        assertEquals("Error parsing JSON: unfinished string, pos 5", fault("\"abc"));
    }

    /** Two members, or a key and its value, that run on without their separator. */
    @Test
    public void aMissingSeparatorIsReportedAtTheTokenThatShouldHaveBeenOne() {
        assertEquals("Error parsing JSON: missing comma, pos 4", fault("[1 2]"));
        assertEquals("Error parsing JSON: missing comma, pos 6", fault("[1,2 3]"));
        assertEquals("Error parsing JSON: missing comma, pos 8", fault("{\"a\":1 \"b\":2}"));
        assertEquals("Error parsing JSON: missing colon, pos 6", fault("{\"a\" 1}"));
    }

    /** One object may not name a key twice — reported at the key's CLOSING QUOTE. */
    @Test
    public void oneObjectMayNotNameAKeyTwice() {
        assertEquals("Error parsing JSON: duplicate object attribute \"a\", pos 10",
            fault("{\"a\":1,\"a\":2}"));
        assertEquals("Error parsing JSON: duplicate object attribute \"abc\", pos 14",
            fault("{\"abc\":1,\"abc\":2}"));
        assertEquals("Error parsing JSON: duplicate object attribute \"a\", pos 10",
            fault("{\"a\":1,\"a\":2,\"a\":3}"), "the FIRST repeat is the one reported");
        assertEquals("accepted {\"a\":{\"a\":1}}", fault("{\"a\":{\"a\":1}}"),
            "a NESTED object may reuse the name");
        assertEquals("accepted [{\"a\":1},{\"a\":1}]", fault("[{\"a\":1},{\"a\":1}]"),
            "and so may a sibling");
    }

    /** A character JSON has no use for outside a string, reported where it stands. */
    @Test
    public void anInvalidCharacterIsQuotedAndPlaced() {
        assertEquals("Error parsing JSON: invalid character outside of a string: '#', pos 1",
            fault("#"));
        assertEquals("Error parsing JSON: invalid character outside of a string: '@', pos 1",
            fault("@1"));
        assertEquals("Error parsing JSON: invalid character outside of a string: '@', pos 2",
            fault("{@:1}"));
        assertEquals("Error parsing JSON: invalid character outside of a string: '@', pos 2",
            fault("[@]"));
        assertEquals("Error parsing JSON: invalid character outside of a string: '$', pos 2",
            fault("{$a$:1}"));
    }

    /** The number faults, each with its own sentence. */
    @Test
    public void aMalformedNumberNamesWhatIsWrongWithIt() {
        assertEquals("Error parsing JSON: no number after a sign, pos 2", fault("-"));
        assertEquals("Error parsing JSON: stray minus sign, pos 2", fault("--1"));
        assertEquals("Error parsing JSON: missing decimal exponent digits: '1e', pos 3", fault("1e"));
        assertEquals("Error parsing JSON: missing decimal exponent digits: '1e+', pos 4", fault("1e+"));
        assertEquals("Error parsing JSON: garbage in the numeric literal: 1a , pos 3", fault("1a"),
            "with a space before the comma, which is live's own spacing");
    }

    /** What FOLLOWS a complete document decides which of the two positionless sentences applies. */
    @Test
    public void whatFollowsACompleteDocumentDecidesTheSentence() {
        assertEquals("Error parsing JSON: more than one document in the input", fault("{} {}"));
        assertEquals("Error parsing JSON: more than one document in the input", fault("[1] [2]"));
        assertEquals("Error parsing JSON: more than one document in the input", fault("\"a\" \"b\""));
        assertEquals("Error parsing JSON: more than one document in the input", fault("1 {}"));
        assertEquals("Error parsing JSON: more than one document in the input", fault("{} {} {}"));
        assertEquals("Error parsing JSON: garbage after valid input document", fault("1 2"),
            "a NUMBER after one is garbage, where an object would be a second document");
        assertEquals("Error parsing JSON: garbage after valid input document", fault("{} 1"));
        assertEquals("Error parsing JSON: garbage after valid input document", fault("\"a\"x"));
        assertEquals("Error parsing JSON: garbage after valid input document", fault(",1"),
            "an empty prefix is a valid document, so a leading comma is already after one");
    }

    /** The documents that DO parse still parse, and the TRY_ form still swallows every fault. */
    @Test
    public void whatParsedBeforeParsesStill() {
        assertEquals("accepted {\"a\":1}", fault("{\"a\":1}"));
        assertEquals("accepted 1", fault("1"));
        assertEquals("accepted null", fault("null"));
        assertEquals("accepted {\"a\":1}", fault("{\"a\":1,}"), "a trailing comma is tolerated");
        final ResultSet tried = engine.executeQuery("SELECT TRY_PARSE_JSON('cdefg') IS NULL");
        tried.next();
        assertEquals("true", String.valueOf(tried.getValue(0)).toLowerCase());
    }

    /** CHECK_JSON answers the same sentence without the prefix, and NULL for a document that parses. */
    @Test
    public void checkJsonAnswersTheSameFaultWithoutThePrefix() {
        final ResultSet rs = engine.executeQuery(
            "SELECT CHECK_JSON('cdefg'), CHECK_JSON('{\"a\":1}') IS NULL, CHECK_JSON('{bad}')");
        rs.next();
        assertEquals("unknown keyword \"cdefg\", pos 6", String.valueOf(rs.getValue(0)));
        assertEquals("true", String.valueOf(rs.getValue(1)).toLowerCase());
        assertEquals("misplaced }, pos 5", String.valueOf(rs.getValue(2)));
    }
}
