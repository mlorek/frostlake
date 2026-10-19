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

import org.junit.jupiter.api.Test;

/**
 * A materialized view is tied to the table it was created over, not to the table's name (live-verified): a table
 * swapped in under the name, or created anew there, is refused as not accessible — a read at position 14, a
 * RESUME at position 24 — and the view is invalid for that reason afterwards, while a table swapped back before
 * anything read the view reads as before and SHOW MATERIALIZED VIEWS names the table where it stands now. A
 * RESUME makes a view valid again whose base table changed only a column's type or nullability, not one whose
 * table lost or renamed a column; and a SET or DROP NOT NULL that changes nothing answers so.
 */
public class MaterializedViewBaseTableTest extends MeasuredCellsTestSupport {

    @Test
    public void aSwappedOrRecreatedBaseTableIsAnotherTable() {
        engine.execute("CREATE OR REPLACE TABLE m9 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m9 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE TABLE m9_other (a INT, b INT, c VARCHAR(10))");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m9_mv AS SELECT a, c FROM m9");
        engine.execute("CREATE OR REPLACE TABLE m13 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m13 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m13_mv AS SELECT a, c FROM m13");
        engine.execute("CREATE OR REPLACE TABLE m11 (a INT, b INT, c VARCHAR(10))");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m11_mv AS SELECT a, c FROM m11");
        engine.execute("CREATE OR REPLACE TABLE m1 (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO m1 VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW m1_mv AS SELECT a, c FROM m1");
        assertAnswer("1|x", "SELECT * FROM m9_mv");
        assertAnswer("Statement executed successfully.", "ALTER TABLE m9 SWAP WITH m9_other");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'M9_MV': SQL compilation "
                + "error: error line 1 at position 14 | Invalid materialized view definition. Source table used in "
                + "the definition is not accessible or been deleted.",
            "SELECT * FROM m9_mv");
        assertAnswer("M13 successfully dropped.", "DROP TABLE m13");
        assertAnswer("Table M13 successfully created.", "CREATE TABLE m13 (a INT, b INT, c VARCHAR(10))");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'M13_MV': SQL compilation "
                + "error: error line 1 at position 24 | Invalid materialized view definition. Source table used in "
                + "the definition is not accessible or been deleted.",
            "ALTER MATERIALIZED VIEW m13_mv RESUME");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'M13_MV': SQL compilation "
                + "error: Materialized View M13_MV is invalid. Invalidation reason: SQL compilation error: error line "
                + "1 at position 24 | Invalid materialized view definition. Source table used in the definition is "
                + "not accessible or been deleted.",
            "SELECT * FROM m13_mv");
        assertAnswer("Not null constraint does not exist for column 'B'",
            "ALTER TABLE m11 ALTER COLUMN b DROP NOT NULL");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'M11_MV': SQL compilation "
                + "error: Materialized View M11_MV is invalid. Invalidation reason: DDL Statement was executed on the "
                + "base table 'M11'. Marked Materialized View as invalid.",
            "SELECT * FROM m11_mv");
        assertAnswer("Statement executed successfully.", "ALTER TABLE m1 ADD COLUMN d INT");
        assertAnswer("1|x", "SELECT * FROM m1_mv");
        assertAnswer("Statement executed successfully.", "ALTER MATERIALIZED VIEW m1_mv SUSPEND");
        assertAnswer("Statement executed successfully.", "ALTER MATERIALIZED VIEW m1_mv RESUME");
        assertAnswer("1|x", "SELECT * FROM m1_mv");
        assertAnswer("Statement executed successfully.", "ALTER TABLE m11 ALTER COLUMN b SET NOT NULL");
        assertAnswer("Statement executed successfully.", "ALTER TABLE m11 ALTER COLUMN b DROP NOT NULL");
    }

    @Test
    public void theRefusalStandsAtTheSamePlaceWhateverTheDefinition() {
        engine.execute("CREATE OR REPLACE TABLE mx (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO mx VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE TABLE mx_other (a INT, b INT, c VARCHAR(10))");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mx_mv AS SELECT c FROM mx WHERE a > 0");
        engine.execute("CREATE OR REPLACE TABLE my (a INT, b INT, c VARCHAR(10))");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW my_mv (aa, cc) AS SELECT a, c FROM test_db.test_schema.my");
        engine.execute("CREATE OR REPLACE TABLE mz (a INT, b INT, c VARCHAR(10))");
        engine.execute("CREATE OR REPLACE TABLE mz_other (a INT, b INT, c VARCHAR(10))");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mz_mv COMMENT = 'zzz' AS SELECT a, b, c FROM mz");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mx SWAP WITH mx_other");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'MX_MV': SQL compilation "
                + "error: error line 1 at position 14 | Invalid materialized view definition. Source table used in "
                + "the definition is not accessible or been deleted.",
            "SELECT * FROM mx_mv");
        assertAnswer("MY successfully dropped.", "DROP TABLE my");
        assertAnswer("Table MY successfully created.", "CREATE TABLE my (a INT, b INT, c VARCHAR(10))");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'MY_MV': SQL compilation "
                + "error: Materialized View MY_MV is invalid. Invalidation reason: Base table 'MY' dropped. Marked "
                + "Materialized View as invalid.",
            "SELECT * FROM my_mv");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'MY_MV': SQL compilation "
                + "error: error line 1 at position 24 | Invalid materialized view definition. Source table used in "
                + "the definition is not accessible or been deleted.",
            "ALTER MATERIALIZED VIEW my_mv RESUME");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mz SWAP WITH mz_other");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'MZ_MV': SQL compilation "
                + "error: error line 1 at position 14 | Invalid materialized view definition. Source table used in "
                + "the definition is not accessible or been deleted.",
            "SELECT * FROM mz_mv");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mz SWAP WITH mz_other");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'MZ_MV': SQL compilation "
                + "error: Materialized View MZ_MV is invalid. Invalidation reason: SQL compilation error: error line "
                + "1 at position 14 | Invalid materialized view definition. Source table used in the definition is "
                + "not accessible or been deleted.",
            "SELECT * FROM mz_mv");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mx RENAME TO mx_renamed");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'MX_MV': SQL compilation "
                + "error: Materialized View MX_MV is invalid. Invalidation reason: SQL compilation error: error line "
                + "1 at position 14 | Invalid materialized view definition. Source table used in the definition is "
                + "not accessible or been deleted.",
            "SELECT * FROM mx_mv");
    }

    @Test
    public void aRenameASwapBackAndAResumeOverASwap() {
        engine.execute("CREATE OR REPLACE TABLE ma (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO ma VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE TABLE ma_other (a INT, b INT, c VARCHAR(10))");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW ma_mv AS SELECT a, c FROM ma");
        engine.execute("CREATE OR REPLACE TABLE mb (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO mb VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE TABLE mb_other (a INT, b INT, c VARCHAR(10))");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mb_mv AS SELECT a, c FROM mb");
        engine.execute("CREATE OR REPLACE TABLE mc (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO mc VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE TABLE mc_other (a INT, b INT, c VARCHAR(10))");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mc_mv AS SELECT a, c FROM mc");
        engine.execute("CREATE OR REPLACE TABLE md (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO md VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE TABLE md_other (a INT, b INT, c VARCHAR(10))");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW md_mv AS SELECT a, c FROM md");
        engine.execute("CREATE OR REPLACE TABLE me (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO me VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE TABLE me_other (a INT, b INT, c VARCHAR(10))");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW me_mv AS SELECT a, c FROM me");
        engine.execute("CREATE OR REPLACE TABLE mf (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO mf VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE TABLE mf_other (a INT, b INT, c VARCHAR(10))");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mf_mv AS SELECT a, c FROM mf");
        engine.execute("CREATE OR REPLACE TABLE mg (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO mg VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE TABLE mg_other (a INT, b INT, c VARCHAR(10))");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mg_mv AS SELECT a, c FROM mg");
        assertAnswer("Statement executed successfully.", "ALTER TABLE ma RENAME TO ma_renamed");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'MA_MV': SQL compilation "
                + "error: | Object 'TEST_DB.TEST_SCHEMA.MA' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.MA.",
            "SELECT * FROM ma_mv");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mb SWAP WITH mb_other");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mb SWAP WITH mb_other");
        assertAnswer("1|x", "SELECT * FROM mb_mv");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mc SWAP WITH mc_other");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'MC_MV': SQL compilation "
                + "error: error line 1 at position 24 | Invalid materialized view definition. Source table used in "
                + "the definition is not accessible or been deleted.",
            "ALTER MATERIALIZED VIEW mc_mv RESUME");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'MC_MV': SQL compilation "
                + "error: Materialized View MC_MV is invalid. Invalidation reason: SQL compilation error: error line "
                + "1 at position 24 | Invalid materialized view definition. Source table used in the definition is "
                + "not accessible or been deleted.",
            "SELECT * FROM mc_mv");
        assertAnswer("Statement executed successfully.", "ALTER TABLE md SWAP WITH md_other");
        answer("SHOW MATERIALIZED VIEWS LIKE 'MD_MV'");
        assertAnswer("false|null", "SELECT \"invalid\", \"invalid_reason\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'MD_MV': SQL compilation "
                + "error: error line 1 at position 14 | Invalid materialized view definition. Source table used in "
                + "the definition is not accessible or been deleted.",
            "SELECT * FROM md_mv");
        answer("SHOW MATERIALIZED VIEWS LIKE 'MD_MV'");
        assertAnswer("true|SQL compilation error: error line 1 at position 14 | Invalid materialized view definition. "
                + "Source table used in the definition is not accessible or been deleted.",
            "SELECT \"invalid\", \"invalid_reason\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("Not null constraint does not exist for column 'B'",
            "ALTER TABLE me ALTER COLUMN b DROP NOT NULL");
        assertAnswer("Statement executed successfully.", "ALTER MATERIALIZED VIEW me_mv RESUME");
        assertAnswer("1|x", "SELECT * FROM me_mv");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mf ALTER COLUMN b SET NOT NULL");
        assertAnswer("Not null constraint already exists for column 'B'", "ALTER TABLE mf ALTER COLUMN b SET NOT NULL");
        assertAnswer("Not null constraint does not exist for column 'C'",
            "ALTER TABLE mf MODIFY COLUMN c DROP NOT NULL");
        assertAnswer("Not null constraint does not exist for column 'C'",
            "ALTER TABLE mf ALTER COLUMN b DROP NOT NULL, COLUMN c DROP NOT NULL");
        assertAnswer("Not null constraint does not exist for column 'B'",
            "ALTER TABLE mg ALTER COLUMN b DROP NOT NULL, COLUMN c SET NOT NULL");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mg RENAME TO mg_renamed");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mg_renamed RENAME TO mg");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'MG_MV': SQL compilation "
                + "error: Materialized View MG_MV is invalid. Invalidation reason: DDL Statement was executed on the "
                + "base table 'MG'. Marked Materialized View as invalid.",
            "SELECT * FROM mg_mv");
    }

    @Test
    public void aResumeAfterBaseTableDdl() {
        engine.execute("CREATE OR REPLACE TABLE mh (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO mh VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mh_mv AS SELECT a, c FROM mh");
        engine.execute("CREATE OR REPLACE TABLE mi (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO mi VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mi_mv AS SELECT a, c FROM mi");
        engine.execute("CREATE OR REPLACE TABLE mj (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO mj VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mj_mv AS SELECT a, c FROM mj");
        engine.execute("CREATE OR REPLACE TABLE mk (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO mk VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mk_mv AS SELECT a, c FROM mk");
        engine.execute("CREATE OR REPLACE TABLE ml (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO ml VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW ml_mv AS SELECT a, c FROM ml");
        engine.execute("CREATE OR REPLACE TABLE mm (a INT, b INT, c VARCHAR(10))");
        engine.execute("INSERT INTO mm VALUES (1, 2, 'x')");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mm_mv AS SELECT a, c FROM mm");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mh DROP COLUMN b");
        assertAnswer("Statement executed successfully.", "ALTER MATERIALIZED VIEW mh_mv RESUME");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'MH_MV': DDL Statement was "
                + "executed on the base table 'MH'. Marked Materialized View as invalid.",
            "SELECT * FROM mh_mv");
        assertAnswer("Not null constraint does not exist for column 'B'",
            "ALTER TABLE mi ALTER COLUMN b DROP NOT NULL");
        assertAnswer("Statement executed successfully.", "ALTER MATERIALIZED VIEW mi_mv SUSPEND");
        assertAnswer("Statement executed successfully.", "ALTER MATERIALIZED VIEW mi_mv RESUME");
        assertAnswer("1|x", "SELECT * FROM mi_mv");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mj ALTER COLUMN b SET NOT NULL");
        assertAnswer("Statement executed successfully.", "ALTER MATERIALIZED VIEW mj_mv RESUME");
        assertAnswer("1|x", "SELECT * FROM mj_mv");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mk RENAME COLUMN b TO bb");
        assertAnswer("Statement executed successfully.", "ALTER MATERIALIZED VIEW mk_mv RESUME");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'MK_MV': DDL Statement was "
                + "executed on the base table 'MK'. Marked Materialized View as invalid.",
            "SELECT * FROM mk_mv");
        answer("SHOW MATERIALIZED VIEWS LIKE 'MK_MV'");
        assertAnswer("true|DDL Statement was executed on the base table 'MK'. Marked Materialized View as invalid.",
            "SELECT \"invalid\", \"invalid_reason\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("Statement executed successfully.", "ALTER TABLE ml ALTER COLUMN c SET NOT NULL");
        assertAnswer("Not null constraint already exists for column 'C'",
            "ALTER TABLE ml ALTER COLUMN b DROP NOT NULL, COLUMN c SET NOT NULL");
        assertAnswer("Not null constraint does not exist for column 'B'",
            "ALTER TABLE ml ALTER COLUMN c SET NOT NULL, COLUMN b DROP NOT NULL");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mm ALTER COLUMN b SET DATA TYPE INT");
        assertAnswer("Statement executed successfully.", "ALTER MATERIALIZED VIEW mm_mv RESUME");
        assertAnswer("1|x", "SELECT * FROM mm_mv");
        assertAnswer("Statement executed successfully.", "ALTER TABLE mm ADD COLUMN d INT");
        assertAnswer("1|x", "SELECT * FROM mm_mv");
    }
}
