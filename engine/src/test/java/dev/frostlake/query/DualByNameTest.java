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
package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DUAL written in FROM answers to its alias alone: no qualifier names the unaliased pseudo-table, so
 * {@code DUAL.COLUMN1} is no identifier there and {@code DUAL.*} no object, while its column and a plain star
 * read it. A SELECT with no FROM is different: its DUAL is reached by name, from a query nested inside it too.
 */
public class DualByNameTest extends BaseDatabaseTest {

    private static final String AT = "SQL compilation error: error line 1 at position ";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO FZ VALUES (1, TRUE), (2, FALSE)");
    }

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql).getMessage();
    }

    private List<String> rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final StringBuilder line = new StringBuilder();
            for (int i = 0; i < rs.getColumns().size(); i++) {
                line.append(i > 0 ? "|" : "").append(row.getValue(i));
            }
            out.add(line.toString());
        }
        return out;
    }

    @Test
    public void noQualifierNamesThePseudoTableWrittenInFrom() {
        assertEquals(AT + "7\ninvalid identifier 'DUAL.COLUMN1'", refusalOf("SELECT dual.column1 FROM DUAL"));
        assertEquals(AT + "7\ninvalid identifier 'DUAL.COLUMN1'", refusalOf("SELECT \"DUAL\".column1 FROM DUAL"));
        assertEquals(AT + "7\ninvalid identifier 'DUAL.COLUMN1'", refusalOf("SELECT DUAL.column1 FROM DUAL, FZ"));
        assertEquals(AT + "7\ninvalid identifier 'DUAL.$1'", refusalOf("SELECT dual.$1 FROM DUAL"));
        assertEquals(AT + "13\ninvalid identifier 'DUAL.COLUMN1'", refusalOf("SELECT UPPER(dual.column1) FROM DUAL"));
        assertEquals(AT + "25\ninvalid identifier 'DUAL.COLUMN1'",
            refusalOf("SELECT 1 FROM DUAL WHERE dual.column1 IS NULL"));
        assertEquals(AT + "28\ninvalid identifier 'DUAL.COLUMN1'",
            refusalOf("SELECT 1 FROM DUAL ORDER BY dual.column1"));
        assertEquals(AT + "28\ninvalid identifier 'DUAL.COLUMN1'",
            refusalOf("SELECT 1 FROM DUAL GROUP BY dual.column1"));
        assertEquals(AT + "30\ninvalid identifier 'DUAL.COLUMN1'",
            refusalOf("SELECT 1 FROM dual JOIN fz ON dual.column1 = fz.id"));
        assertEquals("SQL compilation error:\nObject 'DUAL' does not exist or not authorized.",
            refusalOf("SELECT dual.* FROM DUAL"));
        assertEquals("SQL compilation error:\nObject 'DUAL' does not exist or not authorized.",
            refusalOf("SELECT dual.*, fz.id FROM DUAL, FZ ORDER BY 2"));
    }

    @Test
    public void itsColumnAStarAndAnAliasReadIt() {
        assertEquals(List.of("null"), rows("SELECT column1 FROM DUAL"));
        assertEquals(List.of("null"), rows("SELECT column1 FROM dual"));
        assertEquals(List.of("null"), rows("SELECT $1 FROM DUAL"));
        assertEquals(List.of("null"), rows("SELECT d.* FROM DUAL AS d"));
        assertEquals(List.of("null"), rows("SELECT d.column1 FROM DUAL d"));
        assertEquals(List.of("null"), rows("SELECT dual.column1 FROM dual dual"));
        assertEquals(List.of("null|1|true", "null|2|false"), rows("SELECT * FROM DUAL, FZ ORDER BY id"));
        assertEquals(List.of("1|true", "2|false"), rows("SELECT * EXCLUDE (column1) FROM DUAL, FZ ORDER BY id"));
        assertEquals(List.of("1"), rows("SELECT COUNT(*) FROM DUAL"));
    }

    @Test
    public void aSelectWithNoFromIsReachedByName() {
        assertEquals(List.of("null"), rows("SELECT dual.column1"));
        assertEquals(List.of("null"), rows("SELECT dual.*"));
        // A nested query's own FROM DUAL is not named, so its DUAL.COLUMN1 is the outer select's.
        assertEquals(List.of("null"), rows("SELECT (SELECT dual.column1 FROM DUAL)"));
    }
}
