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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Refusals that carry the {@code SQL compilation error:} prefix, and the ones that deliberately do not.
 *
 * <p>★ THE PREFIX IS NOT DECORATION. {@code SqlCompilationError.isCompilationError} keys off it, and
 * the view-body resolution uses that to tell a body which will NOT COMPILE from one that merely failed
 * while producing rows. Without it a CREATE VIEW over such a body was created with NULL COLUMNS instead
 * of being refused — the same consequence the window-function-type and semi-structured families had.
 *
 * <p>★ SOME REFUSALS ARE GENUINELY PREFIX-LESS, which is why this could not be a blanket rule: live
 * prints {@code Numeric value 'abc' is not recognized} and {@code Division by zero} bare, because they
 * happen while producing a row rather than while compiling. The presence of a bare throw is not itself
 * the bug — the question is whether live calls it a compilation error, and that has to be measured one
 * family at a time.
 *
 * <p>★ THE SET-OPERATION SENTENCE WAS ALSO WRONG, not only unprefixed: live names the expected and
 * actual column counts AND the branch number, where Frostlake had one flat sentence for every case.
 * The already-exists family is live's GENERIC object sentence rather than a table- or view-specific
 * one, and the unknown-table-function sentence has no colon.
 *
 * <p>Left for their own tasks, measured here and NOT fixed: the argument-type family, whose prefix
 * carries a POSITION rather than standing bare; a duplicate column name in CREATE TABLE, which
 * Frostlake accepts outright; and an unknown data type, refused as a syntax error where live names the
 * type.
 */
public class CompilationPrefixSweepTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE cp (i INT, n NUMBER(10,2), g VARCHAR(10), f FLOAT)");
        engine.execute("INSERT INTO cp VALUES (1, 1.00, 'a', 1.5)");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("ACCEPTED:");
            while (rs.next()) {
                all.append(" ").append(String.valueOf(rs.getValue(0)));
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** ★ A set operation whose branches differ in WIDTH — prefix, counts and branch number. */
    @Test
    public void asetOperationWidthNamesTheCountsAndTheBranch() {
        assertEquals("SQL compilation error:|invalid number of result columns for set operator"
            + " input branches, expected 1, got 2 in branch 2",
            answer("SELECT i FROM cp UNION ALL SELECT i, n FROM cp"));
        assertEquals("SQL compilation error:|invalid number of result columns for set operator"
            + " input branches, expected 3, got 1 in branch 2",
            answer("SELECT i, n, g FROM cp INTERSECT SELECT i FROM cp"),
            "the counts follow the query, not a fixed pair");
        assertEquals("SQL compilation error:|invalid number of result columns for set operator"
            + " input branches, expected 1, got 2 in branch 2",
            answer("SELECT i FROM cp EXCEPT SELECT i, n FROM cp"),
            "and every operator says it the same way");
    }

    /** ★ An object that already exists is refused with live's GENERIC sentence. */
    @Test
    public void analreadyExistingObjectUsesTheGenericSentence() {
        assertEquals("SQL compilation error:|Object 'CP' already exists.",
            answer("CREATE TABLE cp (i INT)"));
        engine.execute("CREATE OR REPLACE VIEW v_cp AS SELECT 1 AS c FROM cp");
        assertEquals("SQL compilation error:|Object 'V_CP' already exists.",
            answer("CREATE VIEW v_cp AS SELECT 1 AS c FROM cp"),
            "a view says OBJECT too, not VIEW");
    }

    /** An unknown TABLE FUNCTION — prefixed, and with no colon before the name. */
    @Test
    public void anUnknownTableFunctionIsPrefixed() {
        assertEquals("SQL compilation error:|Unknown table function NOSUCHTABLEFN",
            answer("SELECT * FROM TABLE(NOSUCHTABLEFN(1))"));
    }

    /** An INSERT whose value list is the wrong width. */
    @Test
    public void aninsertWidthMismatchIsPrefixed() {
        assertEquals("SQL compilation error:|Insert value list does not match column list"
            + " expecting 4 but got 1",
            answer("INSERT INTO cp VALUES (1)"));
    }

    /** ★ A tuple IN of the wrong width — the sentence was already right, only the prefix was missing. */
    @Test
    public void atupleInWidthMismatchIsPrefixed() {
        assertEquals("SQL compilation error:|Can not convert parameter 'ROW(CP.I, CP.N, CP.G)' of type"
            + " [ROW(NUMBER(38,0), NUMBER(10,2), VARCHAR(10))] into expected type"
            + " [ROW(NUMBER(38,0), NUMBER(10,2))]",
            answer("SELECT * FROM cp WHERE (i, n) IN ((i, n, g))"));
    }

    /** ★ The ROW-TIME refusals stay bare on BOTH engines — the reason this is not a blanket rule. */
    @Test
    public void therowTimeRefusalsStayBare() {
        assertEquals("Numeric value 'abc' is not recognized", answer("SELECT 'abc'::NUMBER FROM cp"));
        assertEquals("Division by zero", answer("SELECT 1 / 0 FROM cp"));
    }

    /** A missing relation was already prefixed and must not move. */
    @Test
    public void amissingRelationIsUntouched() {
        assertEquals(hinted("SQL compilation error:|Table 'TEST_DB.TEST_SCHEMA.NOSUCHTABLE_XYZ'"
            + " does not exist or not authorized."),
            answer("DROP TABLE nosuchtable_xyz"));
    }
}
