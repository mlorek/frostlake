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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Snowflake folds an <b>unquoted</b> identifier to UPPERCASE and preserves a <b>"double-quoted"</b> one.
 * Frostlake resolves names case-insensitively, but the canonical (stored/displayed) name must fold so that
 * INFORMATION_SCHEMA, SHOW output and result-set column labels match Snowflake — e.g. an existence check
 * that compares {@code COLUMN_NAME} to an upper-case literal.
 */
public class IdentifierCaseFoldingTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE DATABASE test");
        engine.execute("USE DATABASE test");
        engine.execute("CREATE SCHEMA app");
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void unquotedNamesFoldToUpperCaseInInformationSchema() {
        engine.execute("CREATE TABLE app.cfg (col_a VARCHAR, note VARCHAR)");
        // The lowercase-written schema/table/column all report upper-case, matching Snowflake.
        assertEquals(1L, ((Number) scalar(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_SCHEMA = 'APP' AND TABLE_NAME = 'CFG' AND COLUMN_NAME = 'NOTE'")).longValue());
    }

    @Test
    public void selectColumnLabelFolds() {
        engine.execute("CREATE TABLE app.cfg (note VARCHAR)");
        engine.execute("INSERT INTO app.cfg VALUES ('x')");
        final ResultSet rs = engine.executeQuery("SELECT note FROM app.cfg");
        assertEquals("NOTE", rs.getColumns().get(0).getName());
    }

    @Test
    public void quotedIdentifierPreservesCase() {
        engine.execute("CREATE TABLE app.q (\"MixedCol\" INT)");
        assertEquals("MixedCol", scalar(
            "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Q'"));
    }

    @Test
    public void idempotentColumnMigrationDoesNotReAddExistingColumn() {
        // A common pattern: guard an ALTER TABLE ADD COLUMN with an INFORMATION_SCHEMA existence check
        // written in Snowflake's upper-case convention. The column already exists, so the count is 1, the
        // IF is skipped, and no "column already exists" error is raised.
        engine.execute("CREATE TABLE app.cfg (col_a VARCHAR, note VARCHAR)");
        engine.execute("""
            EXECUTE IMMEDIATE $$
            DECLARE
                col_count INT;
            BEGIN
                SELECT COUNT(*) INTO :col_count
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = 'APP' AND TABLE_NAME = 'CFG' AND COLUMN_NAME = 'NOTE';
                IF (col_count = 0) THEN
                    ALTER TABLE app.cfg ADD COLUMN note VARCHAR;
                END IF;
            END;
            $$
            """);
        // Still exactly two columns — the guard was a no-op the second time.
        assertEquals(2L, ((Number) scalar(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'CFG'")).longValue());
    }
}
