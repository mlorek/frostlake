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

package dev.frostlake.udf;

import dev.frostlake.DatabaseEngine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A name a Python routine's body defines lives no longer than the routine's call, so a routine another engine runs on
 * the same thread does not see it: neither while both engines are open nor after the first has shut down.
 */
public class PythonNamespaceEngineIsolationTest {

    private static final String SETS_A_NAME = """
        CREATE FUNCTION set_secret() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = 'h' AS $$
        SECRET = 'first engine'
        def h():
            return SECRET
        $$""";

    private static final String READS_THE_NAME = """
        CREATE FUNCTION peek() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = 'h' AS $$
        def h():
            return str(globals().get('SECRET'))
        $$""";

    /** A fresh engine with one database, holding the function the statement creates. */
    private static DatabaseEngine engineWith(final String function) {
        final DatabaseEngine engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE d");
        engine.execute("USE SCHEMA d.PUBLIC");
        engine.execute(function);
        return engine;
    }

    private static String scalar(final DatabaseEngine engine, final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValues().get(0));
    }

    @Test
    public void afterTheFirstEngineShutDown() {
        final DatabaseEngine first = engineWith(SETS_A_NAME);
        try {
            assertEquals("first engine", scalar(first, "SELECT set_secret()"));
        } finally {
            first.shutdown();
        }
        final DatabaseEngine second = engineWith(READS_THE_NAME);
        try {
            assertEquals("None", scalar(second, "SELECT peek()"));
        } finally {
            second.shutdown();
        }
    }

    @Test
    public void whileBothEnginesAreOpen() {
        final DatabaseEngine first = engineWith(SETS_A_NAME);
        final DatabaseEngine second = engineWith(READS_THE_NAME);
        try {
            assertEquals("first engine", scalar(first, "SELECT set_secret()"));
            assertEquals("None", scalar(second, "SELECT peek()"));
            assertEquals("first engine", scalar(first, "SELECT set_secret()"));
        } finally {
            first.shutdown();
            second.shutdown();
        }
    }
}
