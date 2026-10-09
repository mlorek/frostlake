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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * GET_DDL writes a table's keys as table-level lines. The engine wrote a single-column UNIQUE on its column, left
 * out a foreign key declared on a column, named no constraint and wrote a foreign key's parent as its statement had.
 *
 * <p>On the account every key follows the columns on a line of its own, however it was written: the unique keys — a
 * column's own first, then the table-level ones — the checks, the primary key and the foreign keys, a column's own
 * first. A name the constraint was given is spelled {@code constraint NAME}; RELY ends the line as {@code rely}
 * with a space after it; a foreign key's parent is written in full and its parent's columns without a space after
 * the comma. Every cell is live-verified.
 */
public class GetDdlConstraintLinesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE p1 (id INT PRIMARY KEY)");
        engine.execute("CREATE TABLE p2 (a INT, b INT, PRIMARY KEY (a, b))");
    }

    private String ddl(final String table) {
        return String.valueOf(engine.executeQuery("SELECT GET_DDL('table', '" + table + "')").getRows().get(0)
            .getValue(0));
    }

    @Test
    public void keysWrittenAtTableLevel() {
        engine.execute("CREATE TABLE d1 (a INT, b INT, c INT, PRIMARY KEY (a) RELY, UNIQUE (b) NOT ENFORCED,"
            + " FOREIGN KEY (c) REFERENCES p1 (id) NOT ENFORCED RELY)");
        assertEquals("""
            create or replace TABLE D1 (
            \tA NUMBER(38,0) NOT NULL,
            \tB NUMBER(38,0),
            \tC NUMBER(38,0),
            \tunique (B),
            \tprimary key (A) rely\s,
            \tforeign key (C) references TEST_DB.TEST_SCHEMA.P1(ID) rely\s
            );""", ddl("d1"));
    }

    @Test
    public void namedKeys() {
        engine.execute("CREATE TABLE d3 (a INT, b INT, c INT, CONSTRAINT pk_d3 PRIMARY KEY (a, b), CONSTRAINT uq_d3"
            + " UNIQUE (c) RELY, CONSTRAINT fk_d3 FOREIGN KEY (c) REFERENCES p1 MATCH FULL ON DELETE NO ACTION RELY)");
        assertEquals("""
            create or replace TABLE D3 (
            \tA NUMBER(38,0) NOT NULL,
            \tB NUMBER(38,0) NOT NULL,
            \tC NUMBER(38,0),
            \tconstraint UQ_D3 unique (C) rely\s,
            \tconstraint PK_D3 primary key (A, B),
            \tconstraint FK_D3 foreign key (C) references TEST_DB.TEST_SCHEMA.P1(ID) rely\s
            );""", ddl("d3"));
    }

    @Test
    public void keysWrittenOnTheirColumns() {
        engine.execute("CREATE TABLE d4 (a INT CONSTRAINT pk_d4 PRIMARY KEY, b INT CONSTRAINT uq_d4 UNIQUE,"
            + " c INT CONSTRAINT fk_d4 REFERENCES p1 (id), d INT CONSTRAINT ck_d4 CHECK (d > 0))");
        assertEquals("""
            create or replace TABLE D4 (
            \tA NUMBER(38,0) NOT NULL,
            \tB NUMBER(38,0),
            \tC NUMBER(38,0),
            \tD NUMBER(38,0),
            \tconstraint UQ_D4 unique (B),
            \tconstraint CK_D4 check (d > 0),
            \tconstraint PK_D4 primary key (A),
            \tconstraint FK_D4 foreign key (C) references TEST_DB.TEST_SCHEMA.P1(ID)
            );""", ddl("d4"));
    }

    @Test
    public void aColumnsOwnKeysComeFirst() {
        engine.execute("CREATE TABLE d5 (a INT, b INT, c INT UNIQUE NORELY, d INT REFERENCES p1, UNIQUE (a, b),"
            + " FOREIGN KEY (a, b) REFERENCES p2, FOREIGN KEY (c) REFERENCES p1 (id) NORELY, CHECK (a > 0))"
            + " COMMENT = 'c'");
        assertEquals("""
            create or replace TABLE D5 (
            \tA NUMBER(38,0),
            \tB NUMBER(38,0),
            \tC NUMBER(38,0),
            \tD NUMBER(38,0),
            \tunique (C),
            \tunique (A, B),
            \tcheck (a > 0),
            \tforeign key (D) references TEST_DB.TEST_SCHEMA.P1(ID),
            \tforeign key (A, B) references TEST_DB.TEST_SCHEMA.P2(A,B),
            \tforeign key (C) references TEST_DB.TEST_SCHEMA.P1(ID)
            )COMMENT='c'
            ;""", ddl("d5"));
    }

    @Test
    public void quotedNamesAndAParentElsewhere() {
        engine.execute("CREATE TABLE d6 (\"a b\" INT PRIMARY KEY NORELY, \"c\" INT UNIQUE RELY)");
        assertEquals("""
            create or replace TABLE D6 (
            \t"a b" NUMBER(38,0) NOT NULL,
            \t"c" NUMBER(38,0),
            \tunique ("c") rely\s,
            \tprimary key ("a b")
            );""", ddl("d6"));
        engine.execute("CREATE SCHEMA other_n");
        engine.execute("USE SCHEMA test_schema");
        engine.execute("CREATE TABLE other_n.op (id INT PRIMARY KEY)");
        engine.execute("CREATE TABLE d8 (a INT REFERENCES other_n.op (id), b INT REFERENCES test_db.other_n.op)");
        assertEquals("""
            create or replace TABLE D8 (
            \tA NUMBER(38,0),
            \tB NUMBER(38,0),
            \tforeign key (A) references TEST_DB.OTHER_N.OP(ID),
            \tforeign key (B) references TEST_DB.OTHER_N.OP(ID)
            );""", ddl("d8"));
    }

    @Test
    public void aKeyLeftOutIsNotWritten() {
        engine.execute("CREATE TABLE f_simple (y INT, z INT UNIQUE, CONSTRAINT fk_s FOREIGN KEY (y) REFERENCES p1 (id)"
            + " MATCH SIMPLE)");
        assertEquals("""
            create or replace TABLE F_SIMPLE (
            \tY NUMBER(38,0),
            \tZ NUMBER(38,0),
            \tunique (Z)
            );""", ddl("f_simple"));
        engine.execute("CREATE TABLE f_full (y INT, FOREIGN KEY (y) REFERENCES p1 (id) MATCH FULL ON DELETE CASCADE)");
        assertEquals("""
            create or replace TABLE F_FULL (
            \tY NUMBER(38,0)
            );""", ddl("f_full"));
    }
}
