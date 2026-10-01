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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.LiveSnowflake;
import dev.frostlake.storage.Row;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

/**
 * A SHOW scope written as a single keyword — DATASET, MATERIALIZED, USER, DATE, MODEL outside the plain function
 * listing — names a schema on its own and reads no name after it: whatever follows it that does not open the
 * listing's modifiers is a syntax error where it stands, the first word of a qualified name, an IDENTIFIER word and a
 * VIEW alike. A word Frostlake keeps as a keyword but live reads as a name — FLATTEN, CURRENT_DATE, … — is a class
 * there like any other name. In a block live names the refused word and one token more. DATASET is a kind live
 * drops, and no class a listing names. Every cell is live-verified.
 */
public class ShowKeywordScopeWordTest extends BaseDatabaseTest {

    private static final String NO_OBJECT = "SQL compilation error:|Object does not exist, or operation cannot be performed.";

    /** Every row's first cell, a bar between rows, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                out.append(out.length() > 0 ? " | " : "").append(row.getValue(0));
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The status a DROP answers, read off a raw connection in live mode, where the harness reports a count. */
    private String statusOf(final String sql) {
        if (LiveSnowflake.enabled()) {
            try {
                final Connection connection = LiveSnowflake.shared();
                final Statement st = connection.createStatement();
                final ResultSet rs = st.executeQuery(sql);
                rs.next();
                final String value = rs.getString(1);
                rs.close();
                st.close();
                return value;
            } catch (final SQLException e) {
                throw new IllegalStateException(sql + " failed on live: " + e.getMessage(), e);
            }
        }
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private static String syntaxError(final int line, final int position, final String token) {
        return "SQL compilation error:|syntax error line " + line + " at position " + position + " unexpected '"
            + token + "'.";
    }

    private static String syntaxError(final int position, final String token) {
        return syntaxError(1, position, token);
    }

    /** The refusal of a block: a syntax error line per position and token, in order. */
    private static String syntaxErrors(final Object... positionsAndTokens) {
        final StringBuilder lines = new StringBuilder("SQL compilation error:");
        for (int i = 0; i + 1 < positionsAndTokens.length; i += 2) {
            lines.append("|syntax error line 1 at position ").append(positionsAndTokens[i]).append(" unexpected '")
                .append(positionsAndTokens[i + 1]).append("'.");
        }
        return lines.toString();
    }

    /** Asserts a refusal by its first sentence, which a hint may follow. */
    private void assertRefused(final String sentence, final String sql) {
        final String answer = answer(sql);
        assertTrue(answer.startsWith(sentence), sql + " answered " + answer);
    }

    @Test
    public void theWordAfterDatasetIsRefusedInEveryListing() {
        assertEquals(syntaxError(23, "y"), answer("SHOW TABLES IN DATASET y"));
        assertEquals(syntaxError(23, "y"), answer("SHOW TABLES IN dataset y"));
        assertEquals(syntaxError(22, "y"), answer("SHOW VIEWS IN DATASET y"));
        assertEquals(syntaxError(24, "y"), answer("SHOW COLUMNS IN DATASET y"));
        assertEquals(syntaxError(24, "y"), answer("SHOW SCHEMAS IN DATASET y"));
        assertEquals(syntaxError(26, "y"), answer("SHOW FUNCTIONS IN DATASET y"));
        assertEquals(syntaxError(27, "y"), answer("SHOW PROCEDURES IN DATASET y"));
        assertEquals(syntaxError(29, "y"), answer("SHOW PRIMARY KEYS IN DATASET y"));
        assertEquals(syntaxError(23, "y"), answer("SHOW STAGES IN DATASET y"));
        assertEquals(syntaxError(22, "y"), answer("SHOW USERS IN DATASET y"));
        assertEquals(syntaxError(22, "y"), answer("SHOW ROLES IN DATASET y"));
        assertEquals(syntaxError(26, "y"), answer("SHOW DATABASES IN DATASET y"));
        assertEquals(syntaxError(23, "\"y\""), answer("SHOW TABLES IN DATASET \"y\""));
        assertEquals(syntaxError(23, "a"), answer("SHOW TABLES IN DATASET a.b"));
        assertEquals(syntaxError(23, "IDENTIFIER"), answer("SHOW TABLES IN DATASET IDENTIFIER('y')"));
        assertEquals(syntaxError(23, "identifier"), answer("SHOW TABLES IN DATASET identifier('y')"));
        assertEquals(syntaxError(23, "y"), answer("SHOW TABLES IN DATASET y LIMIT 1"));
        assertEquals(syntaxError(23, "x"), answer("SHOW TABLES IN DATASET x LIMIT 0"));
        assertEquals(syntaxError(32, "y"), answer("SHOW TABLES LIKE 'x' IN DATASET y"));
        assertEquals(syntaxError(23, "SCHEMA"), answer("SHOW TABLES IN DATASET SCHEMA x"));
        assertEquals(syntaxError(23, "ACCOUNT"), answer("SHOW TABLES IN DATASET ACCOUNT"));
    }

    @Test
    public void materializedIsAScopeWordOfItsOwnSoItsViewIsRefused() {
        assertEquals(syntaxError(28, "VIEW"), answer("SHOW TABLES IN MATERIALIZED VIEW y"));
        assertEquals(syntaxError(28, "VIEW"), answer("SHOW TABLES IN MATERIALIZED VIEW y z"));
        assertEquals(syntaxError(28, "view"), answer("show tables in materialized view y"));
        assertEquals(syntaxError(2, 0, "VIEW"), answer("SHOW TABLES IN MATERIALIZED\nVIEW y"));
        assertEquals(syntaxError(27, "VIEW"), answer("SHOW VIEWS IN MATERIALIZED VIEW y"));
        assertEquals(syntaxError(29, "VIEW"), answer("SHOW COLUMNS IN MATERIALIZED VIEW y"));
        assertEquals(syntaxError(31, "VIEW"), answer("SHOW FUNCTIONS IN MATERIALIZED VIEW y"));
        assertEquals(syntaxError(40, "VIEW"), answer("SHOW MATERIALIZED VIEWS IN MATERIALIZED VIEW y"));
        assertEquals(syntaxError(34, "VIEW"), answer("SHOW TERSE TABLES IN MATERIALIZED VIEW y"));
        assertEquals(syntaxError(37, "VIEW"), answer("SHOW TABLES LIKE 'x' IN MATERIALIZED VIEW y"));
        assertEquals(syntaxError(28, "y"), answer("SHOW TABLES IN MATERIALIZED y"));
        assertEquals(syntaxError(28, "a"), answer("SHOW TABLES IN MATERIALIZED a.b.c"));
        assertEquals(syntaxError(31, "IDENTIFIER"), answer("SHOW FUNCTIONS IN MATERIALIZED IDENTIFIER('y')"));
    }

    @Test
    public void everyKeywordScopeWordRefusesTheFirstWordAfterIt() {
        assertEquals(syntaxError(20, "x"), answer("SHOW TABLES IN USER x y"));
        assertEquals(syntaxError(20, "x"), answer("SHOW TABLES IN DATE x y"));
        assertEquals(syntaxError(23, "x"), answer("SHOW FUNCTIONS IN USER x y"));
        assertEquals(syntaxError(23, "x"), answer("SHOW TABLES IN DATASET x y"));
        assertEquals(syntaxError(28, "x"), answer("SHOW TABLES IN MATERIALIZED x y"));
        assertEquals(syntaxError(21, "x"), answer("SHOW TABLES IN MODEL x y"));
        assertEquals(syntaxError(25, "x"), answer("SHOW PROCEDURES IN MODEL x y"));
        assertEquals(syntaxError(20, "IDENTIFIER"), answer("SHOW TABLES IN USER IDENTIFIER('y')"));
        // The function listing reads MODEL as a scope kind, and a name after it.
        assertEquals(syntaxError(26, "y"), answer("SHOW FUNCTIONS IN MODEL x y"));
    }

    @Test
    public void aWordLiveReadsAsANameIsAClassThere() {
        assertEquals(syntaxError(28, "y"), answer("SHOW FUNCTIONS IN FLATTEN x y"));
        assertEquals(syntaxError(28, "y"), answer("SHOW FUNCTIONS IN flatten x y"));
        assertEquals(syntaxError(33, "y"), answer("SHOW FUNCTIONS IN CURRENT_DATE x y"));
        assertEquals(syntaxError(25, "y"), answer("SHOW TABLES IN FLATTEN x y"));
        assertEquals(syntaxError(31, "y"), answer("SHOW PROCEDURES IN GENERATOR x y"));
        assertEquals(syntaxError(24, "y"), answer("SHOW ROLES IN FLATTEN x y"));
        assertRefused("SQL compilation error: Object type or Class 'FLATTEN' does not exist or not authorized.",
            "SHOW FUNCTIONS IN FLATTEN x");
        assertRefused("SQL compilation error: Object type or Class 'CURRENT_DATE' does not exist or not authorized.",
            "SHOW FUNCTIONS IN CURRENT_DATE x");
        assertEquals("syntax error line 1 at position 15 unexpected 'FLATTEN'.", answer("SHOW TABLES IN FLATTEN x"));
        assertEquals("syntax error line 1 at position 15 unexpected 'GENERATOR'.",
            answer("SHOW TABLES IN GENERATOR LIMIT"));
        assertEquals("SQL compilation error:|Unsupported statement type 'Cannot show objects of type VIEW in INSTANCE'.",
            answer("SHOW VIEWS IN FLATTEN x"));
    }

    @Test
    public void modelIsAScopeKindOfThePlainFunctionListingAlone() {
        assertEquals(syntaxError(29, "m"), answer("SHOW USER FUNCTIONS IN MODEL m"));
        assertEquals(syntaxError(30, "m"), answer("SHOW TERSE FUNCTIONS IN MODEL m"));
        assertEquals(syntaxError(32, "m"), answer("SHOW BUILTIN FUNCTIONS IN MODEL m"));
        assertEquals(syntaxError(38, "m"), answer("SHOW USER FUNCTIONS LIKE 'x' IN MODEL m"));
        assertEquals(NO_OBJECT, answer("SHOW FUNCTIONS LIKE 'x' IN MODEL m"));
    }

    @Test
    public void inABlockTheRefusedWordAndOneTokenMoreAreReported() {
        assertEquals(syntaxErrors(29, "y", 32, "END"), answer("BEGIN SHOW TABLES IN DATASET y; END;"));
        assertEquals(syntaxErrors(26, "x", 28, "y"), answer("BEGIN SHOW TABLES IN USER x y; END;"));
        assertEquals(syntaxErrors(26, "x", 28, "y"), answer("BEGIN SHOW TABLES IN USER x y z; END;"));
        assertEquals(syntaxErrors(34, "VIEW", 39, "y"), answer("BEGIN SHOW TABLES IN MATERIALIZED VIEW y; END;"));
        assertEquals(syntaxErrors(26, "x", 27, "."), answer("BEGIN SHOW TABLES IN USER x.y; END;"));
        assertEquals(syntaxErrors(29, "y", 31, "LIMIT"), answer("BEGIN SHOW TABLES IN DATASET y LIMIT 1; END;"));
        assertEquals(syntaxErrors(29, "y"), answer("BEGIN SHOW TABLES IN DATASET y END;"));
        assertEquals(syntaxErrors(29, "y", 32, "RETURN"), answer("BEGIN SHOW TABLES IN DATASET y; RETURN 1; END;"));
        assertEquals(syntaxErrors(29, "y", 32, "y"), answer("BEGIN SHOW TABLES IN DATASET y; y := 1; END;"));
        assertEquals(syntaxErrors(29, "y", 42, "END"), answer("BEGIN SHOW TABLES IN DATASET y; SELECT 1; END;"));
        assertEquals(syntaxErrors(62, "x", 64, "y"),
            answer("BEGIN SELECT 1; EXCEPTION WHEN OTHER THEN SHOW TABLES IN USER x y; END;"));
        assertEquals(syntaxErrors(35, "y", 43, "END"), answer("BEGIN BEGIN SHOW TABLES IN DATASET y; END; END;"));
        assertEquals(syntaxErrors(31, "y", 34, "END"), answer("BEGIN SHOW TABLES IN FLATTEN x y; END;"));
    }

    @Test
    public void inAConstructsBodyTheRecoveryResumesPastItsEnd() {
        assertEquals(syntaxErrors(44, "y", 55, "END"),
            answer("BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; END IF; END;"));
        assertEquals(syntaxErrors(41, "x", 54, "END"),
            answer("BEGIN IF (TRUE) THEN SHOW TABLES IN USER x y; END IF; END;"));
        assertEquals(syntaxErrors(48, "y", 55, "FOR"),
            answer("BEGIN FOR i IN 1 TO 2 DO SHOW TABLES IN DATASET y; END FOR; END;"));
        assertEquals(syntaxErrors(34, "y", 47, "END"), answer("BEGIN LOOP SHOW TABLES IN DATASET y; END LOOP; END;"));
        assertEquals(syntaxErrors(45, "y", 59, "END"),
            answer("BEGIN WHILE (TRUE) DO SHOW TABLES IN DATASET y; END WHILE; END;"));
        assertEquals(syntaxErrors(44, "y"), answer("BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y END IF; END;"));
    }

    @Test
    public void aLimitAfterAKeywordScopeWordIsTheListingsOwn() {
        assertEquals(syntaxError(25, "<EOF>"), answer("SHOW TABLES IN USER LIMIT"));
        assertEquals(syntaxError(28, "<EOF>"), answer("SHOW TABLES IN DATASET LIMIT"));
        assertEquals(syntaxError(33, "<EOF>"), answer("SHOW TABLES IN MATERIALIZED LIMIT"));
        assertEquals(syntaxError(26, "<EOF>"), answer("SHOW TABLES IN MODEL LIMIT"));
        assertEquals(NO_OBJECT, answer("SHOW TABLES IN DATASET LIMIT 1"));
        assertEquals(NO_OBJECT, answer("SHOW TABLES IN MATERIALIZED LIMIT 1"));
        assertEquals(NO_OBJECT, answer("SHOW FUNCTIONS IN MODEL LIMIT"));
    }

    @Test
    public void aKeywordScopeWordAloneNamesASchema() {
        assertEquals(NO_OBJECT, answer("SHOW TABLES IN DATASET"));
        assertEquals(NO_OBJECT, answer("SHOW TABLES IN MATERIALIZED"));
        assertEquals(NO_OBJECT, answer("SHOW USERS IN DATASET"));
        assertRefused("SQL compilation error:|Schema 'TEST_DB.DATASET' does not exist or not authorized.",
            "SHOW FUNCTIONS IN DATASET");
        assertRefused("SQL compilation error:|Schema 'TEST_DB.MATERIALIZED' does not exist or not authorized.",
            "SHOW SEQUENCES IN MATERIALIZED");
        assertRefused("SQL compilation error:|Table 'DATASET' does not exist or not authorized.",
            "SHOW COLUMNS IN DATASET");
    }

    @Test
    public void datasetIsAKindLiveDropsAndNoClass() {
        assertEquals(syntaxError(5, "DATASET"), answer("SHOW DATASET"));
        assertEquals(syntaxError(11, "DATASET"), answer("SHOW TERSE DATASET"));
        assertEquals(syntaxError(5, "DATASET"), answer("SHOW DATASET IN SCHEMA TEST_SCHEMA"));
        assertRefused("SQL compilation error:|Dataset 'TEST_DB.TEST_SCHEMA.X' does not exist or not authorized.",
            "DROP DATASET x");
        assertRefused("SQL compilation error:|Dataset 'TEST_DB.TEST_SCHEMA.X' does not exist or not authorized.",
            "DROP DATASET x CASCADE");
        assertRefused("SQL compilation error:|Dataset 'TEST_DB.TEST_SCHEMA.X' does not exist or not authorized.",
            "DROP DATASET TEST_SCHEMA.x");
        assertRefused("SQL compilation error:|Dataset 'TEST_DB.TEST_SCHEMA.\"x\"' does not exist or not authorized.",
            "DROP DATASET \"x\"");
        assertRefused("SQL compilation error:|Database 'A' does not exist or not authorized.", "DROP DATASET a.b.x");
        assertEquals(syntaxError(12, "<EOF>"), answer("DROP DATASET"));
        assertEquals("Drop statement executed successfully (X already dropped).", statusOf("DROP DATASET IF EXISTS x"));
    }
}
