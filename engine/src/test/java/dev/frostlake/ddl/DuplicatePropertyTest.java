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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A property may be given ONCE per statement — and which repeats are refused, in what words, is
 * measured rather than assumed. Live has four distinct sentences for it:
 *
 * <ul>
 *   <li>{@code duplicate property 'X';} (with its trailing semicolon) for table, view, sequence,
 *       warehouse and task properties — a sequence reports its INTERNAL name, {@code SEQUENCE_START};</li>
 *   <li>{@code conflicting values file format parameter 'X'} for a file-format parameter;</li>
 *   <li>{@code conflicting values for copy option 'X'} for a COPY option;</li>
 *   <li>{@code Multiple DEFAULT or AUTOINCREMENT expressions declared for column X.} for a column
 *       carrying two of that one slot, and a primary-key / constraint-signature sentence naming the
 *       TABLE for the key constraints.</li>
 * </ul>
 *
 * <p>Just as important is what stays LEGAL, all of it live-verified: a repeated CLUSTER BY, a
 * repeated COMMENT on a stage or a routine, NOT NULL twice, NOT NULL beside NULL, COLLATE twice —
 * and the option ORDER is free, so the grammar keeps its permissive {@code option*} lists and the
 * refusals live in the option collectors.
 */
public class DuplicatePropertyTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE base_t (n INT)");
        engine.execute("CREATE STAGE st");
    }

    private void assertRefusedWith(final String expected, final String sql) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
        assertEquals(expected, ex.getMessage(), sql);
    }

    private void assertAccepted(final String sql) {
        engine.execute(sql);
    }

    // ── duplicate property 'X'; ───────────────────────────────────────────────────────────────────

    @Test
    public void aTablePropertyMayNotBeGivenTwice() {
        assertRefusedWith("SQL compilation error:\nduplicate property 'DATA_RETENTION_TIME_IN_DAYS';",
            "CREATE TABLE d1 (n INT) DATA_RETENTION_TIME_IN_DAYS = 1 DATA_RETENTION_TIME_IN_DAYS = 0");
        assertRefusedWith("SQL compilation error:\nduplicate property 'CHANGE_TRACKING';",
            "CREATE TABLE d2 (n INT) CHANGE_TRACKING = TRUE CHANGE_TRACKING = FALSE");
    }

    @Test
    public void aViewCommentMayNotBeGivenTwice() {
        assertRefusedWith("SQL compilation error:\nduplicate property 'COMMENT';",
            "CREATE VIEW d3 COMMENT = 'a' COMMENT = 'b' AS SELECT 1 n");
    }

    @Test
    public void aSequenceBoundReportsItsInternalName() {
        assertRefusedWith("SQL compilation error:\nduplicate property 'SEQUENCE_START';",
            "CREATE SEQUENCE d4 START = 1 START = 2");
        assertRefusedWith("SQL compilation error:\nduplicate property 'SEQUENCE_INCREMENT';",
            "CREATE SEQUENCE d5 INCREMENT = 1 INCREMENT = 2");
    }

    @Test
    public void aWarehousePropertyMayNotBeGivenTwice() {
        assertRefusedWith("SQL compilation error:\nduplicate property 'WAREHOUSE_SIZE';",
            "CREATE WAREHOUSE d6 WAREHOUSE_SIZE = 'XSMALL' WAREHOUSE_SIZE = 'SMALL'");
        assertRefusedWith("SQL compilation error:\nduplicate property 'AUTO_SUSPEND';",
            "CREATE WAREHOUSE d7 AUTO_SUSPEND = 60 AUTO_SUSPEND = 120");
    }

    @Test
    public void aTaskOptionMayNotBeGivenTwice() {
        assertRefusedWith("SQL compilation error:\nduplicate property 'SCHEDULE';",
            "CREATE TASK d8 WAREHOUSE = 'COMPUTE_WH' SCHEDULE = '1 MINUTE' SCHEDULE = '2 MINUTE' AS SELECT 1");
    }

    // ── conflicting values … ──────────────────────────────────────────────────────────────────────

    @Test
    public void aFileFormatParameterMayNotBeGivenTwice() {
        assertRefusedWith("SQL compilation error: conflicting values file format parameter 'TYPE'",
            "CREATE FILE FORMAT d9 TYPE = 'CSV' TYPE = 'JSON'");
        assertRefusedWith("SQL compilation error: conflicting values file format parameter 'SKIP_HEADER'",
            "CREATE FILE FORMAT d10 TYPE = 'CSV' SKIP_HEADER = 1 SKIP_HEADER = 2");
    }

    @Test
    public void aCopyOptionMayNotBeGivenTwice() {
        assertRefusedWith("SQL compilation error: conflicting values for copy option 'FILE_FORMAT'\n",
            "COPY INTO base_t FROM @st FILE_FORMAT = (TYPE = 'CSV') FILE_FORMAT = (TYPE = 'JSON')");
    }

    @Test
    public void aFileFormatParameterInsideCopyIsTheFormatSentence() {
        assertRefusedWith("SQL compilation error: conflicting values file format parameter 'SKIP_HEADER'",
            "COPY INTO base_t FROM @st FILE_FORMAT = (TYPE = 'CSV' SKIP_HEADER = 1 SKIP_HEADER = 2)");
    }

    // ── routine options — live refuses the repeat as a syntax error ───────────────────────────────

    @Test
    public void aRoutineOptionMayNotBeRepeated() {
        assertSyntaxRefusal("CREATE FUNCTION d11() RETURNS INT LANGUAGE SQL LANGUAGE SQL AS $$ 1 $$");
        assertSyntaxRefusal("CREATE FUNCTION d12() RETURNS INT IMMUTABLE IMMUTABLE AS $$ 1 $$");
    }

    @Test
    public void theVolatilityAndNullHandlingFamiliesAreExclusive() {
        assertSyntaxRefusal("CREATE FUNCTION d13() RETURNS INT VOLATILE IMMUTABLE AS $$ 1 $$");
        assertSyntaxRefusal(
            "CREATE FUNCTION d14() RETURNS INT CALLED ON NULL INPUT RETURNS NULL ON NULL INPUT AS $$ 1 $$");
    }

    private void assertSyntaxRefusal(final String sql) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
        assertTrue(String.valueOf(ex.getMessage()).contains("syntax error"),
            "expected a syntax error, got: " + ex.getMessage());
    }

    // ── column and table constraints ──────────────────────────────────────────────────────────────

    @Test
    public void aColumnCarriesOneDefaultOrAutoincrement() {
        assertRefusedWith("SQL compilation error: \nMultiple DEFAULT or AUTOINCREMENT expressions"
            + " declared for column N.", "CREATE TABLE d15 (n INT DEFAULT 1 DEFAULT 2)");
        assertRefusedWith("SQL compilation error: \nMultiple DEFAULT or AUTOINCREMENT expressions"
            + " declared for column N.", "CREATE TABLE d16 (n INT DEFAULT 1 AUTOINCREMENT)");
        assertRefusedWith("SQL compilation error: \nMultiple DEFAULT or AUTOINCREMENT expressions"
            + " declared for column N.", "CREATE TABLE d17 (n INT AUTOINCREMENT IDENTITY)");
    }

    @Test
    public void aTableCarriesOnePrimaryKeyHoweverItIsSpelled() {
        assertRefusedWith("SQL compilation error:\nprimary key already exists for table 'D18'",
            "CREATE TABLE d18 (n INT PRIMARY KEY PRIMARY KEY)");
        assertRefusedWith("SQL compilation error:\nprimary key already exists for table 'D19'",
            "CREATE TABLE d19 (a INT PRIMARY KEY, b INT PRIMARY KEY)");
        assertRefusedWith("SQL compilation error:\nprimary key already exists for table 'D20'",
            "CREATE TABLE d20 (n INT PRIMARY KEY, PRIMARY KEY (n))");
        assertRefusedWith("SQL compilation error:\nprimary key already exists for table 'D21'",
            "CREATE TABLE d21 (a INT, b INT, PRIMARY KEY (a), PRIMARY KEY (b))");
    }

    @Test
    public void twoConstraintsOverTheSameColumnsCollide() {
        assertRefusedWith("SQL compilation error:\nconstraint with the same signature already exists"
            + " on table 'D22'", "CREATE TABLE d22 (n INT UNIQUE UNIQUE)");
    }

    // ── what stays legal ──────────────────────────────────────────────────────────────────────────

    @Test
    public void aRepeatedClusterByIsLegal() {
        assertAccepted("CREATE TABLE k1 (n INT) CLUSTER BY (n) CLUSTER BY (n)");
    }

    @Test
    public void aRepeatedCommentIsLegalOnAStageAndOnARoutine() {
        assertAccepted("CREATE STAGE k2 COMMENT = 'a' COMMENT = 'b'");
        assertAccepted("CREATE FUNCTION k3() RETURNS INT COMMENT = 'a' COMMENT = 'b' AS $$ 1 $$");
    }

    @Test
    public void repeatedNullabilityAndCollationAreLegal() {
        assertAccepted("CREATE TABLE k4 (n INT NOT NULL NOT NULL)");
        assertAccepted("CREATE TABLE k5 (n INT NOT NULL NULL)");
        assertAccepted("CREATE TABLE k6 (s VARCHAR COLLATE 'en' COLLATE 'de')");
        assertAccepted("CREATE TABLE k7 (n INT DEFAULT 1 NOT NULL)");
        assertAccepted("CREATE TABLE k8 (n INT NOT NULL DEFAULT 1)");
    }

    @Test
    public void twoColumnsMayEachBeUnique() {
        assertAccepted("CREATE TABLE k9 (a INT UNIQUE, b INT UNIQUE)");
    }

    @Test
    public void optionOrderIsFree() {
        assertAccepted("CREATE TABLE k10 (n INT) COMMENT = 'a' CLUSTER BY (n)");
        assertAccepted("CREATE TABLE k11 (n INT) CLUSTER BY (n) COMMENT = 'a'");
        assertAccepted("CREATE SEQUENCE k12 INCREMENT = 2 START = 5");
        assertAccepted("CREATE WAREHOUSE k13 COMMENT = 'a' WAREHOUSE_SIZE = 'XSMALL'");
        assertAccepted("CREATE TASK k14 WAREHOUSE = 'COMPUTE_WH' COMMENT = 'a' SCHEDULE = '1 MINUTE' AS SELECT 1");
    }

    /** A stage's URL may sit after another option — the grammar anchors it first, live does not. */
    @Test
    public void aStageUrlMayFollowAnotherOption() {
        assertAccepted("CREATE STAGE k15 COMMENT = 'a' URL = 's3://bucket/'");
    }
}
