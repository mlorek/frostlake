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

package dev.frostlake.stream;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A stream's name keeps the case it was created with, as every other object's does: an unquoted name folds to
 * upper case, a double-quoted one is kept as written, so {@code "s"} and {@code S} are two streams. The name was
 * folded to upper case at CREATE, so {@code "Mixed Case"} was listed as {@code MIXED CASE}, {@code "s"} then
 * blocked creating {@code S}, and a quoted name inside a string argument could not be found.
 */
public class StreamNameCaseTest extends BaseDatabaseTest {

    /** The listed streams' names and comments, as {@code name:comment} joined by commas. */
    private String listed(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            if (out.length() > 0) {
                out.append(",");
            }
            out.append(row.getValue(1)).append(":").append(row.getValue(5));
        }
        return out.toString();
    }

    /** The first cell of a query's first row. */
    private Object cell(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void quotedNamesKeepTheirCaseAndDifferFromTheirFolding() {
        engine.execute("CREATE TABLE src (a INT)");
        engine.execute("CREATE STREAM \"Mixed Case\" ON TABLE src");
        engine.execute("CREATE STREAM \"s\" ON TABLE src");
        engine.execute("CREATE STREAM S ON TABLE src COMMENT = 'upper'");

        assertEquals("Mixed Case:,S:upper,s:", listed("SHOW STREAMS"));
        assertEquals("Mixed Case", engine.executeQuery("DESCRIBE STREAM \"Mixed Case\"").getRows().get(0).getValue(1));
        assertEquals("create or replace stream \"Mixed Case\" on table SRC;",
            cell("SELECT GET_DDL('STREAM', '\"Mixed Case\"')"));
    }

    @Test
    public void replaceAndDropReachOnlyTheStreamOfThatExactName() {
        engine.execute("CREATE TABLE src (a INT)");
        engine.execute("CREATE STREAM S ON TABLE src COMMENT = 'upper'");
        engine.execute("CREATE OR REPLACE STREAM \"s\" ON TABLE src COMMENT = 'lower'");
        assertEquals("S:upper,s:lower", listed("SHOW STREAMS"));

        engine.execute("CREATE STREAM \"Copy\" CLONE \"s\"");
        engine.execute("ALTER STREAM \"Copy\" SET COMMENT = 'copied'");
        engine.execute("DROP STREAM \"s\"");
        assertEquals("Copy:copied,S:upper", listed("SHOW STREAMS"));
    }

    @Test
    public void aStringArgumentReadsAQuotedNameAsWritten() {
        engine.execute("CREATE TABLE src (a INT)");
        engine.execute("CREATE STREAM \"Mixed Case\" ON TABLE src");
        engine.execute("CREATE STREAM quiet ON TABLE src APPEND_ONLY = TRUE");
        engine.execute("INSERT INTO src VALUES (1)");
        engine.execute("CREATE TABLE sink (a INT)");
        engine.execute("INSERT INTO sink SELECT a FROM quiet");

        assertEquals(true, cell("SELECT SYSTEM$STREAM_HAS_DATA('\"Mixed Case\"')"));
        assertEquals(false, cell("SELECT SYSTEM$STREAM_HAS_DATA('quiet')"));
    }
}
