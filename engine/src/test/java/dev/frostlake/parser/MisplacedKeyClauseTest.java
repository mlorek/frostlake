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
 * A clause written out of place after a key constraint. A foreign key reads its MATCH first, then its ON UPDATE
 * and ON DELETE rules - one of each - then its properties; the first word that breaks the order is where the
 * report starts: a MATCH or a rule after a property, a second MATCH, a third rule, a VALIDATE without its
 * ENABLE, and any MATCH or rule after a PRIMARY KEY or a UNIQUE. A rule of a kind given already is refused at
 * its DELETE or UPDATE, which earlier versions accepted. An ALTER TABLE names the one word; in a CREATE TABLE's
 * column list the account reads on - {@code NOT ENFORCED MATCH SIMPLE)} is MATCH, SIMPLE and the parenthesis,
 * {@code NOT ENFORCED ON UPDATE CASCADE)} is ON, UPDATE and the parenthesis. Every answer is live-verified.
 */
public class MisplacedKeyClauseTest extends MeasuredCellsTestSupport {

    @Test
    public void whatFollowsAKeyInTheWrongPlace() {
        answer(
            "CREATE OR REPLACE TABLE p1 (id INT PRIMARY KEY)");
        answer(
            "CREATE OR REPLACE TABLE t2 (y INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 88 unexpected 'FULL'. | syntax error line 1 at position 92 unexpected "
                + "')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) MATCH SIMPLE MATCH FULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 106 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 112 unexpected 'PARTIAL'. | syntax error line 1 at position 119 "
                + "unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) ON UPDATE CASCADE ON DELETE SET "
                + "NULL MATCH PARTIAL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 87 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 93 unexpected 'PARTIAL'. | syntax error line 1 at position 104 "
                + "unexpected 'DELETE'. | syntax error line 1 at position 111 unexpected 'SET'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) ON UPDATE CASCADE MATCH PARTIAL "
                + "ON DELETE SET NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 88 unexpected 'SIMPLE'. | syntax error line 1 at position 94 unexpected "
                + "')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED MATCH SIMPLE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'ON'. | syntax error "
                + "line 1 at position 85 unexpected 'DELETE'. | syntax error line 1 at position 92 unexpected 'NO'. "
                + "| syntax error line 1 at position 95 unexpected 'ACTION'. | syntax error line 1 at position 101 "
                + "unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED ON DELETE NO "
                + "ACTION)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected 'ON'. | syntax error "
                + "line 1 at position 77 unexpected 'DELETE'. | syntax error line 1 at position 84 unexpected 'NO'. "
                + "| syntax error line 1 at position 87 unexpected 'ACTION'. | syntax error line 1 at position 93 "
                + "unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) RELY ON DELETE NO ACTION)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 80 unexpected 'FULL'. | syntax error line 1 at position 84 unexpected "
                + "')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) RELY MATCH FULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 90 unexpected 'DELETE'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) ON DELETE CASCADE ON DELETE "
                + "CASCADE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 90 unexpected 'UPDATE'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) ON UPDATE CASCADE ON UPDATE "
                + "CASCADE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 98 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 104 unexpected 'FULL'. | syntax error line 1 at position 108 unexpected "
                + "')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) MATCH FULL ON DELETE CASCADE "
                + "MATCH FULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 88 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 94 unexpected 'FULL'. | syntax error line 1 at position 98 unexpected "
                + "')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) ON DELETE SET NULL MATCH FULL)");
        assertAnswer(
            "Table T successfully created.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) MATCH FULL ON UPDATE CASCADE ON "
                + "DELETE CASCADE)");
        assertAnswer(
            "Table T successfully created.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) ON DELETE CASCADE ON UPDATE "
                + "CASCADE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 78 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 84 unexpected 'FULL'. | syntax error line 1 at position 88 unexpected "
                + "')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) ENFORCED MATCH FULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 80 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 86 unexpected 'FULL'. | syntax error line 1 at position 90 unexpected "
                + "')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) DEFERRABLE MATCH FULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 88 unexpected 'ON'. | syntax error "
                + "line 1 at position 91 unexpected 'DELETE'. | syntax error line 1 at position 98 unexpected "
                + "'CASCADE'. | syntax error line 1 at position 105 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) INITIALLY DEFERRED ON DELETE "
                + "CASCADE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 76 unexpected 'ON'. | syntax error "
                + "line 1 at position 79 unexpected 'UPDATE'. | syntax error line 1 at position 93 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) ENABLE ON UPDATE CASCADE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 69 unexpected 'VALIDATE'. | syntax "
                + "error line 1 at position 78 unexpected 'MATCH'. | syntax error line 1 at position 90 unexpected "
                + "')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) VALIDATE MATCH SIMPLE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 69 unexpected 'NOVALIDATE'. | "
                + "syntax error line 1 at position 80 unexpected 'ON'. | syntax error line 1 at position 83 "
                + "unexpected 'DELETE'. | syntax error line 1 at position 90 unexpected 'RESTRICT'. | syntax error "
                + "line 1 at position 98 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOVALIDATE ON DELETE RESTRICT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 76 unexpected 'ON'. | syntax error "
                + "line 1 at position 79 unexpected 'DELETE'. | syntax error line 1 at position 86 unexpected 'NO'. "
                + "| syntax error line 1 at position 89 unexpected 'ACTION'. | syntax error line 1 at position 95 "
                + "unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NORELY ON DELETE NO ACTION)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) MATCH)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 75 unexpected 'FOO'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) MATCH FOO)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 71 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) ON)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 78 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) ON DELETE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 79 unexpected 'FOO'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) ON DELETE FOO)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) ON UPDATE SET)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 72 unexpected 'FOO'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) ON FOO CASCADE)");
        assertAnswer(
            "Table T successfully created.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED NOT ENFORCED)");
        assertAnswer(
            "Table T successfully created.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) RELY RELY)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 87 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 93 unexpected 'FULL'. | syntax error line 1 at position 97 unexpected "
                + "')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED RELY MATCH FULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'ON'. | syntax error "
                + "line 1 at position 85 unexpected 'UPDATE'. | syntax error line 1 at position 99 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED ON UPDATE CASCADE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'ON'. | syntax error "
                + "line 1 at position 85 unexpected 'DELETE'. | syntax error line 1 at position 92 unexpected "
                + "'CASCADE'. | syntax error line 1 at position 100 unexpected 'ON'. | syntax error line 1 at "
                + "position 103 unexpected 'UPDATE'. | syntax error line 1 at position 117 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED ON DELETE CASCADE "
                + "ON UPDATE CASCADE)");
        assertAnswer(
            "Table T successfully created.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) ON DELETE NO ACTION NOT "
                + "ENFORCED)");
        assertAnswer(
            "Table T successfully created.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) MATCH FULL NOT ENFORCED)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 72 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 78 unexpected 'FULL'. | syntax error line 1 at position 82 unexpected "
                + "')'.",
            "CREATE OR REPLACE TABLE t (y INT REFERENCES p1 (id) ON DELETE NO ACTION MATCH FULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 63 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 69 unexpected 'SIMPLE'. | syntax error line 1 at position 75 unexpected "
                + "')'.",
            "CREATE OR REPLACE TABLE t (y INT REFERENCES p1 (id) MATCH FULL MATCH SIMPLE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 71 unexpected 'SIMPLE'. | syntax error line 1 at position 77 unexpected "
                + "')'.",
            "CREATE OR REPLACE TABLE t (y INT REFERENCES p1 (id) NOT ENFORCED MATCH SIMPLE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'ON'. | syntax error "
                + "line 1 at position 68 unexpected 'DELETE'. | syntax error line 1 at position 75 unexpected 'NO'. "
                + "| syntax error line 1 at position 78 unexpected 'ACTION'. | syntax error line 1 at position 84 "
                + "unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT REFERENCES p1 (id) NOT ENFORCED ON DELETE NO ACTION)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 70 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 76 unexpected 'PARTIAL'. | syntax error line 1 at position 87 unexpected "
                + "'DELETE'. | syntax error line 1 at position 94 unexpected 'SET'.",
            "CREATE OR REPLACE TABLE t (y INT REFERENCES p1 (id) ON UPDATE CASCADE MATCH PARTIAL ON DELETE SET "
                + "NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected 'ON'. | syntax error "
                + "line 1 at position 60 unexpected 'DELETE'. | syntax error line 1 at position 67 unexpected 'NO'. "
                + "| syntax error line 1 at position 70 unexpected 'ACTION'. | syntax error line 1 at position 76 "
                + "unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT REFERENCES p1 (id) RELY ON DELETE NO ACTION)");
        assertAnswer(
            "Table T successfully created.",
            "CREATE OR REPLACE TABLE t (y INT REFERENCES p1 (id) MATCH FULL ON DELETE CASCADE)");
        assertAnswer(
            "Table T successfully created.",
            "CREATE OR REPLACE TABLE t (y INT REFERENCES p1 (id) NOT ENFORCED)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 88 unexpected 'FULL'. | syntax error line 1 at position 96 unexpected "
                + "'INT'. | syntax error line 1 at position 99 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) MATCH SIMPLE MATCH FULL, z INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'ON'. | syntax error "
                + "line 1 at position 85 unexpected 'DELETE'. | syntax error line 1 at position 92 unexpected 'NO'. "
                + "| syntax error line 1 at position 95 unexpected 'ACTION'. | syntax error line 1 at position 105 "
                + "unexpected 'INT'. | syntax error line 1 at position 108 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED ON DELETE NO "
                + "ACTION, z INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 96 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 102 unexpected 'SIMPLE'. | syntax error line 1 at position 108 "
                + "unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, CONSTRAINT fk FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED "
                + "MATCH SIMPLE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 67 unexpected 'MATCH'.",
            "ALTER TABLE t2 ADD FOREIGN KEY (y) REFERENCES p1 (id) MATCH SIMPLE MATCH FULL");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 81 unexpected 'ON'.",
            "ALTER TABLE t2 ADD CONSTRAINT fk FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED ON DELETE NO ACTION");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 72 unexpected 'MATCH'.",
            "ALTER TABLE t2 ADD FOREIGN KEY (y) REFERENCES p1 (id) ON UPDATE CASCADE MATCH PARTIAL ON DELETE SET "
                + "NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'ON'.",
            "ALTER TABLE t2 ADD FOREIGN KEY (y) REFERENCES p1 (id) RELY ON DELETE NO ACTION");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 63 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 69 unexpected 'FULL'. | syntax error line 1 at position 73 unexpected "
                + "')'.",
            "CREATE OR REPLACE TABLE t (y INT, PRIMARY KEY (y) NOT ENFORCED MATCH FULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'ON'. | syntax error "
                + "line 1 at position 53 unexpected 'DELETE'. | syntax error line 1 at position 60 unexpected "
                + "'CASCADE'. | syntax error line 1 at position 67 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, UNIQUE (y) RELY ON DELETE CASCADE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 64 unexpected 'FULL'. | syntax error line 1 at position 68 unexpected "
                + "')'.",
            "CREATE OR REPLACE TABLE t (y INT PRIMARY KEY NOT ENFORCED MATCH FULL)");
    }

    @Test
    public void whatTheAccountReadsOnTo() {
        answer(
            "CREATE OR REPLACE TABLE p1 (id INT PRIMARY KEY)");
        answer(
            "CREATE OR REPLACE TABLE t2 (y INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'ON'. | syntax error "
                + "line 1 at position 85 unexpected 'DELETE'. | syntax error line 1 at position 92 unexpected 'SET'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED ON DELETE SET NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'ON'. | syntax error "
                + "line 1 at position 85 unexpected 'DELETE'. | syntax error line 1 at position 92 unexpected 'SET'. "
                + "| syntax error line 1 at position 103 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED ON DELETE SET "
                + "DEFAULT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'ON'. | syntax error "
                + "line 1 at position 85 unexpected 'UPDATE'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED ON UPDATE SET NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'ON'. | syntax error "
                + "line 1 at position 85 unexpected 'UPDATE'. | syntax error line 1 at position 95 unexpected "
                + "'ACTION'. | syntax error line 1 at position 101 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED ON UPDATE NO "
                + "ACTION)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'ON'. | syntax error "
                + "line 1 at position 85 unexpected 'UPDATE'. | syntax error line 1 at position 100 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED ON UPDATE RESTRICT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 87 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 93 unexpected 'PARTIAL'. | syntax error line 1 at position 104 "
                + "unexpected 'DELETE'. | syntax error line 1 at position 111 unexpected 'CASCADE'. | syntax error "
                + "line 1 at position 118 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) ON UPDATE CASCADE MATCH PARTIAL "
                + "ON DELETE CASCADE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 87 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 93 unexpected 'PARTIAL'. | syntax error line 1 at position 104 "
                + "unexpected 'DELETE'. | syntax error line 1 at position 111 unexpected 'NO'. | syntax error line 1 "
                + "at position 114 unexpected 'ACTION'. | syntax error line 1 at position 120 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) ON UPDATE CASCADE MATCH PARTIAL "
                + "ON DELETE NO ACTION)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 87 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 93 unexpected 'FULL'. | syntax error line 1 at position 101 unexpected "
                + "'UPDATE'. | syntax error line 1 at position 115 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) ON DELETE CASCADE MATCH FULL ON "
                + "UPDATE CASCADE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 88 unexpected 'FULL'. | syntax error line 1 at position 96 unexpected "
                + "'DELETE'. | syntax error line 1 at position 103 unexpected 'CASCADE'. | syntax error line 1 at "
                + "position 110 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED MATCH FULL ON "
                + "DELETE CASCADE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 88 unexpected 'SIMPLE'. | syntax error line 1 at position 95 unexpected "
                + "'NOT'. | syntax error line 1 at position 107 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED MATCH SIMPLE NOT "
                + "ENFORCED)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 88 unexpected 'FULL'. | syntax error line 1 at position 97 unexpected "
                + "')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED MATCH FULL RELY)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'ON'. | syntax error "
                + "line 1 at position 85 unexpected 'DELETE'. | syntax error line 1 at position 92 unexpected "
                + "'CASCADE'. | syntax error line 1 at position 100 unexpected 'NOT'. | syntax error line 1 at "
                + "position 112 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED ON DELETE CASCADE "
                + "NOT ENFORCED)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'ON'. | syntax error "
                + "line 1 at position 85 unexpected 'DELETE'. | syntax error line 1 at position 92 unexpected "
                + "'CASCADE'. | syntax error line 1 at position 100 unexpected 'RELY'. | syntax error line 1 at "
                + "position 104 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED ON DELETE CASCADE "
                + "RELY)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected 'ON'. | syntax error "
                + "line 1 at position 77 unexpected 'UPDATE'. | syntax error line 1 at position 92 unexpected "
                + "'RELY'. | syntax error line 1 at position 96 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) RELY ON UPDATE CASCADE RELY)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 88 unexpected 'FULL'. | syntax error line 1 at position 96 unexpected "
                + "'INT'. | syntax error line 1 at position 103 unexpected 'VARCHAR'. | syntax error line 1 at "
                + "position 110 unexpected '('.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED MATCH FULL, z INT, "
                + "w VARCHAR(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 88 unexpected 'FULL'. | syntax error line 1 at position 96 unexpected "
                + "'NUMBER'. | syntax error line 1 at position 102 unexpected '('.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED MATCH FULL, z "
                + "NUMBER(10,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 88 unexpected 'FULL'. | syntax error line 1 at position 102 unexpected "
                + "'KEY'. | syntax error line 1 at position 106 unexpected '('.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED MATCH FULL, "
                + "PRIMARY KEY (y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'ON'. | syntax error "
                + "line 1 at position 85 unexpected 'UPDATE'. | syntax error line 1 at position 99 unexpected ','. | "
                + "syntax error line 1 at position 103 unexpected 'INT'. | syntax error line 1 at position 106 "
                + "unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED ON UPDATE CASCADE, "
                + "z INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 88 unexpected 'FULL'. | syntax error line 1 at position 92 unexpected "
                + "')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED MATCH FULL) "
                + "COMMENT = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 75 unexpected 'MATCH'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) MATCH MATCH)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 80 unexpected 'MATCH'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) MATCH FULL MATCH)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'MATCH'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED MATCH)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'ON'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED ON)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'ON'. | syntax error "
                + "line 1 at position 85 unexpected 'DELETE'. | syntax error line 1 at position 91 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED ON DELETE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'ON'. | syntax error "
                + "line 1 at position 85 unexpected 'FOO'. | syntax error line 1 at position 88 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED ON FOO)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 69 unexpected 'VALIDATE'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) VALIDATE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 69 unexpected 'VALIDATE'. | syntax "
                + "error line 1 at position 78 unexpected 'NOT'. | syntax error line 1 at position 90 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) VALIDATE NOT ENFORCED)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 69 unexpected 'NOVALIDATE'. | "
                + "syntax error line 1 at position 80 unexpected 'RELY'. | syntax error line 1 at position 84 "
                + "unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOVALIDATE RELY)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 85 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 91 unexpected 'FULL'. | syntax error line 1 at position 95 unexpected "
                + "')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) ENABLE VALIDATE MATCH FULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 69 unexpected 'NOVALIDATE'. | "
                + "syntax error line 1 at position 80 unexpected 'MATCH'. | syntax error line 1 at position 90 "
                + "unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOVALIDATE MATCH FULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 69 unexpected 'VALIDATE'. | syntax "
                + "error line 1 at position 78 unexpected 'ON'. | syntax error line 1 at position 81 unexpected "
                + "'UPDATE'. | syntax error line 1 at position 95 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) VALIDATE ON UPDATE CASCADE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 105 unexpected 'ON'. | syntax error "
                + "line 1 at position 108 unexpected 'DELETE'. | syntax error line 1 at position 115 unexpected "
                + "'CASCADE'. | syntax error line 1 at position 122 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) ON DELETE CASCADE ON UPDATE "
                + "CASCADE ON DELETE CASCADE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 90 unexpected 'DELETE'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) ON DELETE CASCADE ON DELETE SET "
                + "NULL, z INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 90 unexpected 'DELETE'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) ON DELETE CASCADE ON DELETE "
                + "CASCADE NOT ENFORCED)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 88 unexpected 'FULL'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED MATCH FULL");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected 'ON'. | syntax error "
                + "line 1 at position 85 unexpected 'DELETE'. | syntax error line 1 at position 92 unexpected "
                + "'CASCADE'. | syntax error line 1 at position 99 unexpected '<EOF>'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED ON DELETE CASCADE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 90 unexpected 'DELETE'.",
            "CREATE OR REPLACE TABLE t (y INT, FOREIGN KEY (y) REFERENCES p1 (id) ON DELETE CASCADE ON DELETE "
                + "CASCADE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected 'DELETE'.",
            "CREATE OR REPLACE TABLE t (y INT REFERENCES p1 (id) ON DELETE CASCADE ON DELETE CASCADE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 75 unexpected 'DELETE'.",
            "ALTER TABLE t2 ADD FOREIGN KEY (y) REFERENCES p1 (id) ON DELETE CASCADE ON DELETE CASCADE");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t2 ADD FOREIGN KEY (y) REFERENCES p1 (id) ON DELETE CASCADE ON UPDATE CASCADE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 89 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 95 unexpected 'FULL'. | syntax error line 1 at position 109 unexpected "
                + "'KEY'. | syntax error line 1 at position 113 unexpected '('.",
            "CREATE OR REPLACE TABLE t (y INT, z INT, FOREIGN KEY (y) REFERENCES p1 (id) NOT ENFORCED MATCH FULL, "
                + "FOREIGN KEY (z) REFERENCES p1 (id))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 71 unexpected 'FULL'. | syntax error line 1 at position 79 unexpected "
                + "'INT'. | syntax error line 1 at position 82 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT REFERENCES p1 (id) NOT ENFORCED MATCH FULL, z INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'ON'. | syntax error "
                + "line 1 at position 68 unexpected 'DELETE'. | syntax error line 1 at position 75 unexpected "
                + "'CASCADE'. | syntax error line 1 at position 83 unexpected 'NOT'.",
            "CREATE OR REPLACE TABLE t (y INT REFERENCES p1 (id) NOT ENFORCED ON DELETE CASCADE NOT NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'ON'. | syntax error "
                + "line 1 at position 53 unexpected 'DELETE'. | syntax error line 1 at position 60 unexpected "
                + "'CASCADE'. | syntax error line 1 at position 67 unexpected ')'.",
            "CREATE OR REPLACE TABLE t (y INT PRIMARY KEY RELY ON DELETE CASCADE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'MATCH'. | syntax "
                + "error line 1 at position 46 unexpected 'FULL'. | syntax error line 1 at position 50 unexpected "
                + "')'.",
            "CREATE OR REPLACE TABLE t (y INT UNIQUE MATCH FULL)");
    }
}
