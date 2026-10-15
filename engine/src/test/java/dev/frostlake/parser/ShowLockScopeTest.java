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
 * A scope SHOW LOCKS and SHOW TRANSACTIONS do not take. Both read IN ACCOUNT alone - and TERSE before the kind
 * - so whatever else follows the IN is refused where it stands, and the token after it too when the two could
 * be a kind and its name, or two names: {@code SHOW LOCKS IN DATABASE abc} is DATABASE and abc. A number or a
 * $variable after the kind is not named, nothing is named after TABLE, and nothing after a number or a text.
 * Every answer is live-verified.
 */
public class ShowLockScopeTest extends MeasuredCellsTestSupport {

    @Test
    public void aScopeOtherThanTheAccount() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'DATABASE'. | syntax "
                + "error line 1 at position 23 unexpected 'abc'.",
            "SHOW LOCKS IN DATABASE abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SCHEMA'. | syntax "
                + "error line 1 at position 21 unexpected 'abc'.",
            "SHOW LOCKS IN SCHEMA abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'DATABASE'. | syntax "
                + "error line 1 at position 30 unexpected 'abc'.",
            "SHOW TRANSACTIONS IN DATABASE abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'SCHEMA'. | syntax "
                + "error line 1 at position 28 unexpected 'abc'.",
            "SHOW TRANSACTIONS IN SCHEMA abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'abc'. | syntax error "
                + "line 1 at position 18 unexpected 'def'.",
            "SHOW LOCKS IN abc def");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'TABLE'.",
            "SHOW LOCKS IN TABLE abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'DATABASE'.",
            "SHOW LOCKS IN DATABASE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'abc'.",
            "SHOW LOCKS IN abc");
        assertShape(
            "session:NUMBER(38,0), resource:VARCHAR(16777216), type:VARCHAR(16777216), transaction:NUMBER(38,0), "
                + "transaction_started_on:TIMESTAMP_LTZ, status:VARCHAR(16777216), acquired_on:TIMESTAMP_LTZ, "
                + "query_id:VARCHAR(16777216)",
            "SHOW LOCKS IN ACCOUNT");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW LOCKS IN ACCOUNT abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'DATABASE'. | syntax "
                + "error line 1 at position 23 unexpected 'abc'.",
            "SHOW LOCKS IN DATABASE abc def");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SCHEMA'. | syntax "
                + "error line 1 at position 21 unexpected 'abc'.",
            "SHOW LOCKS IN SCHEMA abc.def");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'DATABASE'. | syntax "
                + "error line 1 at position 23 unexpected 'abc'.",
            "SHOW LOCKS IN DATABASE abc.def");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'abc'.",
            "SHOW LOCKS IN abc.def");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'abc'. | syntax error "
                + "line 1 at position 18 unexpected 'def'.",
            "SHOW LOCKS IN abc def ghi");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'VIEW'. | syntax "
                + "error line 1 at position 19 unexpected 'abc'.",
            "SHOW LOCKS IN VIEW abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'WAREHOUSE'. | syntax "
                + "error line 1 at position 24 unexpected 'abc'.",
            "SHOW LOCKS IN WAREHOUSE abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'USER'. | syntax "
                + "error line 1 at position 19 unexpected 'abc'.",
            "SHOW LOCKS IN USER abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SCHEMA'.",
            "SHOW LOCKS IN SCHEMA");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'TABLE'.",
            "SHOW LOCKS IN TABLE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'abc'.",
            "SHOW LOCKS abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'LIKE'.",
            "SHOW LOCKS LIKE 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'DATABASE'. | syntax "
                + "error line 1 at position 23 unexpected 'abc'.",
            "SHOW LOCKS IN DATABASE abc LIMIT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'abc'. | syntax error "
                + "line 1 at position 25 unexpected 'def'.",
            "SHOW TRANSACTIONS IN abc def");
        assertShape(
            "id:NUMBER(38,0), user:VARCHAR(16777216), session:NUMBER(38,0), name:VARCHAR(16777216), "
                + "started_on:TIMESTAMP_LTZ, state:VARCHAR(16777216), scope:NUMBER(38,0)",
            "SHOW TRANSACTIONS IN ACCOUNT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'TABLE'.",
            "SHOW TRANSACTIONS IN TABLE abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'abc'.",
            "SHOW TRANSACTIONS IN abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'abc'.",
            "SHOW TRANSACTIONS abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'DATABASE'. | syntax "
                + "error line 1 at position 23 unexpected '\"abc\"'.",
            "SHOW LOCKS IN DATABASE \"abc\"");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'DATABASE'. | syntax "
                + "error line 1 at position 23 unexpected ''abc''.",
            "SHOW LOCKS IN DATABASE 'abc'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'DATABASE'.",
            "SHOW LOCKS IN DATABASE 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'DATABASE'. | syntax "
                + "error line 1 at position 23 unexpected 'IDENTIFIER'.",
            "SHOW LOCKS IN DATABASE IDENTIFIER('abc')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SCHEMA'. | syntax "
                + "error line 1 at position 21 unexpected 'abc'.",
            "SHOW LOCKS IN SCHEMA abc def");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "SHOW LOCKS IN");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '<EOF>'.",
            "SHOW TRANSACTIONS IN");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'DATABASE'. | syntax "
                + "error line 1 at position 23 unexpected 'abc'.",
            "SHOW LOCKS IN DATABASE abc;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'SCHEMA'. | syntax "
                + "error line 1 at position 28 unexpected 'abc'.",
            "SHOW TRANSACTIONS IN SCHEMA abc.def ghi");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'TABLE'.",
            "SHOW LOCKS IN TABLE abc def");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'def'.",
            "SHOW LOCKS IN ACCOUNT abc def");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'DATABASE'. | syntax "
                + "error line 1 at position 29 unexpected 'abc'.",
            "SHOW TERSE LOCKS IN DATABASE abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'DATABASE'. | syntax "
                + "error line 1 at position 23 unexpected 'abc'.",
            "SHOW LOCKS IN DATABASE abc STARTS WITH");
    }

    @Test
    public void otherKindsAndTerse() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '1'.",
            "SHOW LOCKS IN 1 abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected ''abc''.",
            "SHOW LOCKS IN 'abc' def");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'TABLE'.",
            "SHOW LOCKS IN TABLE abc.def");
        assertShape(
            "resource:VARCHAR(16777216), type:VARCHAR(16777216), transaction:NUMBER(38,0), "
                + "transaction_started_on:TIMESTAMP_LTZ, status:VARCHAR(16777216), acquired_on:TIMESTAMP_LTZ, "
                + "query_id:VARCHAR(16777216)",
            "SHOW TERSE LOCKS");
        assertShape(
            "id:NUMBER(38,0), user:VARCHAR(16777216), session:NUMBER(38,0), name:VARCHAR(16777216), "
                + "started_on:TIMESTAMP_LTZ, state:VARCHAR(16777216), scope:NUMBER(38,0)",
            "SHOW TERSE TRANSACTIONS");
        assertShape(
            "session:NUMBER(38,0), resource:VARCHAR(16777216), type:VARCHAR(16777216), transaction:NUMBER(38,0), "
                + "transaction_started_on:TIMESTAMP_LTZ, status:VARCHAR(16777216), acquired_on:TIMESTAMP_LTZ, "
                + "query_id:VARCHAR(16777216)",
            "SHOW TERSE LOCKS IN ACCOUNT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'DATABASE'. | syntax "
                + "error line 1 at position 23 unexpected 'abc'.",
            "SHOW LOCKS IN DATABASE abc def ghi");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'FUNCTION'. | syntax "
                + "error line 1 at position 23 unexpected 'abc'.",
            "SHOW LOCKS IN FUNCTION abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'STAGE'. | syntax "
                + "error line 1 at position 20 unexpected 'abc'.",
            "SHOW LOCKS IN STAGE abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'ROLE'. | syntax "
                + "error line 1 at position 19 unexpected 'abc'.",
            "SHOW LOCKS IN ROLE abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SESSION'.",
            "SHOW LOCKS IN SESSION");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'ORGANIZATION'.",
            "SHOW LOCKS IN ORGANIZATION");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'APPLICATION'. | "
                + "syntax error line 1 at position 26 unexpected 'abc'.",
            "SHOW LOCKS IN APPLICATION abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'TABLES'. | syntax "
                + "error line 1 at position 21 unexpected 'abc'.",
            "SHOW LOCKS IN TABLES abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'DATABASE'. | syntax "
                + "error line 1 at position 23 unexpected 'abc'.",
            "SHOW LOCKS IN DATABASE abc, def");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'DATABASE'. | syntax "
                + "error line 1 at position 23 unexpected '('.",
            "SHOW LOCKS IN DATABASE (abc)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'DATABASE'.",
            "SHOW LOCKS IN DATABASE $x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'abc'. | syntax error "
                + "line 1 at position 18 unexpected ''def''.",
            "SHOW LOCKS IN abc 'def'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'abc'.",
            "SHOW LOCKS IN abc 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'abc'. | syntax error "
                + "line 1 at position 18 unexpected '('.",
            "SHOW LOCKS IN abc (");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '\"a b\"'. | syntax "
                + "error line 1 at position 20 unexpected 'def'.",
            "SHOW LOCKS IN \"a b\" def");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'VIEW'. | syntax "
                + "error line 1 at position 26 unexpected 'abc'.",
            "SHOW TRANSACTIONS IN VIEW abc");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'DATABASE'. | syntax "
                + "error line 1 at position 30 unexpected 'abc'.",
            "SHOW TRANSACTIONS IN DATABASE abc def");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'DATABASE'. | syntax "
                + "error line 1 at position 23 unexpected 'abc'.",
            "SHOW LOCKS IN DATABASE abc; SELECT 1");
    }
}
