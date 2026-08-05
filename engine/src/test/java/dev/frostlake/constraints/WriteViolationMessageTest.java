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

package dev.frostlake.constraints;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The exact wording of the three write violations a DML statement can fail on — a NULL in a NOT NULL column,
 * a value too long for its VARCHAR(n), and a non-numeric string bound to a numeric column.
 *
 * <p>Live-verified on a real account across INSERT, {@code INSERT … SELECT}, UPDATE and MERGE:
 * all four paths report the SAME sentence for a given violation, nested in a per-statement envelope that
 * names the table and column ({@code DML operation to table E1 failed on column NAME with error: …}).
 * Frostlake builds the same envelope on every DML path, so what is pinned here is the WHOLE message —
 * envelope and inner sentence together.
 *
 * <p>Two of the three are worded identically on the COPY path, where they surface as a per-file
 * {@code first_error} (see {@code CopyFirstErrorTest}). The over-long string is the exception: COPY words it
 * {@code User character length limit (n) exceeded by string '…'} instead, which is why the check carries its
 * parts rather than a finished sentence.
 */
public class WriteViolationMessageTest extends BaseDatabaseTest {

    private static final String NOT_NULL =
        "DML operation to table NN failed on column NAME with error:"
            + " NULL result in a non-nullable column";
    private static final String TOO_LONG =
        "DML operation to table TL failed on column NAME with error:"
            + " String 'abcdefgh' is too long and would be truncated";
    private static final String NOT_NUMERIC =
        "DML operation to table NV failed on column ID with error:"
            + " Numeric value 'BADX' is not recognized";

    /** Run {@code sql}, expecting it to fail with exactly {@code expected} as its message. */
    private void expectMessage(final String sql, final String expected) {
        try {
            engine.execute(sql);
            fail("expected a write violation for: " + sql);
        } catch (final RuntimeException e) {
            final String actual = e.getMessage() == null ? "" : e.getMessage();
            assertTrue(actual.contains(expected),
                "expected the message to carry \"" + expected + "\", got: " + actual);
        }
    }

    // ── NULL into a NOT NULL column ──

    @Test
    public void insertNullIntoNotNullColumn() {
        engine.execute("CREATE TABLE nn (id INTEGER, name VARCHAR NOT NULL)");
        expectMessage("INSERT INTO nn VALUES (1, NULL)", NOT_NULL);
    }

    @Test
    public void insertSelectNullIntoNotNullColumn() {
        engine.execute("CREATE TABLE nn (id INTEGER, name VARCHAR NOT NULL)");
        expectMessage("INSERT INTO nn SELECT 2, NULL", NOT_NULL);
    }

    @Test
    public void updateNotNullColumnToNull() {
        engine.execute("CREATE TABLE nn (id INTEGER, name VARCHAR NOT NULL)");
        engine.execute("INSERT INTO nn VALUES (3, 'ok')");
        expectMessage("UPDATE nn SET name = NULL WHERE id = 3", NOT_NULL);
    }

    @Test
    public void mergeInsertingNullIntoNotNullColumn() {
        engine.execute("CREATE TABLE nn (id INTEGER, name VARCHAR NOT NULL)");
        engine.execute("CREATE TABLE src (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO src VALUES (9, NULL)");
        expectMessage("MERGE INTO nn t USING src s ON t.id = s.id"
            + " WHEN NOT MATCHED THEN INSERT (id, name) VALUES (s.id, s.name)", NOT_NULL);
    }

    // ── a value too long for its VARCHAR(n) ──

    @Test
    public void insertOverLongValue() {
        engine.execute("CREATE TABLE tl (id INTEGER, name VARCHAR(3))");
        expectMessage("INSERT INTO tl VALUES (1, 'abcdefgh')", TOO_LONG);
    }

    @Test
    public void insertSelectOverLongValue() {
        engine.execute("CREATE TABLE tl (id INTEGER, name VARCHAR(3))");
        expectMessage("INSERT INTO tl SELECT 2, 'abcdefgh'", TOO_LONG);
    }

    @Test
    public void updateToAnOverLongValue() {
        engine.execute("CREATE TABLE tl (id INTEGER, name VARCHAR(3))");
        engine.execute("INSERT INTO tl VALUES (3, 'ab')");
        expectMessage("UPDATE tl SET name = 'abcdefgh' WHERE id = 3", TOO_LONG);
    }

    @Test
    public void mergeInsertingAnOverLongValue() {
        engine.execute("CREATE TABLE tl (id INTEGER, name VARCHAR(3))");
        engine.execute("CREATE TABLE src (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO src VALUES (9, 'abcdefgh')");
        expectMessage("MERGE INTO tl t USING src s ON t.id = s.id"
            + " WHEN NOT MATCHED THEN INSERT (id, name) VALUES (s.id, s.name)", TOO_LONG);
    }

    // ── a non-numeric string into a numeric column ──

    @Test
    public void insertNonNumericIntoNumericColumn() {
        engine.execute("CREATE TABLE nv (id INTEGER)");
        expectMessage("INSERT INTO nv VALUES ('BADX')", NOT_NUMERIC);
    }
}
