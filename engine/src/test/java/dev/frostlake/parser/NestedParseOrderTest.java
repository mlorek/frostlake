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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A refused statement is told alike whatever was parsed before it: the readings a refusal makes of a rebuilt text
 * keep a prediction cache of their own, so the lines of one statement do not depend on the statements the engine
 * has read. The statements are asked forwards, backwards and forwards again, and each answers the same.
 */
public class NestedParseOrderTest extends MeasuredCellsTestSupport {

    private static final String[] STATEMENTS = {
        "SELECT (1 x), (2 z w)",
        "SELECT (1 x y), (2 z w)",
        "SELECT 1 FROM t WHERE a IN (SELECT 1 x y)",
        "SELECT 1 FROM t WHERE a IN ((SELECT 1 x y), 2)",
        "INSERT INTO t (a b) VALUES (1)",
        "INSERT INTO t (a, b c) VALUES (1, 2)",
        "SELECT a FROM t LIMIT x y",
        "SELECT a FROM t LIMIT 1 OFFSET x y",
        "CREATE TABLE q (a INT b INT)",
        "CREATE TABLE q (a INT, b INT c)",
        "SELECT 1 x y SELECT 2",
        "SELECT 1 FROM t WHERE (a = 1",
        "SELECT 1 +; SELECT 2 x y",
        "SELECT 1 FROM t JOIN; SELECT 1 x y",
        "SET x (1); SELECT 1 x y",
        "SELECT (SELECT a x y FROM t); SELECT 1 x y",
    };

    @Test
    public void aRefusalDoesNotDependOnWhatWasParsedBefore() {
        final List<String> forwards = new ArrayList<>();
        for (final String statement : STATEMENTS) {
            forwards.add(answer(statement));
        }
        for (int i = STATEMENTS.length - 1; i >= 0; i--) {
            assertEquals(forwards.get(i), answer(STATEMENTS[i]), STATEMENTS[i]);
        }
        for (int i = 0; i < STATEMENTS.length; i++) {
            assertEquals(forwards.get(i), answer(STATEMENTS[i]), STATEMENTS[i]);
        }
    }
}
