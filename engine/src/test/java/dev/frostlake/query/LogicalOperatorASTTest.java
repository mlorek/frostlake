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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.ExecutionResult;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests demonstrating that SQL logical operators use AST internally
 */
public class LogicalOperatorASTTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(LogicalOperatorASTTest.class);

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE users (id INTEGER, age INTEGER, city VARCHAR, status VARCHAR)");
        engine.execute("INSERT INTO users VALUES (1, 25, 'NYC', 'active')");
        engine.execute("INSERT INTO users VALUES (2, 17, 'LA', 'active')");
        engine.execute("INSERT INTO users VALUES (3, 30, 'NYC', 'inactive')");
        engine.execute("INSERT INTO users VALUES (4, 22, 'LA', 'active')");
        engine.execute("INSERT INTO users VALUES (5, 35, 'Chicago', 'deleted')");
    }

    @Test
    public void testSimpleAND() {
        logger.info("Testing AND operator: age > 18 AND status = 'active'");

        // Internally creates AST:
        // BinaryOperationExpression(AND)
        //   ├── BinaryOperationExpression(GREATER_THAN): age > 18
        //   └── BinaryOperationExpression(EQUAL): status = 'active'

        ExecutionResult result = engine.execute(
            "SELECT id FROM users WHERE age > 18 AND status = 'active'"
        );
        ResultSet rs = result.getResultSets().get(0);

        assertEquals(2, rs.getRows().size());
        assertEquals(1L, rs.getRows().get(0).getValue(0)); // id=1, age=25, active
        assertEquals(4L, rs.getRows().get(1).getValue(0)); // id=4, age=22, active
    }

    @Test
    public void testSimpleOR() {
        logger.info("Testing OR operator: city = 'NYC' OR city = 'LA'");

        // Internally creates AST:
        // BinaryOperationExpression(OR)
        //   ├── BinaryOperationExpression(EQUAL): city = 'NYC'
        //   └── BinaryOperationExpression(EQUAL): city = 'LA'

        ExecutionResult result = engine.execute(
            "SELECT id FROM users WHERE city = 'NYC' OR city = 'LA'"
        );
        ResultSet rs = result.getResultSets().get(0);

        assertEquals(4, rs.getRows().size()); // ids 1,2,3,4
    }

    @Test
    public void testNOT() {
        logger.info("Testing NOT operator: NOT (status = 'deleted')");

        // Internally creates AST:
        // UnaryOperationExpression(NOT)
        //   └── BinaryOperationExpression(EQUAL): status = 'deleted'

        ExecutionResult result = engine.execute(
            "SELECT id FROM users WHERE NOT (status = 'deleted')"
        );
        ResultSet rs = result.getResultSets().get(0);

        assertEquals(4, rs.getRows().size()); // All except id=5
    }

    @Test
    public void testNestedANDOR() {
        logger.info("Testing nested: (city = 'NYC' OR city = 'LA') AND age > 20");

        // Internally creates AST:
        // BinaryOperationExpression(AND)
        //   ├── BinaryOperationExpression(OR)
        //   │   ├── BinaryOperationExpression(EQUAL): city = 'NYC'
        //   │   └── BinaryOperationExpression(EQUAL): city = 'LA'
        //   └── BinaryOperationExpression(GREATER_THAN): age > 20

        ExecutionResult result = engine.execute(
            "SELECT id FROM users WHERE (city = 'NYC' OR city = 'LA') AND age > 20"
        );
        ResultSet rs = result.getResultSets().get(0);

        assertEquals(3, rs.getRows().size()); // ids 1,3,4 (age>20 in NYC/LA)
    }

    @Test
    public void testComplexExpression() {
        logger.info("Testing complex: NOT (age < 18) AND (city = 'NYC' OR status = 'active')");

        // Internally creates AST:
        // BinaryOperationExpression(AND)
        //   ├── UnaryOperationExpression(NOT)
        //   │   └── BinaryOperationExpression(LESS_THAN): age < 18
        //   └── BinaryOperationExpression(OR)
        //       ├── BinaryOperationExpression(EQUAL): city = 'NYC'
        //       └── BinaryOperationExpression(EQUAL): status = 'active'

        ExecutionResult result = engine.execute(
            "SELECT id FROM users WHERE NOT (age < 18) AND (city = 'NYC' OR status = 'active')"
        );
        ResultSet rs = result.getResultSets().get(0);

        // Should match:
        // id=1: age=25 (>=18), city=NYC ✓
        // id=3: age=30 (>=18), city=NYC ✓
        // id=4: age=22 (>=18), status=active ✓
        // id=5: age=35 (>=18), BUT city=Chicago AND status=deleted ✗

        assertEquals(3, rs.getRows().size());
    }

    @Test
    public void testOperatorPrecedence() {
        logger.info("Testing precedence: age > 18 OR city = 'NYC' AND status = 'active'");

        // SQL operator precedence: AND > OR
        // Parses as: (age > 18) OR ((city = 'NYC') AND (status = 'active'))

        // Internally creates AST:
        // BinaryOperationExpression(OR)
        //   ├── BinaryOperationExpression(GREATER_THAN): age > 18
        //   └── BinaryOperationExpression(AND)
        //       ├── BinaryOperationExpression(EQUAL): city = 'NYC'
        //       └── BinaryOperationExpression(EQUAL): status = 'active'

        ExecutionResult result = engine.execute(
            "SELECT id FROM users WHERE age > 18 OR city = 'NYC' AND status = 'active'"
        );
        ResultSet rs = result.getResultSets().get(0);

        // Should match:
        // id=1: age=25 (>18) ✓
        // id=3: age=30 (>18) ✓
        // id=4: age=22 (>18) ✓
        // id=5: age=35 (>18) ✓
        // (id=2 would match if city='NYC' AND status='active', but it doesn't)

        assertEquals(4, rs.getRows().size());
    }

    @Test
    public void testMultipleNOT() {
        logger.info("Testing multiple NOT: NOT (NOT (age > 18))");

        // Internally creates AST:
        // UnaryOperationExpression(NOT)
        //   └── UnaryOperationExpression(NOT)
        //       └── BinaryOperationExpression(GREATER_THAN): age > 18

        ExecutionResult result = engine.execute(
            "SELECT id FROM users WHERE NOT (NOT (age > 18))"
        );
        ResultSet rs = result.getResultSets().get(0);

        // Double NOT = original condition
        assertEquals(4, rs.getRows().size()); // age > 18: ids 1,3,4,5
    }

    @Test
    public void testAllThreeOperators() {
        logger.info("Testing AND, OR, NOT together");

        // NOT (status = 'deleted') AND (age > 25 OR city = 'LA')

        ExecutionResult result = engine.execute(
            "SELECT id FROM users WHERE NOT (status = 'deleted') AND (age > 25 OR city = 'LA')"
        );
        ResultSet rs = result.getResultSets().get(0);

        // Should match:
        // id=2: age=17 (not>25), city=LA ✓, status=active ✓
        // id=3: age=30 (>25), status=inactive ✓
        // id=4: age=22 (not>25), city=LA ✓, status=active ✓

        assertEquals(3, rs.getRows().size());
    }
}
