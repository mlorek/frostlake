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
 * EXPLAIN plans a sequence read as a Getnextval of its own, named getNextVal_<SEQ> in the Result, over an empty
 * ValuesClause where there is no FROM; a TO_QUERY of a CALL reads an empty ValuesClause answering the procedure
 * with its parameters' names (all live-verified).
 */
public class ExplainSequenceReadTest extends MeasuredCellsTestSupport {

    @Test
    public void sequenceReads() {
        engine.execute("CREATE OR REPLACE TABLE tq (a INT)");
        engine.execute("INSERT INTO tq VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE SEQUENCE sq");
        engine.execute("CREATE OR REPLACE SEQUENCE s2");
        engine.execute("CREATE OR REPLACE PROCEDURE p_w() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT INTO tq "
            + "VALUES (9); RETURN 'ok'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_w2(x NUMBER) RETURNS TABLE (a NUMBER) LANGUAGE SQL AS $$ "
            + "DECLARE r RESULTSET DEFAULT (SELECT a FROM tq WHERE a > :x); BEGIN RETURN TABLE(r); END $$");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|"
                + "\"getNextVal_SQ\".NEXTVAL|null|null|null ;; 1|1|[0]|Getnextval|null|null|SQ|null|null|null ;; 1|2|"
                + "[1]|ValuesClause|null|null|()|null|null|null",
            "EXPLAIN SELECT sq.NEXTVAL");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|"
                + "\"getNextVal_SQ\".NEXTVAL|null|null|null ;; 1|1|[0]|Getnextval|null|null|SQ|null|null|null ;; 1|2|"
                + "[1]|ValuesClause|null|null|()|null|null|null",
            "EXPLAIN SELECT sq.NEXTVAL AS n");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|P_W()|null|null|"
                + "null ;; 1|1|[0]|ValuesClause|null|null|()|null|null|null",
            "EXPLAIN SELECT * FROM TABLE(TO_QUERY('CALL p_w()'))");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|"
                + "(\"getNextVal_SQ\".NEXTVAL) + 1|null|null|null ;; 1|1|[0]|Getnextval|null|null|SQ|null|null|null "
                + ";; 1|2|[1]|ValuesClause|null|null|()|null|null|null",
            "EXPLAIN SELECT sq.NEXTVAL + 1");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|"
                + "\"getNextVal_SQ\".NEXTVAL, \"getNextVal_SQ_0\".NEXTVAL|null|null|null ;; 1|1|[0]|Getnextval|null|"
                + "null|SQ|null|null|null ;; 1|2|[1]|Getnextval|null|null|SQ|null|null|null ;; 1|3|[2]|ValuesClause|"
                + "null|null|()|null|null|null",
            "EXPLAIN SELECT sq.NEXTVAL, sq.NEXTVAL");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|"
                + "\"getNextVal_SQ\".NEXTVAL, \"getNextVal_S2\".NEXTVAL|null|null|null ;; 1|1|[0]|Getnextval|null|"
                + "null|S2|null|null|null ;; 1|2|[1]|Getnextval|null|null|SQ|null|null|null ;; 1|3|[2]|ValuesClause|"
                + "null|null|()|null|null|null",
            "EXPLAIN SELECT sq.NEXTVAL, s2.NEXTVAL");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|"
                + "\"getNextVal_SQ\".NEXTVAL|null|null|null ;; 1|1|[0]|Getnextval|null|null|SQ|null|null|null ;; 1|2|"
                + "[1]|ValuesClause|null|null|()|null|null|null",
            "EXPLAIN SELECT TEST_DB.TEST_SCHEMA.sq.NEXTVAL");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|number of rows "
                + "inserted|null|null|null ;; 1|1|[0]|Insert|TEST_DB.TEST_SCHEMA.TQ|null|null|null|null|null ;; 1|2|"
                + "[1]|Getnextval|null|null|SQ|null|null|null ;; 1|3|[2]|ValuesClause|null|null|()|null|null|null",
            "EXPLAIN INSERT INTO tq SELECT sq.NEXTVAL");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|P_W2(X)|null|null|"
                + "null ;; 1|1|[0]|ValuesClause|null|null|()|null|null|null",
            "EXPLAIN SELECT * FROM TABLE(TO_QUERY('CALL p_w2(1)'))");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|0|0 ;; 1|0|null|Result|null|null|"
                + "\"getNextVal_SQ\".NEXTVAL|null|null|null ;; 1|1|[0]|Limit|null|null|rowCount: 1|null|null|null ;; "
                + "1|2|[1]|Getnextval|null|null|SQ|null|null|null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.TQ|null|"
                + "null|1|0|0",
            "EXPLAIN SELECT sq.NEXTVAL FROM tq LIMIT 1");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|COUNT(*)|null|null|"
                + "null ;; 1|1|[0]|Aggregate|null|null|aggExprs: [COUNT(*)]|null|null|null ;; 1|2|[1]|ValuesClause|"
                + "null|null|()|null|null|null",
            "EXPLAIN SELECT COUNT(*) FROM TABLE(TO_QUERY('CALL p_w()'))");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|"
                + "\"getNextVal_SQ\".NEXTVAL|null|null|null ;; 1|1|[0]|Getnextval|null|null|SQ|null|null|null ;; 1|2|"
                + "[1]|ValuesClause|null|null|()|null|null|null",
            "EXPLAIN SELECT x FROM (SELECT sq.NEXTVAL AS x)");
        assertAnswer("GlobalStats: |     partitionsTotal=0 |     partitionsAssigned=0 |     bytesAssigned=0 | "
                + "Operations: | 1:0     ->Result  \"getNextVal_SQ\".NEXTVAL   | 1:1          ->Getnextval  SQ   | "
                + "1:2               ->ValuesClause  ()   | ",
            "EXPLAIN USING TEXT SELECT sq.NEXTVAL");
    }

    @Test
    public void sequenceReadsOverScans() {
        engine.execute("CREATE OR REPLACE TABLE tq (a INT)");
        engine.execute("INSERT INTO tq VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE et2 (a INT, b VARCHAR)");
        engine.execute("INSERT INTO et2 VALUES (1, 'x'), (2, 'y')");
        engine.execute("CREATE OR REPLACE SEQUENCE sq");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET2.A, ET2.B|"
                + "null|null|null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.ET2|null|A, B|1|1|1024",
            "EXPLAIN SELECT a, b FROM et2");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET2.A|null|null|"
                + "null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.ET2|null|A|1|1|1024",
            "EXPLAIN SELECT a FROM et2");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET2.A, ET2.B, "
                + "\"getNextVal_SQ\".NEXTVAL|null|null|null ;; 1|1|[0]|Getnextval|null|null|SQ|null|null|null ;; 1|2|"
                + "[1]|TableScan|TEST_DB.TEST_SCHEMA.ET2|null|A, B|1|1|1024",
            "EXPLAIN SELECT a, b, sq.NEXTVAL FROM et2");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|"
                + "\"getNextVal_SQ\".NEXTVAL|null|null|null ;; 1|1|[0]|Sort|null|null|ET2.A ASC NULLS LAST|null|null|"
                + "null ;; 1|2|[1]|Getnextval|null|null|SQ|null|null|null ;; 1|3|[2]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET2|null|A|1|1|1024",
            "EXPLAIN SELECT sq.NEXTVAL FROM et2 ORDER BY a");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET2.A, "
                + "\"getNextVal_SQ\".NEXTVAL|null|null|null ;; 1|1|[0]|Getnextval|null|null|SQ|null|null|null ;; 1|2|"
                + "[1]|Filter|null|null|ET2.A > 1|null|null|null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.ET2|null|A|"
                + "1|1|1024",
            "EXPLAIN SELECT a, sq.NEXTVAL FROM et2 WHERE a > 1");
    }
}
