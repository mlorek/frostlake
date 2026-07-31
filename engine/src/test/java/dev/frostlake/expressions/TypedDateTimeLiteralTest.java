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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Typed date/time literals — {@code DATE '2020-01-15'}, {@code TIME '…'}, {@code TIMESTAMP '…'}. Each is
 * equivalent to {@code '<string>'::<TYPE>}, so it composes with the date/time operators, WHERE, and
 * INSERT. Live-verified: only those three keywords form typed literals — {@code TIMESTAMP_NTZ '…'} is a
 * syntax error (use the {@code ::TIMESTAMP_NTZ} cast instead). A {@code DATE}/{@code TIMESTAMP} keyword
 * NOT followed by a string still parses as an identifier (column name), so nothing regresses.
 */
public class TypedDateTimeLiteralTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void dateLiteral() {
        assertEquals("2020-01-15", scalar("SELECT DATE '2020-01-15'").toString());
    }

    @Test
    public void timestampLiteral() {
        // A typed TIMESTAMP literal is a real LocalDateTime (equal to TO_TIMESTAMP_NTZ of the same string),
        // so its toString() is ISO-8601 ('T' separator, and LocalDateTime omits a :00 seconds field) rather
        // than the raw source string.
        assertEquals("2020-01-15T10:30", scalar("SELECT TIMESTAMP '2020-01-15 10:30:00'").toString());
    }

    @Test
    public void timestampNtzLiteral() {
        // TIMESTAMP_NTZ is not a typed-literal keyword (live-verified) — the cast form is the way.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TIMESTAMP_NTZ '2020-01-15 10:30:00'");
            }
        });
        assertEquals("2020-01-15T10:30", scalar("SELECT '2020-01-15 10:30:00'::TIMESTAMP_NTZ").toString());
    }

    @Test
    public void keywordIsCaseInsensitive() {
        assertEquals("2020-01-15", scalar("SELECT date '2020-01-15'").toString());
    }

    @Test
    public void composesWithDateArithmetic() {
        assertEquals("2020-01-20", scalar("SELECT DATE '2020-01-15' + 5").toString());
        // 2020 is a leap year: Jan (31) + Feb (29) = 60 days.
        assertEquals(60L, ((Number) scalar("SELECT DATE '2020-03-01' - DATE '2020-01-01'")).longValue());
    }

    @Test
    public void worksInWhere() {
        engine.execute("CREATE TABLE t (id INTEGER, d DATE)");
        engine.execute("INSERT INTO t VALUES (1, '2020-01-10'), (2, '2020-02-20')");
        final Object id = scalar("SELECT id FROM t WHERE d > DATE '2020-01-31'");
        assertEquals(2L, ((Number) id).longValue());
    }

    @Test
    public void worksInInsertValues() {
        engine.execute("CREATE TABLE t (id INTEGER, d DATE)");
        engine.execute("INSERT INTO t VALUES (1, DATE '2020-12-25')");
        assertEquals("2020-12-25", scalar("SELECT d FROM t WHERE id = 1").toString());
    }

    @Test
    public void dateAsColumnNameStillResolves() {
        engine.execute("CREATE TABLE c (date VARCHAR, timestamp VARCHAR)");
        engine.execute("INSERT INTO c VALUES ('a', 'b')");
        assertEquals("a", scalar("SELECT date FROM c").toString());
        assertEquals("b", scalar("SELECT timestamp FROM c").toString());
    }
}
