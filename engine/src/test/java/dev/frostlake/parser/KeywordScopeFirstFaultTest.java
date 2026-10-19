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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A SHOW listing's word after a keyword written alone as its scope is named alone, a parenthesis later in the
 * statement included: {@code SHOW TABLES IN USER x (y)} is 'x' and {@code SHOW TABLES IN MATERIALIZED VIEW (x)}
 * 'VIEW'. A DESCRIBE reads a WITH after its name as opening its properties and names the first token that does not
 * fit: {@code DESCRIBE TABLE t1 WITH x AS (SELECT 1) SELECT 1} is 'AS' (live-verified).
 */
public class KeywordScopeFirstFaultTest extends MeasuredCellsTestSupport {

    @Test
    public void theFirstFaultIsNamedAlone() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("CREATE OR REPLACE VIEW v1 AS SELECT 1 x");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mv AS SELECT a FROM t1");
        assertCells(new String[][] {
            {"SHOW TABLES IN USER x (y)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'."},
            {"SHOW TABLES IN DATASET x (y)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'."},
            {"SHOW TABLES IN MATERIALIZED VIEW (x)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'VIEW'."},
            {"SHOW TABLES IN MATERIALIZED VIEW ()",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'VIEW'."},
            {"SHOW TABLES IN USER x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'."},
            {"SHOW TABLES IN USER x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'."},
            {"SHOW TABLES IN MATERIALIZED VIEW mv",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'VIEW'."},
            {"SHOW TABLES IN nosuch x (1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '('."},
            {"SHOW TABLES IN USER x ()",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'."},
            {"SHOW TABLES IN DATASET x (1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'."},
            {"SHOW VIEWS IN USER x (y)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'x'."},
            {"SHOW SCHEMAS IN USER x (y)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'."},
            {"SHOW TABLES IN USER (y)",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW TABLES IN DATASET (y)",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW TABLES IN MATERIALIZED VIEW mv (x)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'VIEW'."},
            {"DESCRIBE TABLE t1 WITH x AS (SELECT 1) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'AS'."},
            {"DESCRIBE TABLE t1 WITH x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '<EOF>'."},
            {"DESCRIBE TABLE t1 WITH",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '<EOF>'."},
            {"DESCRIBE VIEW v1 WITH x AS (SELECT 1) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'AS'."},
            {"DESCRIBE TABLE t1 WITH x AS",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'AS'."},
            {"SELECT 1 FROM system$foo(1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '('."},
            {"SELECT 1 FROM system$foo",
                "REFUSED SQL compilation error: | Object 'SYSTEM$FOO' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE SYSTEM$FOO."},
            {"SELECT 1 FROM t1 JOIN system$foo(1) ON TRUE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '('."},
            {"SELECT 1 FROM system$foo(1) x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '('."},
            {"SELECT 1 FROM t1, system$foo(1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '('."},
            {"SELECT 1 FROM SYSTEM$TYPEOF(1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '('."},
        });
    }
}
