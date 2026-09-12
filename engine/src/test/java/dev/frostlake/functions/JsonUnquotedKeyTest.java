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
 * An UNQUOTED attribute name, and how narrow Snowflake's rule for one is.
 *
 * <p>★ THE RULE IS A PLAIN ASCII IDENTIFIER — {@code [A-Za-z_][A-Za-z0-9_]*} — and nothing wider. This is
 * the whole reason the reader decides for itself instead of letting the parser do it: Jackson's unquoted
 * name follows JavaScript identifiers, which include {@code $}, so turning its flag on would read
 * {@code {a:1}} and {@code {$a$:1}} alike — and live REFUSES the second. Accepting a document live
 * rejects is the worse of the two errors, so the set is matched exactly.
 *
 * <p>★ A WORD IN NAME POSITION IS A NAME, whatever it spells. {@code {true:1}}, {@code {null:1}} and
 * {@code {nan:1}} are objects with those attribute names, not keywords — which also means the reader's
 * own placeholders can never reach an attribute name.
 *
 * <p>★ THE CASE IS KEPT. {@code {Ab:1}} answers to {@code :Ab}, so nothing folds on the way in.
 *
 * <p>NOT COVERED HERE: how live WORDS its refusal for a name it will not read. It has three sentences —
 * an invalid character, an attribute name that cannot be a number, and a missing colon — and Frostlake
 * matches only some of them. That is a refusal-vocabulary surface of its own, tracked separately.
 */
public class JsonUnquotedKeyTest extends BaseDatabaseTest {

    private String doc(final String text) {
        final ResultSet rs = engine.executeQuery("SELECT PARSE_JSON('" + text + "')");
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    private void refuses(final String text) {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT PARSE_JSON('" + text + "')");
            }
        });
    }

    @Test
    void aLetterNameIsRead() {
        assertEquals("{\"a\":1}", doc("{a:1}"));
        assertEquals("{\"A\":1}", doc("{A:1}"));
        assertEquals("{\"aB\":1}", doc("{aB:1}"));
        assertEquals("{\"abc\":1}", doc("{abc:1}"));
    }

    @Test
    void anUnderscoreIsANameCharacterAnywhere() {
        assertEquals("{\"_a\":1}", doc("{_a:1}"));
        assertEquals("{\"_\":1}", doc("{_:1}"));
        assertEquals("{\"a_b\":1}", doc("{a_b:1}"));
        assertEquals("{\"a_\":1}", doc("{a_:1}"));
    }

    @Test
    void aDigitIsANameCharacterButNotAFirstOne() {
        assertEquals("{\"a1\":1}", doc("{a1:1}"));
        assertEquals("{\"a1b\":1}", doc("{a1b:1}"));
        refuses("{1a:1}");
        refuses("{1:1}");
    }

    @Test
    void theDollarJacksonWouldAllowIsRefused() {
        refuses("{$a$:1}");
        refuses("{$:1}");
        refuses("{a$:1}");
        refuses("{$a:1}");
    }

    @Test
    void punctuationAndNonAsciiAreRefused() {
        refuses("{a-b:1}");
        refuses("{a b:1}");
        refuses("{a.b:1}");
        refuses("{a@b:1}");
        refuses("{a+b:1}");
        refuses("{a/b:1}");
        refuses("{ä:1}");
    }

    @Test
    void aKeywordInNamePositionIsJustAName() {
        assertEquals("{\"true\":1}", doc("{true:1}"));
        assertEquals("{\"null\":1}", doc("{null:1}"));
        assertEquals("{\"false\":1}", doc("{false:1}"));
        assertEquals("{\"nan\":1}", doc("{nan:1}"));
        assertEquals("{\"undefined\":1}", doc("{undefined:1}"));
    }

    @Test
    void aKeywordInVALUEpositionIsStillAKeyword() {
        assertEquals("{\"a\":NaN}", doc("{a:nan}"));
        assertEquals("{\"a\":true}", doc("{a:TRUE}"));
        assertEquals("{\"a\":Infinity}", doc("{a:inf}"));
    }

    @Test
    void itWorksWhereverAnObjectStands() {
        assertEquals("{\"a\":{\"b\":1}}", doc("{a:{b:1}}"));
        assertEquals("{\"a\":1,\"b\":2}", doc("{a:1,b:2}"));
        assertEquals("{\"a\":1}", doc("{ a : 1 }"));
        assertEquals("[{\"a\":1},{\"b\":2}]", doc("[{a:1},{b:2}]"));
        assertEquals("{\"a\":1,\"b\":2}", doc("{a:1,\"b\":2}"));
    }

    @Test
    void theCaseOfANameIsKept() {
        final ResultSet rs = engine.executeQuery("SELECT PARSE_JSON('{Ab:1}'):Ab");
        rs.next();
        assertEquals("1", String.valueOf(rs.getValue(0)));
    }

    @Test
    void aSingleQuotedNameStillWorks() {
        assertEquals("{\"a\":1}", doc("{''a'':1}"));
    }

    @Test
    void theDollarRefusalIsWordedTheWayLiveWordsIt() {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT PARSE_JSON('{$a$:1}')");
            }
        });
        assertTrue(String.valueOf(e.getMessage())
                .contains("invalid character outside of a string: '$', pos 2"),
            "expected live's invalid-character sentence, got: " + e.getMessage());
    }
}
