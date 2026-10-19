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
 * A fault straight after a select item's alias inside a query that a FROM clause or a WITH clause holds: the
 * token, and then what the account reads after giving the query up there. A value run into the alias closes the
 * query, so its own ')' is refused when more of the query follows and the statement reads on when none does;
 * an AS, a path, a cast and a list in parentheses are passed over up to the FROM of a query that reads one table;
 * after a comma the rest is read as the FROM clause's next table; a WITH clause's query reads on as if the token
 * were not there. Every answer is live-verified.
 */
public class SubqueryAliasFaultLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void byTheTokenAndWhatFollowsIt() {
        engine.execute(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FZ VALUES (1, TRUE), (2, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE FY (id INT)");
        engine.execute(
            "INSERT INTO FY VALUES (1)");
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 39 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x 'y') WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 31 unexpected '2'. | syntax error line 1 at position 50 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x 'y', 2 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x 'y' FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x 'y' FROM FZ) d WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ''y''. | syntax error "
                + "line 1 at position 59 unexpected 'b'.",
            "WITH c AS (SELECT 1 x 'y' FROM FZ) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ''y''. | syntax error "
                + "line 1 at position 51 unexpected 'b'.",
            "WITH c AS (SELECT 1 x 'y') SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '2'. | syntax error "
                + "line 1 at position 37 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x 2) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '2'. | syntax error "
                + "line 1 at position 29 unexpected '2'. | syntax error line 1 at position 48 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x 2, 2 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '2'. | syntax error "
                + "line 1 at position 35 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x 2 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '2'. | syntax error "
                + "line 1 at position 35 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x 2 FROM FZ) d WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '2'. | syntax error "
                + "line 1 at position 57 unexpected 'b'.",
            "WITH c AS (SELECT 1 x 2 FROM FZ) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '2'. | syntax error "
                + "line 1 at position 49 unexpected 'b'.",
            "WITH c AS (SELECT 1 x 2) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'NULL'. | syntax "
                + "error line 1 at position 40 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x NULL) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'NULL'. | syntax "
                + "error line 1 at position 32 unexpected '2'. | syntax error line 1 at position 51 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x NULL, 2 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'NULL'. | syntax "
                + "error line 1 at position 38 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x NULL FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'NULL'. | syntax "
                + "error line 1 at position 38 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x NULL FROM FZ) d WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'NULL'. | syntax "
                + "error line 1 at position 60 unexpected 'b'.",
            "WITH c AS (SELECT 1 x NULL FROM FZ) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'NULL'. | syntax "
                + "error line 1 at position 52 unexpected 'b'.",
            "WITH c AS (SELECT 1 x NULL) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'AS'. | syntax error "
                + "line 1 at position 38 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x AS) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'AS'. | syntax error "
                + "line 1 at position 30 unexpected '2'. | syntax error line 1 at position 49 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x AS, 2 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'AS'. | syntax error "
                + "line 1 at position 46 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x AS FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'AS'. | syntax error "
                + "line 1 at position 48 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x AS FROM FZ) d WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'AS'. | syntax error "
                + "line 1 at position 58 unexpected 'b'.",
            "WITH c AS (SELECT 1 x AS FROM FZ) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'AS'. | syntax error "
                + "line 1 at position 50 unexpected 'b'.",
            "WITH c AS (SELECT 1 x AS) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 39 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x (1)) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 31 unexpected '2'. | syntax error line 1 at position 50 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x (1), 2 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x (1) FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x (1) FROM FZ) d WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '('. | syntax error "
                + "line 1 at position 23 unexpected '1'. | syntax error line 1 at position 26 unexpected 'FROM'.",
            "WITH c AS (SELECT 1 x (1) FROM FZ) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '('. | syntax error "
                + "line 1 at position 23 unexpected '1'. | syntax error line 1 at position 25 unexpected ')'.",
            "WITH c AS (SELECT 1 x (1)) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 45 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x (id AS k)) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 37 unexpected '2'. | syntax error line 1 at position 56 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x (id AS k), 2 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 53 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x (id AS k) FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 55 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x (id AS k) FROM FZ) d WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '('. | syntax error "
                + "line 1 at position 23 unexpected 'id'. | syntax error line 1 at position 32 unexpected 'FROM'.",
            "WITH c AS (SELECT 1 x (id AS k) FROM FZ) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '('. | syntax error "
                + "line 1 at position 23 unexpected 'id'. | syntax error line 1 at position 31 unexpected ')'.",
            "WITH c AS (SELECT 1 x (id AS k)) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 30 unexpected '2'. | syntax error line 1 at position 49 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x (), 2 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 46 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x () FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 48 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x () FROM FZ) d WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '('.",
            "WITH c AS (SELECT 1 x () FROM FZ) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '('.",
            "WITH c AS (SELECT 1 x ()) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'y'. | syntax error "
                + "line 1 at position 37 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x y) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'y'. | syntax error "
                + "line 1 at position 29 unexpected '2'. | syntax error line 1 at position 48 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x y, 2 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'y'. | syntax error "
                + "line 1 at position 45 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x y FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'y'. | syntax error "
                + "line 1 at position 47 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x y FROM FZ) d WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'y'. | syntax error "
                + "line 1 at position 57 unexpected 'b'.",
            "WITH c AS (SELECT 1 x y FROM FZ) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'y'. | syntax error "
                + "line 1 at position 49 unexpected 'b'.",
            "WITH c AS (SELECT 1 x y) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TRUE'. | syntax "
                + "error line 1 at position 40 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x TRUE) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TRUE'. | syntax "
                + "error line 1 at position 32 unexpected '2'. | syntax error line 1 at position 51 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x TRUE, 2 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TRUE'. | syntax "
                + "error line 1 at position 48 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x TRUE FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TRUE'. | syntax "
                + "error line 1 at position 50 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x TRUE FROM FZ) d WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'TRUE'. | syntax "
                + "error line 1 at position 60 unexpected 'b'.",
            "WITH c AS (SELECT 1 x TRUE FROM FZ) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'TRUE'. | syntax "
                + "error line 1 at position 52 unexpected 'b'.",
            "WITH c AS (SELECT 1 x TRUE) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1.5'. | syntax error "
                + "line 1 at position 39 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x 1.5) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1.5'. | syntax error "
                + "line 1 at position 31 unexpected '2'. | syntax error line 1 at position 50 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x 1.5, 2 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1.5'. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x 1.5 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1.5'. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x 1.5 FROM FZ) d WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '1.5'. | syntax error "
                + "line 1 at position 59 unexpected 'b'.",
            "WITH c AS (SELECT 1 x 1.5 FROM FZ) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '1.5'. | syntax error "
                + "line 1 at position 51 unexpected 'b'.",
            "WITH c AS (SELECT 1 x 1.5) SELECT * FROM c WHERE a b");
    }

    @Test
    public void moreTokensAndPlaces() {
        engine.execute(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FZ VALUES (1, TRUE), (2, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE FN (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FN VALUES (1, TRUE), (2, NULL)");
        engine.execute(
            "CREATE OR REPLACE TABLE FY (id INT)");
        engine.execute(
            "INSERT INTO FY VALUES (1)");
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x 'y' FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 39 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x 'y') WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '2'. | syntax error "
                + "line 1 at position 35 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x 2 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '2'. | syntax error "
                + "line 1 at position 37 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x 2) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'NULL'. | syntax "
                + "error line 1 at position 38 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x NULL FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'NULL'. | syntax "
                + "error line 1 at position 40 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x NULL) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x (id) FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x (id)) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 39 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x ('y') FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 41 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x ('y')) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 41 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x (1 + 1) FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 43 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x (1 + 1)) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 40 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x (NULL) FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 42 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x (NULL)) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '*'. | syntax error "
                + "line 1 at position 35 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x * FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '*'. | syntax error "
                + "line 1 at position 37 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x *) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '+'. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x + 1 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '+'. | syntax error "
                + "line 1 at position 39 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x + 1) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '.'. | syntax error "
                + "line 1 at position 46 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x .y FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '.'. | syntax error "
                + "line 1 at position 28 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x .y) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '::'. | syntax error "
                + "line 1 at position 49 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x ::INT FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '::'. | syntax error "
                + "line 1 at position 31 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x ::INT) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'AS'. | syntax error "
                + "line 1 at position 48 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x AS y FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'AS'. | syntax error "
                + "line 1 at position 30 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x AS y) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'FALSE'. | syntax "
                + "error line 1 at position 49 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x FALSE FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'FALSE'. | syntax "
                + "error line 1 at position 41 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x FALSE) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '$1'. | syntax error "
                + "line 1 at position 46 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x $1 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '$1'. | syntax error "
                + "line 1 at position 38 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x $1) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '?'. | syntax error "
                + "line 1 at position 35 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x ? FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '?'. | syntax error "
                + "line 1 at position 37 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x ?) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'CASE'. | syntax "
                + "error line 1 at position 48 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x CASE FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'CASE'. | syntax "
                + "error line 1 at position 40 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x CASE) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1e3'. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x 1e3 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1e3'. | syntax error "
                + "line 1 at position 39 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x 1e3) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ''y''. | syntax error "
                + "line 1 at position 40 unexpected ')'.",
            "SELECT a FROM (SELECT 1 AS x 'y' FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ''y''. | syntax error "
                + "line 1 at position 42 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 AS x 'y') WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax error "
                + "line 1 at position 32 unexpected '2'. | syntax error line 1 at position 51 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 AS x y, 2 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ''y''. | syntax error "
                + "line 1 at position 44 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 a, 2 x 'y') WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ''y''. | syntax error "
                + "line 1 at position 36 unexpected '3'. | syntax error line 1 at position 55 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 a, 2 x 'y', 3 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ''y''. | syntax error "
                + "line 1 at position 55 unexpected 'b'.",
            "SELECT a FROM (SELECT * FROM (SELECT 1 x 'y')) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ''y''. | syntax error "
                + "line 1 at position 53 unexpected ')'.",
            "SELECT a FROM (SELECT * FROM (SELECT 1 x 'y' FROM FZ)) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ''y''. | syntax error "
                + "line 1 at position 41 unexpected 'ON'.",
            "SELECT a FROM FZ JOIN (SELECT 1 x 'y') d ON 1 = 1 WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ''y''. | syntax error "
                + "line 1 at position 45 unexpected ')'.",
            "SELECT a FROM FZ JOIN (SELECT 1 x 'y' FROM FZ) d ON 1 = 1 WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''.",
            "SELECT a FROM (SELECT 1 x 'y')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x 'y' FROM FZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 31 unexpected '2'.",
            "SELECT a FROM (SELECT 1 x 'y', 2 FROM FZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'NULL'. | syntax "
                + "error line 1 at position 38 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x NULL FROM FZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'AS'.",
            "SELECT a FROM (SELECT 1 x AS FROM FZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x (1) FROM FZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('.",
            "SELECT a FROM (SELECT 1 x ())");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 46 unexpected ''w''. | syntax error line 1 at position 61 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x 'y') d, (SELECT 1 z 'w') e WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 43 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x 'y' 'z') WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 31 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x 'y' z) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'y'. | syntax error "
                + "line 1 at position 39 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x y z) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 41 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x 'y' WHERE 1 = 1) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 44 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x 'y' UNION SELECT 2) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 40 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x 'y' ORDER BY 1) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x 'y' LIMIT 1) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'y'. | syntax error "
                + "line 1 at position 35 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x y LIMIT 1) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'NULL'. | syntax "
                + "error line 1 at position 42 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x NULL WHERE 1 = 1) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '2'. | syntax error "
                + "line 1 at position 37 unexpected 'b'.",
            "SELECT a FROM t , 2 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'FROM'. | syntax "
                + "error line 1 at position 33 unexpected 'b'.",
            "SELECT a FROM t FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ')'.",
            "SELECT a FROM t ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ''y''. | syntax error "
                + "line 1 at position 37 unexpected 'b'.",
            "SELECT a FROM t 'y' FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ''y''. | syntax error "
                + "line 1 at position 30 unexpected 'b'.",
            "SELECT a FROM t x 'y' WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ''y''. | syntax error "
                + "line 1 at position 27 unexpected '2'. | syntax error line 1 at position 62 unexpected 'b'.",
            "WITH c AS (SELECT 1 x 'y', 2 FROM FZ) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'AS'. | syntax error "
                + "line 1 at position 26 unexpected '2'. | syntax error line 1 at position 61 unexpected 'b'.",
            "WITH c AS (SELECT 1 x AS, 2 FROM FZ) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ''y''. | syntax error "
                + "line 1 at position 45 unexpected ''w''. | syntax error line 1 at position 74 unexpected 'b'.",
            "WITH c AS (SELECT 1 x 'y'), d AS (SELECT 1 z 'w') SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ''y''.",
            "WITH c AS (SELECT 1 x 'y') SELECT * FROM c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 19 unexpected ''y''.",
            "SELECT (SELECT 1 x 'y') FROM FZ WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 52 unexpected 'b'.",
            "SELECT a FROM FZ WHERE id IN (SELECT 1 x 'y') AND a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ''y''. | syntax error "
                + "line 1 at position 54 unexpected 'b'.",
            "INSERT INTO FY SELECT a FROM (SELECT 1 x 'y') WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected ''y''. | syntax error "
                + "line 1 at position 58 unexpected 'b'.",
            "CREATE TABLE zz AS SELECT a FROM (SELECT 1 x 'y') WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 45 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x 'y') d (c) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ''y''. | syntax error "
                + "line 1 at position 41 unexpected 'b'.",
            "SELECT a FROM ((SELECT 1 x 'y')) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 2 at position 0 unexpected ''y''. | syntax error "
                + "line 3 at position 0 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x\n'y') WHERE a\nb");
    }

    @Test
    public void tablesAliasesAndLaterStatements() {
        engine.execute(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FZ VALUES (1, TRUE), (2, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE FN (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FN VALUES (1, TRUE), (2, NULL)");
        engine.execute(
            "CREATE OR REPLACE TABLE FY (id INT)");
        engine.execute(
            "INSERT INTO FY VALUES (1)");
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ''y''. | syntax error "
                + "line 1 at position 38 unexpected ')'.",
            "SELECT a FROM (SELECT id x 'y' FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '2'. | syntax error "
                + "line 1 at position 45 unexpected 'b'.",
            "SELECT a FROM (SELECT id + 1 AS x 2) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ''y''. | syntax error "
                + "line 1 at position 48 unexpected 'b'.",
            "SELECT a FROM (SELECT UPPER('a') x 'y') WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ''y''. | syntax error "
                + "line 1 at position 69 unexpected ')'.",
            "SELECT a FROM (SELECT * FROM (SELECT * FROM (SELECT 1 x 'y' FROM FZ))) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''y''. | syntax error "
                + "line 1 at position 41 unexpected ')'.",
            "SELECT a FROM FZ, (SELECT 1 x 'y' FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''y''. | syntax error "
                + "line 1 at position 43 unexpected 'b'.",
            "SELECT a FROM FZ, (SELECT 1 x 'y') WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x 'y' FROM FZ) d JOIN FY ON 1 = 1 WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '::'. | syntax error "
                + "line 1 at position 61 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x ::INT FROM test_schema.FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'AS'. | syntax error "
                + "line 1 at position 48 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x AS FROM FZ f) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'AS'. | syntax error "
                + "line 1 at position 49 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x AS FROM FZ WHERE id = 1) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 56 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x (id AS k) FROM FZ WHERE id = 1) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '2'. | syntax error "
                + "line 1 at position 74 unexpected 'b'.",
            "WITH c AS (SELECT 1 x 2 FROM FZ), d AS (SELECT 1) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ''y''.",
            "WITH c AS (SELECT 1 x 'y' FROM FZ) SELECT * FROM c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ''y''. | syntax error "
                + "line 1 at position 72 unexpected 'b'.",
            "WITH c AS (SELECT 1 x 'y' FROM FZ WHERE id = 1) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'NULL'. | syntax "
                + "error line 1 at position 28 unexpected '2'. | syntax error line 1 at position 59 unexpected 'b'.",
            "WITH c AS (SELECT 1 x NULL, 2 y 3) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ''y''. | syntax error "
                + "line 1 at position 66 unexpected 'b'.",
            "INSERT INTO FY WITH c AS (SELECT 1 x 'y') SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 34 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x 'y', 'z') WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 33 unexpected 'FROM'. | syntax error line 1 at position 50 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x 'y', z FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '2'. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x 2 3 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'NULL'. | syntax "
                + "error line 1 at position 45 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x NULL NULL) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '-'. | syntax error "
                + "line 1 at position 39 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x - 1) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '*'. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x * 2 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '||'. | syntax error "
                + "line 1 at position 42 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x || 'a') WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '/'. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x / 2 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x (id) y FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ''y''. | syntax error "
                + "line 1 at position 41 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 \"x\" 'y') WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ''y''. | syntax error "
                + "line 1 at position 39 unexpected ')'.",
            "SELECT a FROM (SELECT 'q' x 'y' FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 41 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x 'y') d WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 44 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x 'y') AS d WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 54 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x 'y') WHERE a = 1 ORDER BY a b c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x 'y' FROM FZ) WHERE a = 1 ORDER BY a b c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '2'. | syntax error "
                + "line 1 at position 39 unexpected 'ON'.",
            "SELECT a FROM FZ JOIN (SELECT 1 x 2) q ON 1 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ''y''. | syntax error "
                + "line 1 at position 46 unexpected 'ON'.",
            "SELECT a FROM FZ LEFT JOIN (SELECT 1 x 'y') d ON 1 = 1 WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ''y''.",
            "SELECT a FROM FZ JOIN (SELECT 1 x 'y') ON 1 = 1 WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ''y''. | syntax error "
                + "line 1 at position 48 unexpected 'b'.",
            "SELECT a FROM (SELECT DISTINCT 1 x 'y') WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ''y''. | syntax error "
                + "line 1 at position 44 unexpected ')'.",
            "SELECT a FROM (SELECT TOP 1 id x 'y' FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected ''y''. | syntax error "
                + "line 1 at position 49 unexpected 'b'.",
            "SELECT 1; SELECT a FROM (SELECT 1 x 'y') WHERE a b");
    }
}
