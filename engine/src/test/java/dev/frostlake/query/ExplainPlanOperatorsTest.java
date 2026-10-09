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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The operators EXPLAIN lists, with their objects, their partitions and the operator each feeds, statement by
 * statement: three tables of three, two and two rows, an empty one and one of a hundred. A join lists the smaller
 * table first — the right one of two as large, the left one for a FULL JOIN, the side not kept for a LEFT or a
 * RIGHT JOIN — and an equality makes it an InnerJoin with a JoinFilter over the other side, anything else a
 * CartesianJoin. A view reads as its query, a sampled source stands under operators that are numbered and not
 * listed, a set operation is a UnionAll, a SemiJoin or an AntiJoin under its Aggregates, and a statement that
 * writes is a Result over the write over what it reads. Every answer is live-verified.
 */
public class ExplainPlanOperatorsTest extends MeasuredCellsTestSupport {

    /** A plan without its expressions: each operator, its objects, its partitions and the operator it feeds. */
    private static final String OPERATORS = " ->> SELECT \"operation\", \"objects\", \"partitionsTotal\", "
        + "\"partitionsAssigned\", \"bytesAssigned\", \"parentOperators\" FROM $1";

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE et (a INT, b VARCHAR)");
        engine.execute("INSERT INTO et VALUES (1, 'x'), (2, 'y'), (3, 'z')");
        engine.execute("CREATE OR REPLACE TABLE eu (a INT, c VARCHAR)");
        engine.execute("INSERT INTO eu VALUES (1, 'p'), (2, 'q')");
        engine.execute("CREATE OR REPLACE TABLE ev (a INT, d VARCHAR)");
        engine.execute("INSERT INTO ev VALUES (2, 'm'), (3, 'n')");
        engine.execute("CREATE OR REPLACE TABLE ee (a INT)");
        engine.execute("CREATE OR REPLACE TABLE ef (a INT)");
        engine.execute("CREATE OR REPLACE TABLE eb (a INT)");
        engine.execute("INSERT INTO eb SELECT SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => 100))");
        engine.execute("CREATE OR REPLACE VIEW evw AS SELECT a FROM et WHERE a > 1");
        engine.execute("CREATE OR REPLACE VIEW evg AS SELECT b, SUM(a) s FROM et GROUP BY b");
        engine.execute("CREATE OR REPLACE VIEW evj AS SELECT et.a, eu.c FROM et JOIN eu ON et.a = eu.a");
        engine.execute("CREATE OR REPLACE VIEW evv AS SELECT a FROM evw WHERE a < 3");
        engine.execute("CREATE OR REPLACE VIEW evo AS SELECT a FROM et ORDER BY a");
        engine.execute("CREATE OR REPLACE VIEW evl AS SELECT a FROM et LIMIT 2");
        engine.execute("CREATE OR REPLACE STAGE est");
    }

    private void assertPlan(final String operators, final String statement) {
        assertAnswer(operators, "EXPLAIN " + statement + OPERATORS);
    }

    @Test
    public void aStatementThatWrites() {
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; "
                + "Insert|TEST_DB.TEST_SCHEMA.EE|null|null|null|[0] ;; ValuesClause|null|null|null|null|[1]",
            "INSERT INTO ee VALUES (1)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Insert|TEST_DB.TEST_SCHEMA.EE|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "INSERT INTO ee SELECT a FROM et");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Update|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "UPDATE et SET b = 'q' WHERE a = 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Update|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "UPDATE et SET b = 'q'");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Delete|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "DELETE FROM et WHERE a = 1");
        assertPlan(
            "GlobalStats|null|1|0|0|null ;; Result|null|null|null|null|null ;; "
                + "Delete|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|0|0|[1]",
            "DELETE FROM et");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Insert|TEST_DB.TEST_SCHEMA.EE|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "INSERT INTO ee SELECT a FROM et WHERE a > 1");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Update|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; InnerJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; JoinFilter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4]",
            "UPDATE et SET b = eu.c FROM eu WHERE et.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Delete|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; InnerJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; JoinFilter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4]",
            "DELETE FROM et USING eu WHERE et.a = eu.a");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; "
                + "Delete|TEST_DB.TEST_SCHEMA.EE|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EE|0|0|0|[1]",
            "DELETE FROM ee");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; "
                + "Update|TEST_DB.TEST_SCHEMA.EE|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EE|0|0|0|[1]",
            "UPDATE ee SET a = 1");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; "
                + "Insert|TEST_DB.TEST_SCHEMA.EE|null|null|null|[0] ;; ValuesClause|null|null|null|null|[1]",
            "INSERT INTO ee VALUES (1), (2)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Insert|TEST_DB.TEST_SCHEMA.EE|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "INSERT OVERWRITE INTO ee SELECT a FROM et");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Insert|TEST_DB.TEST_SCHEMA.EE|null|null|null|[0] ;; UnionAll|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2]",
            "INSERT INTO ee SELECT a FROM et UNION ALL SELECT a FROM eu");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; "
                + "Insert|TEST_DB.TEST_SCHEMA.EE|null|null|null|[0] ;; Generator|null|null|null|null|[1]",
            "INSERT INTO ee SELECT 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Insert|TEST_DB.TEST_SCHEMA.EE|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "INSERT ALL INTO ee INTO ee SELECT a FROM et");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Insert|TEST_DB.TEST_SCHEMA.EE|null|null|null|[0] ;; Limit|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "INSERT INTO ee SELECT a FROM et LIMIT 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Insert|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1]",
            "INSERT INTO et SELECT a, c FROM eu");
        assertPlan(
            "GlobalStats|null|3|3|3072|null ;; Result|null|null|null|null|null ;; "
                + "Delete|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; InnerJoin|null|null|null|null|[1] ;; "
                + "InnerJoin|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[3] ;; "
                + "JoinFilter|null|null|null|null|[3] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[5] ;; "
                + "JoinFilter|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[7]",
            "DELETE FROM et USING eu, ev WHERE et.a = eu.a AND eu.a = ev.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; InnerJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; JoinFilter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4]",
            "MERGE INTO et USING eu ON et.a = eu.a WHEN MATCHED THEN UPDATE SET b = eu.c");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; AntiJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; Aggregate|null|null|null|null|[2] ;; "
                + "JoinFilter|null|null|null|null|[4] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[5]",
            "MERGE INTO et USING eu ON et.a = eu.a WHEN NOT MATCHED THEN INSERT (a, b) VALUES (eu.a, eu.c)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; InnerJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; JoinFilter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4]",
            "MERGE INTO et USING eu ON et.a = eu.a WHEN MATCHED THEN DELETE");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; LeftOuterJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; Filter|null|null|null|null|[2] ;; "
                + "JoinFilter|null|null|null|null|[4] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[5]",
            "MERGE INTO et USING eu ON et.a = eu.a WHEN MATCHED THEN UPDATE SET b = eu.c WHEN NOT MATCHED THEN "
                + "INSERT (a, b) VALUES (eu.a, eu.c)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; LeftOuterJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; Filter|null|null|null|null|[2] ;; "
                + "JoinFilter|null|null|null|null|[4] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[5]",
            "MERGE INTO et USING eu ON et.a = eu.a WHEN MATCHED AND eu.a > 1 THEN DELETE WHEN MATCHED THEN UPDATE "
                + "SET b = eu.c WHEN NOT MATCHED THEN INSERT (a, b) VALUES (eu.a, eu.c)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.EE|null|null|null|[0] ;; AntiJoin|null|null|null|null|[1] ;; "
                + "Aggregate|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EE|0|0|0|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2]",
            "MERGE INTO ee USING eu ON ee.a = eu.a WHEN NOT MATCHED THEN INSERT (a) VALUES (eu.a)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.EU|null|null|null|[0] ;; InnerJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; JoinFilter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4]",
            "MERGE INTO eu USING et ON et.a = eu.a WHEN MATCHED THEN UPDATE SET c = et.b");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.EU|null|null|null|[0] ;; AntiJoin|null|null|null|null|[1] ;; "
                + "Aggregate|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "MERGE INTO eu USING et ON et.a = eu.a WHEN NOT MATCHED THEN INSERT (a, c) VALUES (et.a, et.b)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.EU|null|null|null|[0] ;; LeftOuterJoin|null|null|null|null|[1] ;; "
                + "Filter|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "MERGE INTO eu USING et ON et.a = eu.a WHEN MATCHED THEN UPDATE SET c = et.b WHEN NOT MATCHED THEN "
                + "INSERT (a, c) VALUES (et.a, et.b)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.EB|null|null|null|[0] ;; InnerJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; JoinFilter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[4]",
            "MERGE INTO eb USING et ON et.a = eb.a WHEN MATCHED THEN DELETE");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; InnerJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; JoinFilter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[4]",
            "MERGE INTO et USING eb ON et.a = eb.a WHEN MATCHED THEN DELETE");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.EB|null|null|null|[0] ;; AntiJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; Aggregate|null|null|null|null|[2] ;; "
                + "JoinFilter|null|null|null|null|[4] ;; TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[5]",
            "MERGE INTO eb USING et ON et.a = eb.a WHEN NOT MATCHED THEN INSERT (a) VALUES (et.a)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; AntiJoin|null|null|null|null|[1] ;; "
                + "Aggregate|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[2]",
            "MERGE INTO et USING eb ON et.a = eb.a WHEN NOT MATCHED THEN INSERT (a) VALUES (eb.a)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.EB|null|null|null|[0] ;; LeftOuterJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; Filter|null|null|null|null|[2] ;; "
                + "JoinFilter|null|null|null|null|[4] ;; TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[5]",
            "MERGE INTO eb USING et ON et.a = eb.a WHEN MATCHED THEN DELETE WHEN NOT MATCHED THEN INSERT (a) "
                + "VALUES (et.a)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; LeftOuterJoin|null|null|null|null|[1] ;; "
                + "Filter|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[2]",
            "MERGE INTO et USING eb ON et.a = eb.a WHEN MATCHED THEN DELETE WHEN NOT MATCHED THEN INSERT (a) "
                + "VALUES (eb.a)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; InnerJoin|null|null|null|null|[1] ;; "
                + "ValuesClause|null|null|null|null|[2] ;; JoinFilter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4]",
            "MERGE INTO et USING (VALUES (1, 'k')) s (a, c) ON et.a = s.a WHEN MATCHED THEN UPDATE SET b = s.c");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; InnerJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; JoinFilter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4]",
            "MERGE INTO et USING eu ON et.a = eu.a WHEN MATCHED AND eu.a > 1 THEN UPDATE SET b = eu.c");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; AntiJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; Aggregate|null|null|null|null|[2] ;; "
                + "JoinFilter|null|null|null|null|[4] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[5]",
            "MERGE INTO et USING eu ON et.a = eu.a WHEN NOT MATCHED AND eu.a > 1 THEN INSERT (a) VALUES (eu.a)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Update|TEST_DB.TEST_SCHEMA.EU|null|null|null|[0] ;; InnerJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; JoinFilter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4]",
            "UPDATE eu SET c = et.b FROM et WHERE et.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Delete|TEST_DB.TEST_SCHEMA.EU|null|null|null|[0] ;; InnerJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; JoinFilter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4]",
            "DELETE FROM eu USING et WHERE et.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Update|TEST_DB.TEST_SCHEMA.EB|null|null|null|[0] ;; InnerJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; JoinFilter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[4]",
            "UPDATE eb SET a = 1 FROM et WHERE et.a = eb.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Delete|TEST_DB.TEST_SCHEMA.EB|null|null|null|[0] ;; InnerJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; JoinFilter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[4]",
            "DELETE FROM eb USING et WHERE et.a = eb.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Update|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; CartesianJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "UPDATE et SET b = 'q' FROM eu WHERE et.a > eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Delete|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; CartesianJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "DELETE FROM et USING eu WHERE et.a > eu.a");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Update|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "UPDATE et SET b = 'q' WHERE a > 1 AND b = 'y'");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Delete|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "DELETE FROM et WHERE a = 2");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Update|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "UPDATE et x SET b = 'q' WHERE x.a = 2");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Delete|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "DELETE FROM et x WHERE x.a = 2");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Insert|TEST_DB.TEST_SCHEMA.EE, "
                + "TEST_DB.TEST_SCHEMA.EF|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "INSERT ALL INTO ee INTO ef SELECT a FROM et");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Insert|TEST_DB.TEST_SCHEMA.EE, "
                + "TEST_DB.TEST_SCHEMA.EF|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "INSERT FIRST WHEN a > 1 THEN INTO ee ELSE INTO ef SELECT a FROM et");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Insert|TEST_DB.TEST_SCHEMA.EE, "
                + "TEST_DB.TEST_SCHEMA.EF|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "INSERT ALL WHEN a > 1 THEN INTO ee WHEN a > 2 THEN INTO ef SELECT a FROM et");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Insert|TEST_DB.TEST_SCHEMA.EE, "
                + "TEST_DB.TEST_SCHEMA.EF|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "INSERT OVERWRITE ALL INTO ee INTO ef SELECT a FROM et");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Insert|TEST_DB.TEST_SCHEMA.EF, "
                + "TEST_DB.TEST_SCHEMA.EE|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "INSERT ALL INTO ef INTO ee SELECT a FROM et WHERE a > 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Insert|TEST_DB.TEST_SCHEMA.EE, "
                + "TEST_DB.TEST_SCHEMA.EF|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "INSERT ALL INTO ee (a) VALUES (a) INTO ef (a) VALUES (a + 1) SELECT a FROM et");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; "
                + "Insert|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; ExternalScan|null|null|null|null|[1]",
            "COPY INTO et FROM @est");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; "
                + "Insert|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; ExternalScan|null|null|null|null|[1]",
            "COPY INTO et FROM @%et");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; "
                + "Insert|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; ExternalScan|null|null|null|null|[1]",
            "COPY INTO et FROM @est/sub/ FILE_FORMAT = (TYPE = CSV)");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; "
                + "Insert|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; ExternalScan|null|null|null|null|[1]",
            "COPY INTO et FROM (SELECT $1, $2 FROM @est)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Unload|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "COPY INTO @est/out FROM et");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Unload|null|null|null|null|[0] "
                + ";; Filter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "COPY INTO @est/out FROM (SELECT a FROM et WHERE a > 1)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Unload|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "COPY INTO @%et FROM et");
    }

    @Test
    public void aStatementThatWritesFurther() {
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; "
                + "Insert|TEST_DB.TEST_SCHEMA.EE|null|null|null|[0] ;; ExternalScan|null|null|null|null|[1]",
            "COPY INTO ee FROM @est");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; Unload|null|null|null|null|[0] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EE|0|0|0|[1]",
            "COPY INTO @est/out FROM ee");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Insert|TEST_DB.TEST_SCHEMA.EE|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "INSERT INTO ee SELECT a FROM evw");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.EU|null|null|null|[0] ;; InnerJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; JoinFilter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[4]",
            "MERGE INTO eu USING ev ON eu.a = ev.a WHEN MATCHED THEN DELETE");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.EV|null|null|null|[0] ;; InnerJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[2] ;; JoinFilter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[4]",
            "MERGE INTO ev USING eu ON eu.a = ev.a WHEN MATCHED THEN DELETE");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.EU|null|null|null|[0] ;; AntiJoin|null|null|null|null|[1] ;; "
                + "Aggregate|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[2]",
            "MERGE INTO eu USING ev ON eu.a = ev.a WHEN NOT MATCHED THEN INSERT (a) VALUES (ev.a)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.EV|null|null|null|[0] ;; AntiJoin|null|null|null|null|[1] ;; "
                + "Aggregate|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2]",
            "MERGE INTO ev USING eu ON eu.a = ev.a WHEN NOT MATCHED THEN INSERT (a) VALUES (eu.a)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.EU|null|null|null|[0] ;; LeftOuterJoin|null|null|null|null|[1] ;; "
                + "Filter|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[2]",
            "MERGE INTO eu USING ev ON eu.a = ev.a WHEN MATCHED THEN DELETE WHEN NOT MATCHED THEN INSERT (a) "
                + "VALUES (ev.a)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.EV|null|null|null|[0] ;; LeftOuterJoin|null|null|null|null|[1] ;; "
                + "Filter|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2]",
            "MERGE INTO ev USING eu ON eu.a = ev.a WHEN MATCHED THEN DELETE WHEN NOT MATCHED THEN INSERT (a) "
                + "VALUES (eu.a)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Update|TEST_DB.TEST_SCHEMA.EU|null|null|null|[0] ;; InnerJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; JoinFilter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[4]",
            "UPDATE eu SET c = ev.d FROM ev WHERE eu.a = ev.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Update|TEST_DB.TEST_SCHEMA.EV|null|null|null|[0] ;; InnerJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[2] ;; JoinFilter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[4]",
            "UPDATE ev SET d = eu.c FROM eu WHERE eu.a = ev.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Delete|TEST_DB.TEST_SCHEMA.EU|null|null|null|[0] ;; InnerJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; JoinFilter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[4]",
            "DELETE FROM eu USING ev WHERE eu.a = ev.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Delete|TEST_DB.TEST_SCHEMA.EV|null|null|null|[0] ;; InnerJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[2] ;; JoinFilter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[4]",
            "DELETE FROM ev USING eu WHERE eu.a = ev.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Insert|TEST_DB.TEST_SCHEMA.EE|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "UnionAll|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[3]",
            "INSERT INTO ee SELECT a FROM et UNION SELECT a FROM eu");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Insert|TEST_DB.TEST_SCHEMA.EE|null|null|null|[0] ;; InnerJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; JoinFilter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4]",
            "INSERT INTO ee SELECT et.a FROM et JOIN eu ON et.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; Unload|null|null|null|null|[0] "
                + ";; InnerJoin|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; "
                + "JoinFilter|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4]",
            "COPY INTO @est/out FROM (SELECT et.a FROM et JOIN eu ON et.a = eu.a)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Unload|null|null|null|null|[0] "
                + ";; Filter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "COPY INTO @est/out FROM evw");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; "
                + "Insert|TEST_DB.TEST_SCHEMA.EE|null|null|null|[0] ;; ValuesClause|null|null|null|null|[1]",
            "INSERT INTO ee (a) VALUES (1), (2), (3)");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; "
                + "Insert|TEST_DB.TEST_SCHEMA.EE|null|null|null|[0] ;; ValuesClause|null|null|null|null|[1]",
            "INSERT INTO ee SELECT * FROM VALUES (1), (2)");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; "
                + "Insert|TEST_DB.TEST_SCHEMA.EE|null|null|null|[0] ;; ValuesClause|null|null|null|null|[1]",
            "INSERT OVERWRITE INTO ee VALUES (1)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Merge|TEST_DB.TEST_SCHEMA.ET|null|null|null|[0] ;; AntiJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; Aggregate|null|null|null|null|[2] ;; "
                + "JoinFilter|null|null|null|null|[4] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[5]",
            "MERGE INTO et USING (SELECT a FROM eu) s ON et.a = s.a WHEN NOT MATCHED THEN INSERT (a) VALUES (s.a)");
    }

    @Test
    public void aSampledSource() {
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM et SAMPLE (50)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT * FROM et SAMPLE (50) WHERE a > 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT * FROM et TABLESAMPLE BERNOULLI (2 ROWS)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM et SAMPLE SYSTEM (50)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4]",
            "SELECT et.a FROM et SAMPLE (50) JOIN eu ON et.a = eu.a");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM (SELECT a FROM et) SAMPLE (50)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Sort|null|null|null|null|[0] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT * FROM et SAMPLE (50) ORDER BY a");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM et SAMPLE (100)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT b, COUNT(*) FROM et SAMPLE (50) GROUP BY b");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT * FROM et SAMPLE (2 ROWS)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM et SAMPLE SYSTEM (50) SEED (1)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM et SAMPLE BLOCK (50)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM et SAMPLE ROW (50)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[5]",
            "SELECT * FROM et x SAMPLE (50) JOIN eu SAMPLE (50) ON x.a = eu.a");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[1] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT * FROM evw SAMPLE (50)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM et SAMPLE (0)");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EE|0|0|0|[1]",
            "SELECT * FROM ee SAMPLE (50)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[3] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[5]",
            "SELECT * FROM et JOIN eu SAMPLE (1 ROWS) ON et.a = eu.a");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM et x SAMPLE (50)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[2] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "SELECT * FROM (SELECT * FROM et WHERE a > 1) SAMPLE (10 ROWS)");
    }

    @Test
    public void aViewATableFunctionAndAValuesList() {
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[0]",
            "WITH c AS (SELECT a FROM et) SELECT * FROM c");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; "
                + "ValuesClause|null|null|null|null|[0]",
            "SELECT * FROM (VALUES (1), (2)) v (n)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "WITH c AS (SELECT a FROM et), d AS (SELECT a FROM c WHERE a > 1) SELECT * FROM d");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Sort|null|null|null|null|[0] ;; "
                + "Filter|null|null|null|null|[1] ;; Aggregate|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "WITH c AS (SELECT b, SUM(a) s FROM et GROUP BY b) SELECT * FROM c WHERE s > 1 ORDER BY s");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "WITH c (n) AS (SELECT a FROM et) SELECT n FROM c WHERE n > 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM evw");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM evw WHERE a < 3");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; Flatten|null|null|null|null|[0] ;; "
                + "ValuesClause|null|null|null|null|[1]",
            "SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[1,2]')))");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Flatten|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM et, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(et.a)))");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Flatten|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM et, LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(et.a))");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; Generator|null|null|null|null|[0]",
            "SELECT SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => 3))");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; "
                + "ValuesClause|null|null|null|null|[0]",
            "SELECT * FROM VALUES (1), (2)");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; "
                + "ValuesClause|null|null|null|null|[0]",
            "SELECT $1 FROM (VALUES (1), (2))");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] ;; "
                + "ValuesClause|null|null|null|null|[1]",
            "SELECT * FROM (VALUES (1), (2)) v (n) WHERE n > 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; ValuesClause|null|null|null|null|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "SELECT * FROM (VALUES (1)) v (n) JOIN et ON et.a = v.n");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM et PIVOT (SUM(a) FOR b IN ('x', 'y'))");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Unpivot|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM et UNPIVOT (v FOR n IN (a))");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; Flatten|null|null|null|null|[0] ;; "
                + "ValuesClause|null|null|null|null|[1]",
            "SELECT f.value FROM TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1, 2))) f");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; Flatten|null|null|null|null|[0] "
                + ";; InnerJoin|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; "
                + "JoinFilter|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4]",
            "SELECT f.value FROM et JOIN eu ON et.a = eu.a, LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(et.a)) f");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Flatten|null|null|null|null|[0] "
                + ";; Flatten|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT f.value, g.value FROM et, LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(et.a)) f, LATERAL "
                + "FLATTEN(INPUT => ARRAY_CONSTRUCT(f.value)) g");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Flatten|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT f.value FROM et, LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(et.a), OUTER => TRUE) f");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; Flatten|null|null|null|null|[0] ;; "
                + "ValuesClause|null|null|null|null|[1]",
            "SELECT f.value FROM LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(1)) f");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "SortWithLimit|null|null|null|null|[0] ;; Flatten|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT f.value FROM et, LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(et.a)) f ORDER BY 1 LIMIT 1");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; Sort|null|null|null|null|[0] ;; "
                + "Generator|null|null|null|null|[1]",
            "SELECT SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => 3)) ORDER BY 1");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; Generator|null|null|null|null|[0]",
            "SELECT 1 FROM TABLE(GENERATOR(ROWCOUNT => 3))");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; Generator|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT et.a FROM et, TABLE(GENERATOR(ROWCOUNT => 2))");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; Flatten|null|null|null|null|[0] ;; "
                + "ValuesClause|null|null|null|null|[1]",
            "SELECT s.value FROM TABLE(SPLIT_TO_TABLE('a,b', ',')) s");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Flatten|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT s.value FROM et, TABLE(SPLIT_TO_TABLE(et.b, ',')) s");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; Aggregate|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT * FROM et PIVOT (SUM(a) FOR b IN ('x', 'y')) WHERE \"'x'\" > 0");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; Unpivot|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT * FROM et UNPIVOT (v FOR n IN (a)) WHERE v > 1");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; Filter|null|null|null|null|[1] ;; "
                + "JoinFilter|null|null|null|null|[4] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[5]",
            "SELECT * FROM evw v JOIN evw w ON v.a = w.a");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM evg");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; Aggregate|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT * FROM evg WHERE s > 1");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "SELECT * FROM evj");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM evv");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Limit|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM evl");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT COUNT(*) FROM evw");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Flatten|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1]",
            "SELECT * FROM eu JOIN LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(eu.a)) f");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Flatten|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1]",
            "SELECT * FROM eu CROSS JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(eu.a))) f");
    }

    @Test
    public void aSetOperation() {
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "UnionAll|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1]",
            "SELECT a FROM et UNION ALL SELECT a FROM eu");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; UnionAll|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2]",
            "SELECT a FROM et UNION SELECT a FROM eu");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "UnionAll|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.EE|0|0|0|[1]",
            "SELECT a FROM et UNION ALL SELECT a FROM eu UNION ALL SELECT a FROM ee");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "SemiJoin|null|null|null|null|[2] ;; Aggregate|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[4] ;; Aggregate|null|null|null|null|[3] ;; "
                + "JoinFilter|null|null|null|null|[6] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[7]",
            "SELECT a FROM et INTERSECT SELECT a FROM eu");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "AntiJoin|null|null|null|null|[2] ;; Aggregate|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[4] ;; Aggregate|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[6]",
            "SELECT a FROM et EXCEPT SELECT a FROM eu");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "AntiJoin|null|null|null|null|[2] ;; Aggregate|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[4] ;; Aggregate|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[6]",
            "SELECT a FROM et MINUS SELECT a FROM eu");
        assertPlan(
            "GlobalStats|null|3|3|3072|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; UnionAll|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; Aggregate|null|null|null|null|[2] ;; "
                + "Aggregate|null|null|null|null|[4] ;; SemiJoin|null|null|null|null|[5] ;; "
                + "Aggregate|null|null|null|null|[6] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[7] ;; "
                + "Aggregate|null|null|null|null|[6] ;; JoinFilter|null|null|null|null|[9] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[10]",
            "SELECT a FROM et UNION SELECT a FROM eu INTERSECT SELECT a FROM ev");
        assertPlan(
            "GlobalStats|null|3|3|3072|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "AntiJoin|null|null|null|null|[2] ;; Aggregate|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[4] ;; Aggregate|null|null|null|null|[3] ;; "
                + "UnionAll|null|null|null|null|[6] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[7] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[7]",
            "SELECT a FROM et UNION ALL SELECT a FROM eu EXCEPT SELECT a FROM ev");
        assertPlan(
            "GlobalStats|null|3|3|3072|null ;; Result|null|null|null|null|null ;; "
                + "UnionAll|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "Aggregate|null|null|null|null|[2] ;; SemiJoin|null|null|null|null|[3] ;; "
                + "Aggregate|null|null|null|null|[4] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[5] ;; "
                + "Aggregate|null|null|null|null|[4] ;; JoinFilter|null|null|null|null|[7] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[8] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[1]",
            "SELECT a FROM et INTERSECT SELECT a FROM eu UNION ALL SELECT a FROM ev");
        assertPlan(
            "GlobalStats|null|3|3|3072|null ;; Result|null|null|null|null|null ;; "
                + "UnionAll|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "UnionAll|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[3] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[1]",
            "SELECT a FROM et UNION SELECT a FROM eu UNION ALL SELECT a FROM ev");
        assertPlan(
            "GlobalStats|null|3|3|3072|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; UnionAll|null|null|null|null|[1] ;; "
                + "UnionAll|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[3] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[2]",
            "SELECT a FROM et UNION ALL SELECT a FROM eu UNION SELECT a FROM ev");
        assertPlan(
            "GlobalStats|null|3|3|3072|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; UnionAll|null|null|null|null|[1] ;; "
                + "Aggregate|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3] ;; "
                + "Aggregate|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[5] ;; "
                + "Aggregate|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[7]",
            "SELECT a FROM et UNION SELECT a FROM eu UNION SELECT a FROM ev");
        assertPlan(
            "GlobalStats|null|3|3|3072|null ;; Result|null|null|null|null|null ;; "
                + "UnionAll|null|null|null|null|[0] ;; UnionAll|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[1]",
            "(SELECT a FROM et UNION ALL SELECT a FROM eu) UNION ALL SELECT a FROM ev");
        assertPlan(
            "GlobalStats|null|3|3|3072|null ;; Result|null|null|null|null|null ;; "
                + "UnionAll|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1] ;; "
                + "UnionAll|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[3]",
            "SELECT a FROM et UNION ALL (SELECT a FROM eu UNION ALL SELECT a FROM ev)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; Sort|null|null|null|null|[0] ;; "
                + "UnionAll|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2]",
            "SELECT a FROM et UNION ALL SELECT a FROM eu ORDER BY a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; Limit|null|null|null|null|[0] "
                + ";; UnionAll|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2]",
            "SELECT a FROM et UNION ALL SELECT a FROM eu LIMIT 2");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "SemiJoin|null|null|null|null|[2] ;; Aggregate|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[4] ;; Aggregate|null|null|null|null|[3] ;; "
                + "JoinFilter|null|null|null|null|[6] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[7]",
            "SELECT a, b FROM et INTERSECT SELECT a, c FROM eu");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "UnionAll|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1]",
            "SELECT a FROM et UNION ALL BY NAME SELECT a FROM eu");
        assertPlan(
            "GlobalStats|null|3|3|3072|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; UnionAll|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; UnionAll|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[4] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[4]",
            "SELECT a FROM et UNION (SELECT a FROM eu UNION ALL SELECT a FROM ev)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "UnionAll|null|null|null|null|[0] ;; SortWithLimit|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; SortWithLimit|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[4]",
            "(SELECT a FROM et ORDER BY a LIMIT 1) UNION ALL (SELECT a FROM eu ORDER BY a LIMIT 1)");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; UnionAll|null|null|null|null|[0] "
                + ";; Generator|null|null|null|null|[1] ;; Generator|null|null|null|null|[1]",
            "SELECT 1 UNION ALL SELECT 2");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "UnionAll|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[4]",
            "SELECT a FROM et WHERE a > 1 UNION ALL SELECT a FROM eu WHERE a > 1");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; Sort|null|null|null|null|[0] ;; "
                + "UnionAll|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2]",
            "SELECT * FROM (SELECT a FROM et UNION ALL SELECT a FROM eu) ORDER BY a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "SortWithLimit|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "UnionAll|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[3]",
            "SELECT a FROM et UNION SELECT a FROM eu ORDER BY a LIMIT 1");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; Sort|null|null|null|null|[0] ;; "
                + "Aggregate|null|null|null|null|[1] ;; UnionAll|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[3]",
            "SELECT a FROM et UNION SELECT a FROM eu ORDER BY a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; Limit|null|null|null|null|[0] "
                + ";; Aggregate|null|null|null|null|[1] ;; UnionAll|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[3]",
            "SELECT a FROM et UNION SELECT a FROM eu LIMIT 1");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "UnionAll|null|null|null|null|[0] ;; Limit|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1]",
            "SELECT TOP 2 a FROM et UNION ALL SELECT a FROM eu");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "UnionAll|null|null|null|null|[0] ;; Limit|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1]",
            "SELECT a FROM (SELECT a FROM et LIMIT 2) UNION ALL SELECT a FROM eu");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "UnionAll|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1]",
            "SELECT DISTINCT a FROM et UNION ALL SELECT a FROM eu");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; UnionAll|null|null|null|null|[1] ;; "
                + "Aggregate|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2]",
            "SELECT a FROM et GROUP BY a UNION SELECT a FROM eu");
        assertPlan(
            "GlobalStats|null|4|4|4096|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; UnionAll|null|null|null|null|[1] ;; "
                + "Aggregate|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3] ;; "
                + "Aggregate|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[5] ;; "
                + "Aggregate|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[7] ;; "
                + "Aggregate|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[9]",
            "SELECT a FROM et UNION SELECT a FROM eu UNION SELECT a FROM ev UNION SELECT a FROM eb");
        assertPlan(
            "GlobalStats|null|4|4|4096|null ;; Result|null|null|null|null|null ;; "
                + "UnionAll|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "UnionAll|null|null|null|null|[2] ;; UnionAll|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[4] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[3] ;; TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[1]",
            "SELECT a FROM et UNION ALL SELECT a FROM eu UNION SELECT a FROM ev UNION ALL SELECT a FROM eb");
        assertPlan(
            "GlobalStats|null|4|4|4096|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; UnionAll|null|null|null|null|[1] ;; "
                + "Aggregate|null|null|null|null|[2] ;; UnionAll|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[4] ;; "
                + "Aggregate|null|null|null|null|[2] ;; UnionAll|null|null|null|null|[7] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[8] ;; TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[8]",
            "(SELECT a FROM et UNION SELECT a FROM eu) UNION (SELECT a FROM ev UNION SELECT a FROM eb)");
        assertPlan(
            "GlobalStats|null|4|4|4096|null ;; Result|null|null|null|null|null ;; "
                + "UnionAll|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "UnionAll|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[3] ;; Aggregate|null|null|null|null|[1] ;; "
                + "UnionAll|null|null|null|null|[6] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[7] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[7]",
            "(SELECT a FROM et UNION SELECT a FROM eu) UNION ALL (SELECT a FROM ev UNION SELECT a FROM eb)");
        assertPlan(
            "GlobalStats|null|3|3|3072|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; UnionAll|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; Aggregate|null|null|null|null|[2] ;; "
                + "UnionAll|null|null|null|null|[4] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[5] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[5]",
            "SELECT a FROM et UNION (SELECT a FROM eu UNION SELECT a FROM ev)");
        assertPlan(
            "GlobalStats|null|3|3|3072|null ;; Result|null|null|null|null|null ;; "
                + "UnionAll|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1] ;; "
                + "Aggregate|null|null|null|null|[1] ;; UnionAll|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[4] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[4]",
            "SELECT a FROM et UNION ALL (SELECT a FROM eu UNION SELECT a FROM ev)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "SemiJoin|null|null|null|null|[2] ;; Aggregate|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[4] ;; Aggregate|null|null|null|null|[3] ;; "
                + "JoinFilter|null|null|null|null|[6] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[7]",
            "SELECT a FROM eu INTERSECT SELECT a FROM et");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "AntiJoin|null|null|null|null|[2] ;; Aggregate|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[4] ;; Aggregate|null|null|null|null|[3] ;; "
                + "JoinFilter|null|null|null|null|[6] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[7]",
            "SELECT a FROM eu EXCEPT SELECT a FROM et");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "SemiJoin|null|null|null|null|[2] ;; Aggregate|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4] ;; Aggregate|null|null|null|null|[3] ;; "
                + "JoinFilter|null|null|null|null|[6] ;; TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[7]",
            "SELECT a FROM eb INTERSECT SELECT a FROM et");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "SemiJoin|null|null|null|null|[2] ;; Aggregate|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4] ;; Aggregate|null|null|null|null|[3] ;; "
                + "JoinFilter|null|null|null|null|[6] ;; TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[7]",
            "SELECT a FROM et INTERSECT SELECT a FROM eb");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "AntiJoin|null|null|null|null|[2] ;; Aggregate|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4] ;; Aggregate|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[6]",
            "SELECT a FROM eb EXCEPT SELECT a FROM et");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "AntiJoin|null|null|null|null|[2] ;; Aggregate|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4] ;; Aggregate|null|null|null|null|[3] ;; "
                + "JoinFilter|null|null|null|null|[6] ;; TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[7]",
            "SELECT a FROM et EXCEPT SELECT a FROM eb");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "SemiJoin|null|null|null|null|[2] ;; Aggregate|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[4] ;; Aggregate|null|null|null|null|[3] ;; "
                + "JoinFilter|null|null|null|null|[6] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[7]",
            "SELECT a FROM eu INTERSECT SELECT a FROM ev");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "AntiJoin|null|null|null|null|[2] ;; Aggregate|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[4] ;; Aggregate|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[6]",
            "SELECT a FROM eu EXCEPT SELECT a FROM ev");
        assertPlan(
            "GlobalStats|null|4|4|4096|null ;; Result|null|null|null|null|null ;; "
                + "UnionAll|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[1]",
            "SELECT a FROM et UNION ALL SELECT a FROM eu UNION ALL SELECT a FROM ev UNION ALL SELECT a FROM eb");
        assertPlan(
            "GlobalStats|null|3|3|3072|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; UnionAll|null|null|null|null|[1] ;; "
                + "Aggregate|null|null|null|null|[2] ;; Aggregate|null|null|null|null|[3] ;; "
                + "AntiJoin|null|null|null|null|[4] ;; Aggregate|null|null|null|null|[5] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[6] ;; Aggregate|null|null|null|null|[5] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[8] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[2]",
            "SELECT a FROM et EXCEPT SELECT a FROM eu UNION SELECT a FROM ev");
        assertPlan(
            "GlobalStats|null|3|3|3072|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "AntiJoin|null|null|null|null|[2] ;; Aggregate|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[4] ;; Aggregate|null|null|null|null|[3] ;; "
                + "Aggregate|null|null|null|null|[6] ;; UnionAll|null|null|null|null|[7] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[8] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[8]",
            "SELECT a FROM et UNION SELECT a FROM eu EXCEPT SELECT a FROM ev");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "AntiJoin|null|null|null|null|[2] ;; Aggregate|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4] ;; Aggregate|null|null|null|null|[3] ;; "
                + "JoinFilter|null|null|null|null|[6] ;; TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[7]",
            "SELECT a FROM et MINUS SELECT a FROM eb");
        assertPlan(
            "GlobalStats|null|3|3|3072|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "UnionAll|null|null|null|null|[1] ;; JoinFilter|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4] ;; JoinFilter|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[6]",
            "SELECT * FROM eu JOIN (SELECT a FROM et UNION ALL SELECT a FROM ev) u ON u.a = eu.a");
    }

    @Test
    public void aJoin() {
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "SELECT et.a, eu.c FROM et JOIN eu ON et.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT et.a, eu.c FROM et LEFT JOIN eu ON et.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT et.a, eu.c FROM et CROSS JOIN eu");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "Filter|null|null|null|null|[1] ;; JoinFilter|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4]",
            "SELECT et.a, eu.c FROM et RIGHT JOIN eu ON et.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "FullOuterJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT et.a, eu.c FROM et FULL JOIN eu ON et.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "SELECT et.a, eu.c FROM et NATURAL JOIN eu");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "SELECT et.a, eu.c FROM et JOIN eu USING (a)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "SELECT et.a, eu.c FROM et, eu WHERE et.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT et.a, eu.c FROM et, eu");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "SELECT x.a FROM et x JOIN et y ON x.a = y.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT et.a FROM et JOIN eu ON et.a > eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT et.a FROM et LEFT JOIN eu ON et.a > eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT et.a FROM et LEFT JOIN eu USING (a)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT et.a FROM et NATURAL LEFT JOIN eu");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "FullOuterJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT et.a FROM et FULL JOIN eu USING (a)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; LeftOuterJoin|null|null|null|null|[1] ;; Filter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[3] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT et.a FROM et LEFT JOIN eu ON et.a = eu.a WHERE eu.c IS NULL");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "Filter|null|null|null|null|[1] ;; JoinFilter|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4]",
            "SELECT et.a FROM et RIGHT JOIN eu USING (a)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT et.a FROM et LEFT OUTER JOIN eu ON et.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "FullOuterJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT et.a FROM et FULL OUTER JOIN eu ON et.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "SELECT et.a FROM et CROSS JOIN eu WHERE et.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "SortWithLimit|null|null|null|null|[0] ;; InnerJoin|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; JoinFilter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4]",
            "SELECT et.a FROM et JOIN eu ON et.a = eu.a ORDER BY et.a LIMIT 1");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "SELECT x.a FROM et x, et y WHERE x.a = y.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "SELECT et.a FROM (et JOIN eu ON et.a = eu.a)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT et.a FROM et JOIN eu ON et.a = eu.a OR et.a = 1");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "SELECT eu.a FROM eu JOIN et ON et.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "Filter|null|null|null|null|[1] ;; JoinFilter|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4]",
            "SELECT eu.a FROM eu LEFT JOIN et ON et.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT eu.a FROM eu RIGHT JOIN et ON et.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "FullOuterJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT eu.a FROM eu FULL JOIN et ON et.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "SELECT eu.a FROM eu, et WHERE et.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[3]",
            "SELECT eu.a FROM eu JOIN ev ON ev.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[3]",
            "SELECT eu.a FROM ev JOIN eu ON ev.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1]",
            "SELECT eu.a FROM eu LEFT JOIN ev ON ev.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[1]",
            "SELECT eu.a FROM ev LEFT JOIN eu ON ev.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[3]",
            "SELECT eb.a FROM eb JOIN et ON et.a = eb.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[3]",
            "SELECT eb.a FROM et JOIN eb ON et.a = eb.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[1]",
            "SELECT eb.a FROM eb LEFT JOIN et ON et.a = eb.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1] ;; "
                + "Filter|null|null|null|null|[1] ;; JoinFilter|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[4]",
            "SELECT eb.a FROM et LEFT JOIN eb ON et.a = eb.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1] ;; "
                + "Filter|null|null|null|null|[1] ;; JoinFilter|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[4]",
            "SELECT eb.a FROM eb RIGHT JOIN et ON et.a = eb.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[1]",
            "SELECT eb.a FROM et RIGHT JOIN eb ON et.a = eb.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "FullOuterJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[1]",
            "SELECT eb.a FROM eb FULL JOIN et ON et.a = eb.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[1]",
            "SELECT eb.a, et.a FROM eb CROSS JOIN et");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[1]",
            "SELECT eb.a, et.a FROM et CROSS JOIN eb");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1]",
            "SELECT eu.a, ev.a FROM eu CROSS JOIN ev");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[1]",
            "SELECT eu.a, ev.a FROM ev CROSS JOIN eu");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT eu.a, et.a FROM eu CROSS JOIN et");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT eu.a, et.a FROM et CROSS JOIN eu");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "SELECT et.a FROM et JOIN eu ON et.a + 1 = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "SELECT x.a FROM eu x JOIN et y ON x.a = y.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT eu.a, et.a FROM eu, et");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT eu.a, et.a FROM et, eu");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[1]",
            "SELECT eb.a, et.a FROM eb, et");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[1]",
            "SELECT eb.a, et.a FROM et, eb");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT et.a FROM et LEFT JOIN eu ON et.a = eu.a AND eu.c = 'p'");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "FullOuterJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT et.a FROM et FULL JOIN eu ON et.a > eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT et.a, eu.a FROM et JOIN eu ON et.a > eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT et.a, eu.a FROM et JOIN eu ON et.a <> eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT et.a, eu.a FROM et, eu WHERE et.a > eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT et.a, eu.a FROM et, eu WHERE et.a = eu.a OR et.a > 1");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "SELECT * FROM (SELECT a FROM et) x, (SELECT a FROM eu) y WHERE x.a = y.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[1]",
            "SELECT eu.a FROM eu RIGHT JOIN ev ON ev.a = eu.a");
    }

    @Test
    public void aJoinFurther() {
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1]",
            "SELECT eu.a FROM ev RIGHT JOIN eu ON ev.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "FullOuterJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[1]",
            "SELECT eu.a FROM eu FULL JOIN ev ON ev.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "FullOuterJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1]",
            "SELECT eu.a FROM ev FULL JOIN eu ON ev.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1]",
            "SELECT eu.a FROM eu LEFT JOIN ev ON ev.a > eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[1]",
            "SELECT eu.a FROM eu RIGHT JOIN ev ON ev.a > eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1]",
            "SELECT eu.a, ev.a FROM eu, ev");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[1]",
            "SELECT eu.a, ev.a FROM ev, eu");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[3]",
            "SELECT eu.a, ev.a FROM eu, ev WHERE eu.a = ev.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[3]",
            "SELECT eu.a, ev.a FROM ev, eu WHERE eu.a = ev.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[1]",
            "SELECT eb.a FROM eb LEFT JOIN et ON et.a > eb.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[1]",
            "SELECT eb.a FROM et LEFT JOIN eb ON et.a > eb.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT et.a FROM et RIGHT JOIN eu ON et.a > eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT eu.a FROM eu NATURAL RIGHT JOIN et");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "SELECT eu.a FROM eu NATURAL JOIN et");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "FullOuterJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT eu.a FROM eu NATURAL FULL JOIN et");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT et.a, eu.a FROM et CROSS JOIN eu WHERE et.a > eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "SELECT et.a, eu.a FROM et, eu WHERE et.a = eu.a AND et.b > eu.c");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "CartesianJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT et.a, eu.a FROM et JOIN eu WHERE et.a < eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "Aggregate|null|null|null|null|[1] ;; JoinFilter|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4]",
            "SELECT * FROM (SELECT DISTINCT a FROM et) d JOIN eu ON d.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; Limit|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2] ;; JoinFilter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[4]",
            "SELECT * FROM eu JOIN (SELECT a FROM et LIMIT 2) l ON l.a = eu.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[3]",
            "SELECT * FROM eu, eb WHERE eu.a = eb.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.EB|1|1|1024|[3]",
            "SELECT * FROM eb, eu WHERE eu.a = eb.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[3]",
            "SELECT * FROM eu x JOIN eu y ON x.a = y.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[1]",
            "SELECT * FROM eu x LEFT JOIN eu y ON x.a = y.a");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "InnerJoin|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[1] ;; "
                + "JoinFilter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[3]",
            "SELECT eu.a FROM eu JOIN ev USING (a)");
        assertPlan(
            "GlobalStats|null|2|2|2048|null ;; Result|null|null|null|null|null ;; "
                + "LeftOuterJoin|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.EV|1|1|1024|[1]",
            "SELECT eu.a FROM ev LEFT JOIN eu USING (a)");
    }

    @Test
    public void aQueryOverOneTable() {
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; Generator|null|null|null|null|[0]",
            "SELECT 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[0]",
            "SELECT * FROM et");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.EE|0|0|0|[0]",
            "SELECT * FROM ee");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[0]",
            "SELECT a FROM et");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM et WHERE a > 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[0]",
            "SELECT a + 1 AS n FROM et");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT b, SUM(a) FROM et GROUP BY b");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT DISTINCT b FROM et");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Limit|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM et LIMIT 2");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "SortWithLimit|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM et ORDER BY a LIMIT 2");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM (SELECT a FROM et WHERE a > 1) s WHERE s.a < 3");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; Aggregate|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT b, COUNT(*) FROM et GROUP BY b HAVING COUNT(*) > 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[0]",
            "SELECT a, (SELECT MAX(a) FROM eu) FROM et");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "SortWithLimit|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT b, COUNT(*) FROM et GROUP BY b ORDER BY b LIMIT 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Sort|null|null|null|null|[0] ;; "
                + "Aggregate|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT DISTINCT b FROM et ORDER BY b");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Limit|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM et LIMIT 2 OFFSET 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Limit|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT TOP 2 * FROM et");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM et WHERE a = 1 AND b = 'x'");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "SortWithLimit|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT a FROM et WHERE a > 1 ORDER BY a LIMIT 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[0]",
            "SELECT * FROM et e");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT e.a FROM et e WHERE e.a > 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT COUNT(*) FROM et WHERE a > 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT SUM(a) FROM et");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Sort|null|null|null|null|[0] ;; "
                + "Aggregate|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT b, SUM(a) FROM et GROUP BY b ORDER BY 2");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "WindowFunction|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT b, SUM(a) OVER (PARTITION BY b) FROM et");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "WindowFunction|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT b, SUM(SUM(a)) OVER () FROM et GROUP BY b");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT DISTINCT a, b FROM et WHERE a > 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT COUNT(DISTINCT b) FROM et");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT a FROM et GROUP BY a HAVING a > 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT b, LISTAGG(a) FROM et GROUP BY b");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT a FROM et WHERE a > (SELECT MAX(a) FROM eu)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "SortWithLimit|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM et ORDER BY a LIMIT 2 OFFSET 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "SortWithLimit|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM et ORDER BY a FETCH FIRST 2 ROWS ONLY");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Limit|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM et FETCH FIRST 2 ROWS ONLY");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "SortWithLimit|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT TOP 2 * FROM et ORDER BY a");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Sort|null|null|null|null|[0] ;; "
                + "SortWithLimit|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT * FROM (SELECT * FROM et ORDER BY a LIMIT 2) ORDER BY a DESC");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Limit|null|null|null|null|[0] "
                + ";; Limit|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT * FROM (SELECT * FROM et LIMIT 2) LIMIT 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; Aggregate|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT * FROM (SELECT b, SUM(a) s FROM et GROUP BY b) WHERE s > 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT AVG(a), MIN(b) FROM et WHERE a > 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Sort|null|null|null|null|[0] ;; "
                + "WindowFunction|null|null|null|null|[1] ;; Filter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "SELECT a, COUNT(*) OVER () FROM et WHERE a > 1 ORDER BY a");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Limit|null|null|null|null|[0] "
                + ";; Aggregate|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT DISTINCT b FROM et LIMIT 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Limit|null|null|null|null|[0] "
                + ";; Aggregate|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT b FROM et GROUP BY b LIMIT 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "GroupingSets|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT b, SUM(a) FROM et GROUP BY ROLLUP (b)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT b, SUM(a) FROM et GROUP BY ALL");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT a FROM et WHERE a > 1 AND a < 3 OR b = 'x'");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT a FROM et WHERE b LIKE 'x%'");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT ANY_VALUE(b) FROM et");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; WindowFunction|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT a, SUM(a) OVER (ORDER BY a) s FROM et QUALIFY s > 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; WindowFunction|null|null|null|null|[1] ;; Filter|null|null|null|null|[2] ;; "
                + "Aggregate|null|null|null|null|[3] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4]",
            "SELECT b, COUNT(*) c FROM et GROUP BY b HAVING c > 0 QUALIFY ROW_NUMBER() OVER (ORDER BY b) = 1");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; Generator|null|null|null|null|[0]",
            "SELECT 1 WHERE 1 = 1");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; Generator|null|null|null|null|[0]",
            "SELECT 1 + 1, CURRENT_DATE");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; Generator|null|null|null|null|[0]",
            "SELECT 1 ORDER BY 1");
        assertPlan(
            "GlobalStats|null|0|0|0|null ;; Result|null|null|null|null|null ;; Generator|null|null|null|null|[0]",
            "SELECT 1 LIMIT 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[0]",
            "SELECT * FROM test_db.test_schema.et");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[0]",
            "SELECT * FROM test_schema.et t");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[0]",
            "SELECT * FROM et AS \"t\"");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[0]",
            "SELECT * FROM et AT (OFFSET => 0)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[0]",
            "SELECT * FROM (SELECT * FROM et) x");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[0]",
            "SELECT * FROM ((SELECT a FROM et))");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Limit|null|null|null|null|[0] "
                + ";; Filter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT a FROM et WHERE a > 1 LIMIT 1");
    }

    @Test
    public void aQueryOverOneTableFurther() {
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Sort|null|null|null|null|[0] ;; "
                + "Aggregate|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT a FROM et GROUP BY a ORDER BY a");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "SortWithLimit|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "Aggregate|null|null|null|null|[2] ;; Filter|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4]",
            "SELECT a FROM et WHERE a > 1 GROUP BY a HAVING COUNT(*) > 0 ORDER BY a LIMIT 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "GroupingSets|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT b, SUM(a) FROM et GROUP BY CUBE (b)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "GroupingSets|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT b, SUM(a) FROM et GROUP BY GROUPING SETS ((b), ())");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "GroupingSets|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT a, b, SUM(a) FROM et GROUP BY a, ROLLUP (b)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; Aggregate|null|null|null|null|[1] ;; Filter|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "SELECT b FROM et GROUP BY b HAVING b > 'x' AND COUNT(*) > 0");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; Aggregate|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT SUM(a) FROM et HAVING SUM(a) > 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "SortWithLimit|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "Filter|null|null|null|null|[2] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "SELECT DISTINCT b FROM et WHERE a > 1 ORDER BY b LIMIT 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT SUM(a) + 1 FROM et");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Sort|null|null|null|null|[0] ;; "
                + "Aggregate|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT b FROM et GROUP BY b ORDER BY SUM(a)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT b FROM et GROUP BY b HAVING b IS NOT NULL");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT DISTINCT b, SUM(a) FROM et GROUP BY b");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Sort|null|null|null|null|[0] ;; "
                + "Filter|null|null|null|null|[1] ;; Aggregate|null|null|null|null|[2] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[3]",
            "SELECT b, SUM(a) FROM et GROUP BY b HAVING SUM(a) > 1 ORDER BY b");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "WindowFunction|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT COUNT(*) OVER () FROM et GROUP BY b");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "WindowFunction|null|null|null|null|[0] ;; Aggregate|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT SUM(a), ROW_NUMBER() OVER (ORDER BY SUM(a)) FROM et");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT a FROM et WHERE a IN (1, 2)");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "SortWithLimit|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "WindowFunction|null|null|null|null|[2] ;; Filter|null|null|null|null|[3] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[4]",
            "SELECT a, b FROM et WHERE a > 1 QUALIFY ROW_NUMBER() OVER (PARTITION BY b ORDER BY a) = 1 ORDER BY a "
                + "LIMIT 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Sort|null|null|null|null|[0] ;; "
                + "Filter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.EU|1|1|1024|[2]",
            "SELECT eu.a FROM eu WHERE eu.a > 1 ORDER BY 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Sort|null|null|null|null|[0] ;; "
                + "Filter|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT * FROM et WHERE a > 1 ORDER BY a");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "SortWithLimit|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT * FROM (SELECT * FROM et WHERE a > 1) ORDER BY a LIMIT 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; Limit|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT * FROM (SELECT * FROM et LIMIT 2) WHERE a > 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; Aggregate|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT b, COUNT(*) c FROM et GROUP BY b HAVING c > 0");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT b, MAX(a) m FROM et GROUP BY b HAVING b > 'a'");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "SortWithLimit|null|null|null|null|[0] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM (SELECT * FROM et ORDER BY a) ORDER BY b LIMIT 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Sort|null|null|null|null|[0] ;; "
                + "SortWithLimit|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT * FROM (SELECT * FROM et ORDER BY a LIMIT 2) ORDER BY b");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[1]",
            "SELECT * FROM et WHERE a > 1 AND b = 'y' AND a < 3");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; Filter|null|null|null|null|[0] "
                + ";; Aggregate|null|null|null|null|[1] ;; TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT COUNT(*), b FROM et GROUP BY b HAVING COUNT(*) > 0 AND MAX(a) > 0");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT b FROM et GROUP BY 1 HAVING 1 = 1");
        assertPlan(
            "GlobalStats|null|1|1|1024|null ;; Result|null|null|null|null|null ;; "
                + "Aggregate|null|null|null|null|[0] ;; Filter|null|null|null|null|[1] ;; "
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|1|1|1024|[2]",
            "SELECT DISTINCT b FROM et HAVING b > 'a'");
    }

    @Test
    public void theWholePlanInEachFormat() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'USE'.",
            "EXPLAIN USE SCHEMA test_schema");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'CALL'.",
            "EXPLAIN CALL SYSTEM$WAIT(0)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'VALUES'.",
            "EXPLAIN VALUES (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'EXPLAIN'.",
            "EXPLAIN EXPLAIN SELECT 1");
        assertAnswer(
            "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|number of rows "
                + "inserted|null|null|null ;; 1|1|[0]|Insert|TEST_DB.TEST_SCHEMA.ET|null|null|null|null|null ;; "
                + "1|2|[1]|ExternalScan|null|null|USER STAGE|null|null|null",
            "EXPLAIN COPY INTO et FROM @~/nothing_here");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'COMMIT'.",
            "EXPLAIN COMMIT");
        assertAnswer(
            "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|1|null|null|null ;; "
                + "1|1|[0]|Generator|null|null|1|null|null|null",
            "EXPLAIN SELECT 1;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '<EOF>'.",
            "EXPLAIN");
        assertAnswer(
            "GlobalStats: |     partitionsTotal=1 |     partitionsAssigned=0 |     bytesAssigned=0 | Operations: "
                + "| 1:0     ->Result  number of rows deleted   | 1:1          ->Delete  TEST_DB.TEST_SCHEMA.ET   | "
                + "1:2               ->TableScan  TEST_DB.TEST_SCHEMA.ET  {partitionsTotal=1, partitionsAssigned=0, "
                + "bytesAssigned=0} | ",
            "EXPLAIN USING TEXT DELETE FROM et");
        assertAnswer(
            "{\"GlobalStats\":{\"partitionsTotal\":1,\"partitionsAssigned\":0,\"bytesAssigned\":0},"
                + "\"Operations\":[[{\"id\":0,\"operation\":\"Result\",\"expressions\":[\"number of rows deleted\"]},"
                + "{\"id\":1,\"operation\":\"Delete\",\"objects\":[\"TEST_DB.TEST_SCHEMA.ET\"],"
                + "\"parentOperators\":[0]},{\"id\":2,\"operation\":\"TableScan\","
                + "\"objects\":[\"TEST_DB.TEST_SCHEMA.ET\"],\"partitionsAssigned\":0,\"partitionsTotal\":1,"
                + "\"bytesAssigned\":0,\"parentOperators\":[1]}]]}",
            "EXPLAIN USING JSON DELETE FROM et");
        assertAnswer(
            "GlobalStats: |     partitionsTotal=0 |     partitionsAssigned=0 |     bytesAssigned=0 | Operations: "
                + "| 1:0     ->Result  1   | 1:1          ->Generator  1   | ",
            "EXPLAIN USING TEXT SELECT 1");
        assertAnswer(
            "{\"GlobalStats\":{\"partitionsTotal\":0,\"partitionsAssigned\":0,\"bytesAssigned\":0},"
                + "\"Operations\":[[{\"id\":0,\"operation\":\"Result\",\"expressions\":[\"1\"]},{\"id\":1,"
                + "\"operation\":\"Generator\",\"expressions\":[\"1\"],\"parentOperators\":[0]}]]}",
            "EXPLAIN USING JSON SELECT 1");
        assertAnswer(
            "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|number of rows "
                + "inserted|null|null|null ;; 1|1|[0]|Insert|TEST_DB.TEST_SCHEMA.EE|null|null|null|null|null ;; "
                + "1|2|[1]|ValuesClause|null|null|(1)|null|null|null",
            "EXPLAIN INSERT INTO ee VALUES (1)");
        assertAnswer(
            "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|number of rows "
                + "inserted|null|null|null ;; 1|1|[0]|Insert|TEST_DB.TEST_SCHEMA.EE|null|null|null|null|null ;; "
                + "1|2|[1]|ValuesClause|null|null|(1), (2), (3)|null|null|null",
            "EXPLAIN INSERT INTO ee (a) VALUES (1), (2), (3)");
        assertAnswer(
            "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|number of rows "
                + "inserted|null|null|null ;; 1|1|[0]|Insert|TEST_DB.TEST_SCHEMA.EE|null|null|null|null|null ;; "
                + "1|2|[1]|ValuesClause|null|null|(1), (2)|null|null|null",
            "EXPLAIN INSERT INTO ee SELECT * FROM VALUES (1), (2)");
        assertAnswer(
            "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|number of rows "
                + "inserted|null|null|null ;; 1|1|[0]|Insert|TEST_DB.TEST_SCHEMA.EE|null|null|null|null|null ;; "
                + "1|2|[1]|ValuesClause|null|null|(1)|null|null|null",
            "EXPLAIN INSERT OVERWRITE INTO ee VALUES (1)");
        assertAnswer(
            "GlobalStats: |     partitionsTotal=0 |     partitionsAssigned=0 |     bytesAssigned=0 | Operations: "
                + "| 1:0     ->Result  number of rows inserted   | 1:1          ->Insert  TEST_DB.TEST_SCHEMA.ET   | "
                + "1:2               ->ExternalScan  INTERNAL STAGE   | ",
            "EXPLAIN USING TEXT COPY INTO et FROM @est");
        assertAnswer(
            "GlobalStats: |     partitionsTotal=1 |     partitionsAssigned=0 |     bytesAssigned=0 | Operations: "
                + "| 1:0     ->Result  number of rows deleted   | 1:1          ->Delete  TEST_DB.TEST_SCHEMA.ET   | "
                + "1:2               ->TableScan  TEST_DB.TEST_SCHEMA.ET  {partitionsTotal=1, partitionsAssigned=0, "
                + "bytesAssigned=0} | ",
            "EXPLAIN USING TEXT DELETE FROM et");
        assertAnswer(
            "{\"GlobalStats\":{\"partitionsTotal\":1,\"partitionsAssigned\":0,\"bytesAssigned\":0},"
                + "\"Operations\":[[{\"id\":0,\"operation\":\"Result\",\"expressions\":[\"number of rows deleted\"]},"
                + "{\"id\":1,\"operation\":\"Delete\",\"objects\":[\"TEST_DB.TEST_SCHEMA.ET\"],"
                + "\"parentOperators\":[0]},{\"id\":2,\"operation\":\"TableScan\","
                + "\"objects\":[\"TEST_DB.TEST_SCHEMA.ET\"],\"partitionsAssigned\":0,\"partitionsTotal\":1,"
                + "\"bytesAssigned\":0,\"parentOperators\":[1]}]]}",
            "EXPLAIN USING JSON DELETE FROM et");
    }
}
