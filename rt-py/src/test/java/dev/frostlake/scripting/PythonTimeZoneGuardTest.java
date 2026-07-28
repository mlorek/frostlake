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

package dev.frostlake.scripting;

import dev.frostlake.rt.py.PythonRuntime;
import org.junit.jupiter.api.Test;

import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Executed Python can mutate the PROCESS-WIDE JVM default time zone ({@code import pandas} under
 * GraalPy sets it to GMT), which silently shifted every later engine {@code CURRENT_TIMESTAMP} by
 * the UTC offset — rows written after the first pandas import sorted BEFORE rows written earlier.
 * {@link PythonRuntime#eval} must restore the host zone after every execution. The mutation is
 * simulated through host interop since the test environment has no pandas.
 */
public class PythonTimeZoneGuardTest {

    @Test
    public void evalRestoresJvmDefaultTimeZone() {
        final TimeZone original = TimeZone.getDefault();
        try {
            PythonRuntime.eval("""
                import java
                _tz_cls = java.type('java.util.TimeZone')
                _tz_cls.setDefault(_tz_cls.getTimeZone('Pacific/Chatham'))
                """);
            assertEquals(original.getID(), TimeZone.getDefault().getID(),
                "eval must undo a Python-side TimeZone.setDefault");
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
