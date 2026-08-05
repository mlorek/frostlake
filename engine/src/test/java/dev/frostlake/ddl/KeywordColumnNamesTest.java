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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * INNER, JOIN, LEFT and CROSS are live-legal unquoted NAMES everywhere a name goes — measured cell
 * by cell on a real account: column definitions, bare and qualified references, WHERE, GROUP BY,
 * ORDER BY, aggregate arguments, INSERT column lists, UPDATE SET targets, and table names. CASE is
 * the measured exception: it works in a column DEFINITION and AFTER a dot ({@code kw.case}), but a
 * BARE leading {@code case} is live's syntax error — the CASE expression owns that spot. The join
 * keywords stay keywords where it matters: {@code FROM a LEFT JOIN b} is still a LEFT join, because
 * only the NAME positions admit them — a bare table alias does not.
 */
public class KeywordColumnNamesTest extends BaseDatabaseTest {

    @BeforeEach
    public void fixture() {
        engine.execute("CREATE TABLE kw (inner INTEGER, join INTEGER, case INTEGER,"
            + " left INTEGER, cross INTEGER)");
        engine.execute("INSERT INTO kw (inner, join, case, left, cross) VALUES (1, 2, 3, 4, 5)");
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private long asLong(final String sql) {
        return ((Number) scalar(sql)).longValue();
    }

    @Test
    public void keywordColumnsReadBareInEveryClause() {
        assertEquals(1L, asLong("SELECT inner FROM kw"));
        assertEquals(2L, asLong("SELECT join FROM kw"));
        assertEquals(4L, asLong("SELECT left FROM kw"));
        assertEquals(5L, asLong("SELECT cross FROM kw"));
        assertEquals(1L, asLong("SELECT 1 FROM kw WHERE inner = 1 AND join = 2"));
        assertEquals(1L, asLong("SELECT inner FROM kw ORDER BY inner"));
        assertEquals(1L, asLong("SELECT inner FROM kw GROUP BY inner"));
        assertEquals(2L, asLong("SELECT SUM(join) FROM kw"));
    }

    @Test
    public void caseReadsQualifiedOnly() {
        assertEquals(3L, asLong("SELECT kw.case FROM kw"));
        // A bare leading `case` is the CASE expression's spot — live rejects it as a syntax error.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT case FROM kw");
            }
        });
        // And the CASE expression itself is untouched.
        assertEquals("y", scalar("SELECT CASE WHEN 1 = 1 THEN 'y' ELSE 'n' END"));
    }

    @Test
    public void keywordColumnsWriteThroughUpdate() {
        engine.execute("UPDATE kw SET inner = 10 WHERE inner = 1");
        assertEquals(10L, asLong("SELECT inner FROM kw"));
    }

    /**
     * Live's measured asymmetry: {@code CREATE TABLE inner} succeeds, but the bare {@code FROM
     * inner} is a syntax error — in the FROM position a leading join keyword stays a keyword.
     */
    @Test
    public void aTableMayBeNamedInnerButNotReadBareInFrom() {
        engine.execute("CREATE TABLE inner (a INTEGER)");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT a FROM inner");
            }
        });
    }

    /** The join keywords stay keywords: only NAME positions admit them, never a bare table alias. */
    @Test
    public void joinParsingIsUntouched() {
        engine.execute("CREATE TABLE ja (k INTEGER)");
        engine.execute("INSERT INTO ja VALUES (1), (2)");
        engine.execute("CREATE TABLE jb (k INTEGER)");
        engine.execute("INSERT INTO jb VALUES (1)");
        assertEquals(2L, asLong("SELECT COUNT(*) FROM ja LEFT JOIN jb ON ja.k = jb.k"));
        assertEquals(1L, asLong("SELECT COUNT(*) FROM ja INNER JOIN jb ON ja.k = jb.k"));
        assertEquals(2L, asLong("SELECT COUNT(*) FROM ja CROSS JOIN jb"));
        assertEquals(1L, asLong("SELECT COUNT(*) FROM ja JOIN jb ON ja.k = jb.k"));
    }
}
