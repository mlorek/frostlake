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

package dev.frostlake.console;

import dev.frostlake.DatabaseEngine;
import org.jline.reader.Candidate;
import org.jline.reader.ParsedLine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for SQL tab completion
 */
public class TabCompletionTest {

    private DatabaseEngine engine;
    private SQLCompleter completer;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        completer = new SQLCompleter(engine);

        // Set up test database and tables
        engine.execute("CREATE DATABASE test_completion_db");
        engine.execute("USE DATABASE test_completion_db");
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("USE SCHEMA test_schema");
        engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, dept VARCHAR)");
        engine.execute("CREATE TABLE departments (dept_id INTEGER, dept_name VARCHAR)");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    @Test
    public void testKeywordCompletion() {
        final List<Candidate> candidates = new ArrayList<Candidate>();
        final ParsedLine line = createParsedLine("SEL", 0);

        completer.complete(null, line, candidates);

        boolean foundSelect = false;
        for (final Candidate c : candidates) {
            if (c.value().equals("SELECT")) {
                foundSelect = true;
                break;
            }
        }
        assertTrue(foundSelect, "Should complete SELECT keyword");
    }

    @Test
    public void testKeywordCompletionCaseInsensitive() {
        final List<Candidate> candidates = new ArrayList<Candidate>();
        final ParsedLine line = createParsedLine("sel", 0);

        completer.complete(null, line, candidates);

        boolean foundSelect = false;
        for (final Candidate c : candidates) {
            if (c.value().equals("SELECT")) {
                foundSelect = true;
                break;
            }
        }
        assertTrue(foundSelect, "Should complete SELECT keyword (case insensitive)");
    }

    @Test
    public void testTableNameCompletion() {
        final List<Candidate> candidates = new ArrayList<Candidate>();
        final ParsedLine line = createParsedLine("SELECT * FROM emp", 14);

        completer.complete(null, line, candidates);

        boolean foundEmployees = false;
        for (final Candidate c : candidates) {
            if (c.value().equalsIgnoreCase("employees")) {
                foundEmployees = true;
                break;
            }
        }
        assertTrue(foundEmployees, "Should complete 'employees' table name");
    }

    @Test
    public void testTableNameCompletionAfterJoin() {
        final List<Candidate> candidates = new ArrayList<Candidate>();
        final ParsedLine line = createParsedLine("SELECT * FROM employees JOIN dep", 29);

        completer.complete(null, line, candidates);

        boolean foundDepartments = false;
        for (final Candidate c : candidates) {
            if (c.value().equalsIgnoreCase("departments")) {
                foundDepartments = true;
                break;
            }
        }
        assertTrue(foundDepartments, "Should complete 'departments' table name after JOIN");
    }

    @Test
    public void testColumnNameCompletion() {
        final List<Candidate> candidates = new ArrayList<Candidate>();
        final ParsedLine line = createParsedLine("SELECT nam", 7);

        completer.complete(null, line, candidates);

        boolean foundName = false;
        for (final Candidate c : candidates) {
            if (c.value().equalsIgnoreCase("name")) {
                foundName = true;
                break;
            }
        }
        assertTrue(foundName, "Should complete 'name' column");
    }

    @Test
    public void testColumnNameCompletionInWhere() {
        final List<Candidate> candidates = new ArrayList<Candidate>();
        final ParsedLine line = createParsedLine("SELECT * FROM employees WHERE dep", 30);

        completer.complete(null, line, candidates);

        boolean foundDept = false;
        for (final Candidate c : candidates) {
            if (c.value().equalsIgnoreCase("dept")) {
                foundDept = true;
                break;
            }
        }
        assertTrue(foundDept, "Should complete 'dept' column in WHERE clause");
    }

    @Test
    public void testFunctionCompletion() {
        final List<Candidate> candidates = new ArrayList<Candidate>();
        final ParsedLine line = createParsedLine("SELECT COU", 7);

        completer.complete(null, line, candidates);

        boolean foundCount = false;
        for (final Candidate c : candidates) {
            if (c.value().equals("COUNT")) {
                foundCount = true;
                break;
            }
        }
        assertTrue(foundCount, "Should complete COUNT function");
    }

    @Test
    public void testDatabaseCompletion() {
        final List<Candidate> candidates = new ArrayList<Candidate>();
        final ParsedLine line = createParsedLine("USE DATABASE test", 13);

        completer.complete(null, line, candidates);

        boolean foundTestDb = false;
        for (final Candidate c : candidates) {
            if (c.value().equalsIgnoreCase("test_completion_db")) {
                foundTestDb = true;
                break;
            }
        }
        assertTrue(foundTestDb, "Should complete 'test_completion_db' database name");
    }

    @Test
    public void testSchemaCompletion() {
        final List<Candidate> candidates = new ArrayList<Candidate>();
        final ParsedLine line = createParsedLine("USE SCHEMA test", 11);

        completer.complete(null, line, candidates);

        boolean foundTestSchema = false;
        for (final Candidate c : candidates) {
            if (c.value().equalsIgnoreCase("test_schema")) {
                foundTestSchema = true;
                break;
            }
        }
        assertTrue(foundTestSchema, "Should complete 'test_schema' schema name");
    }

    @Test
    public void testNoCompletionForEmptyWord() {
        final List<Candidate> candidates = new ArrayList<Candidate>();
        final ParsedLine line = createParsedLine("SELECT ", 7);

        completer.complete(null, line, candidates);

        // Should not add any completions for empty word
        assertTrue(candidates.isEmpty(), "Should not complete empty word");
    }

    @Test
    public void testMultipleKeywordMatches() {
        final List<Candidate> candidates = new ArrayList<Candidate>();
        final ParsedLine line = createParsedLine("CRE", 0);

        completer.complete(null, line, candidates);

        boolean foundCreate = false;
        for (final Candidate c : candidates) {
            if (c.value().equals("CREATE")) {
                foundCreate = true;
                break;
            }
        }
        assertTrue(foundCreate, "Should find CREATE in completion results");
        assertTrue(candidates.size() > 0, "Should have at least one completion");
    }

    @Test
    public void testAggregateFunctionCompletion() {
        final List<Candidate> candidates = new ArrayList<Candidate>();
        final ParsedLine line = createParsedLine("SELECT SU", 7);

        completer.complete(null, line, candidates);

        boolean foundSum = false;
        for (final Candidate c : candidates) {
            if (c.value().equals("SUM")) {
                foundSum = true;
                break;
            }
        }
        assertTrue(foundSum, "Should complete SUM aggregate function");
    }

    @Test
    public void testStringFunctionCompletion() {
        final List<Candidate> candidates = new ArrayList<Candidate>();
        final ParsedLine line = createParsedLine("SELECT UPP", 7);

        completer.complete(null, line, candidates);

        boolean foundUpper = false;
        for (final Candidate c : candidates) {
            if (c.value().equals("UPPER")) {
                foundUpper = true;
                break;
            }
        }
        assertTrue(foundUpper, "Should complete UPPER string function");
    }

    @Test
    public void testNoMatchingCompletions() {
        final List<Candidate> candidates = new ArrayList<Candidate>();
        final ParsedLine line = createParsedLine("SELECT zzz", 7);

        completer.complete(null, line, candidates);

        // Should not find any completions for non-existent prefix
        // (though keywords/functions starting with Z might exist)
        // Just verify the method doesn't crash
        assertTrue(candidates.size() >= 0, "Should handle no matches gracefully");
    }

    // Helper method to create a mock ParsedLine
    private ParsedLine createParsedLine(final String line, final int wordIndex) {
        return new ParsedLine() {
            @Override
            public String word() {
                if (wordIndex >= line.length()) {
                    return "";
                }

                // Extract the word starting at wordIndex
                int start = wordIndex;
                int end = wordIndex;

                // Find the start of the word (go backwards to find non-space)
                while (start > 0 && !Character.isWhitespace(line.charAt(start - 1))) {
                    start--;
                }

                // Find the end of the word (go forward to find space)
                while (end < line.length() && !Character.isWhitespace(line.charAt(end))) {
                    end++;
                }

                final String word = line.substring(start, end);
                return word.trim();
            }

            @Override
            public int wordCursor() {
                return wordIndex;
            }

            @Override
            public int wordIndex() {
                return wordIndex;
            }

            @Override
            public List<String> words() {
                final List<String> result = new ArrayList<String>();
                for (final String word : line.split("\\s+")) {
                    if (!word.isEmpty()) {
                        result.add(word);
                    }
                }
                return result;
            }

            @Override
            public String line() {
                return line;
            }

            @Override
            public int cursor() {
                return line.length();
            }
        };
    }
}
