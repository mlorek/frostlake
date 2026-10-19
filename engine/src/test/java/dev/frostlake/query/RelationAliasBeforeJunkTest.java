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
 * A word straight after a relation that has no alias yet is that alias, so what follows it is what is refused:
 * SELECT a FROM t COMMENT = 'x' and SELECT a FROM t foo = 'x' are syntax errors at the '=', after a joined
 * relation, a derived table and a qualified name alike, and a second word is refused itself. After a WHERE or
 * an ON condition no alias is read, and the word is refused. Every answer is live-verified.
 */
public class RelationAliasBeforeJunkTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE rp_c (a INT)");
        engine.execute("CREATE OR REPLACE TABLE rp_d (a INT)");
    }

    @Test
    public void theWordIsTheRelationsAlias() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '='.",
            "SELECT a FROM rp_c COMMENT = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '='.",
            "SELECT a FROM rp_c foo = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '='.",
            "SELECT a FROM rp_c AS foo = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '='.",
            "SELECT c.a FROM rp_c c JOIN rp_d foo = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '='.",
            "SELECT a FROM (SELECT 1 a) foo = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '='.",
            "SELECT c.a FROM rp_c c, rp_d foo = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '='.",
            "SELECT a FROM test_schema.rp_c foo = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '='.",
            "SELECT a FROM rp_c tag = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 3 at position 10 unexpected '='.",
            "SELECT a\nFROM rp_c\n  COMMENT = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '='.",
            "DELETE FROM rp_c foo = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '='.",
            "UPDATE rp_c foo = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '+'.",
            "SELECT a FROM rp_c foo + 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected ''x''.",
            "SELECT a FROM rp_c foo ('x')");
    }

    @Test
    public void whereNoAliasIsReadTheWordIsRefused() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'COMMENT'.",
            "SELECT 1 AS x COMMENT = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'foo'.",
            "SELECT a FROM rp_c WHERE a = 1 foo = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'foo'.",
            "SELECT c.a FROM rp_c c JOIN rp_d d ON c.a = d.a foo = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'bar'.",
            "SELECT a FROM rp_c foo bar = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '='.",
            "SELECT a FROM rp_c; COMMENT = 'x'");
    }

    @Test
    public void theListingsOfNetworkObjectsDeclareTheirCounts() {
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "entries_in_allowed_ip_list:NUMBER(9,0), entries_in_blocked_ip_list:NUMBER(9,0), "
                + "entries_in_allowed_network_rules:NUMBER(9,0), entries_in_blocked_network_rules:NUMBER(9,0)",
            "SHOW NETWORK POLICIES LIKE 'flq_nosuch%'");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), owner:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "type:VARCHAR(16777216), mode:VARCHAR(16777216), entries_in_valuelist:NUMBER(9,0), "
                + "owner_role_type:VARCHAR(16777216)",
            "SHOW NETWORK RULES LIKE 'flq_nosuch%'");
    }
}
