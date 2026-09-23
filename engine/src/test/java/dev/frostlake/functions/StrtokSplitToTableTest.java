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
 * STRTOK_SPLIT_TO_TABLE — SPLIT_TO_TABLE's tokenizing sibling. Its second argument is a SET of
 * delimiter CHARACTERS rather than one delimiter string, it defaults to a single space, and it never
 * produces an empty token, so consecutive, leading and trailing delimiters all collapse.
 */
public class StrtokSplitToTableTest extends BaseDatabaseTest {

    /** Every row of a result, columns joined by '|' and rows by ';'. */
    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        while (rs.next()) {
            if (text.length() > 0) {
                text.append(';');
            }
            for (int c = 0; c < rs.getColumnCount(); c++) {
                text.append(c == 0 ? "" : "|").append(String.valueOf(rs.getValue(c)));
            }
        }
        return text.toString();
    }

    /** The message of the refusal a statement raises, newlines flattened. */
    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                final ResultSet rs = engine.executeQuery(sql);
                while (rs.next()) {
                    continue;
                }
            }
        }).getMessage().replace("\n", " | ");
    }

    /** Two arguments: the string, and the characters to split it on. */
    @Test
    public void itSplitsOnEachDelimiterCharacter() {
        assertEquals("a;b", rows("SELECT t.value FROM TABLE(STRTOK_SPLIT_TO_TABLE('a.b', '.')) t "
            + "ORDER BY t.index"));
        assertEquals("a;b;c", rows("SELECT t.value FROM TABLE(STRTOK_SPLIT_TO_TABLE('a.b-c', '.-')) t "
            + "ORDER BY t.index"));
    }

    /** The delimiter set may be left out, and is then a single space. */
    @Test
    public void theDelimiterSetDefaultsToASpace() {
        assertEquals("a;b", rows("SELECT t.value FROM TABLE(STRTOK_SPLIT_TO_TABLE('a b')) t "
            + "ORDER BY t.index"));
    }

    /** The three columns, SEQ numbering the input record and INDEX the token. */
    @Test
    public void itAnswersSeqIndexAndValue() {
        assertEquals("1|1|a;1|2|b", rows("SELECT t.seq, t.index, t.value "
            + "FROM TABLE(STRTOK_SPLIT_TO_TABLE('a.b', '.')) t ORDER BY t.index"));
    }

    /** An empty token is never produced: runs of delimiters, and leading or trailing ones, collapse. */
    @Test
    public void emptyTokensAreDropped() {
        assertEquals("a;b", rows("SELECT t.value FROM TABLE(STRTOK_SPLIT_TO_TABLE('.a..b.', '.')) t "
            + "ORDER BY t.index"));
    }

    /** A NULL on either side yields no rows at all, and an empty delimiter set does not split. */
    @Test
    public void nullYieldsNoRowsAndAnEmptySetDoesNotSplit() {
        assertEquals("", rows("SELECT t.value FROM TABLE(STRTOK_SPLIT_TO_TABLE(NULL, '.')) t"));
        assertEquals("", rows("SELECT t.value FROM TABLE(STRTOK_SPLIT_TO_TABLE('a.b', NULL)) t"));
        assertEquals("a.b", rows("SELECT t.value FROM TABLE(STRTOK_SPLIT_TO_TABLE('a.b', '')) t"));
    }

    /** It is positional only, as SPLIT_TO_TABLE is. */
    @Test
    public void itTakesNoNamedArguments() {
        assertTrue(refusal("SELECT t.value FROM TABLE(STRTOK_SPLIT_TO_TABLE(STRING => 'a.b')) t")
            .contains("unexpected argument [STRING] at position 1"));
    }

    /** Its first argument takes text, and a temporal column is refused while the statement compiles. */
    @Test
    public void aTemporalArgumentIsRefused() {
        engine.execute("CREATE OR REPLACE TABLE tq (t9 TIME(9))");
        engine.execute("INSERT INTO tq VALUES ('10:00:00.123456789')");
        assertTrue(refusal("SELECT s.value FROM tq, TABLE(STRTOK_SPLIT_TO_TABLE(tq.t9, ':')) s")
            .contains("invalid type [TIME(9)] for parameter '1'"));
    }
}
