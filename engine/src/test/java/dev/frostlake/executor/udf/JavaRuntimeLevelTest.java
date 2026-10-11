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

package dev.frostlake.executor.udf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Java level an inline body is compiled at: its routine's RUNTIME_VERSION, 11 when it names none, and the
 * JDK's own - written 0 - for a version the JDK the engine runs on cannot hold a body to.
 */
public class JavaRuntimeLevelTest {

    @Test
    public void aRoutineWithoutAVersionIsJavaEleven() {
        assertEquals(11, JavaRuntimeLevel.of(null));
        assertEquals(11, JavaRuntimeLevel.of(""));
        assertEquals(11, JavaRuntimeLevel.of("  "));
    }

    @Test
    public void aNamedVersionIsItsLevel() {
        assertEquals(11, JavaRuntimeLevel.of("11"));
        assertEquals(17, JavaRuntimeLevel.of("17"));
        assertEquals(17, JavaRuntimeLevel.of(" 17 "));
    }

    @Test
    public void aVersionTheJdkCannotHoldABodyToIsTheJdksOwn() {
        assertEquals(0, JavaRuntimeLevel.of("999"));
        assertEquals(0, JavaRuntimeLevel.of("abc"));
        assertEquals(0, JavaRuntimeLevel.of("1234"));
        final int own = Runtime.version().feature();
        assertEquals(own, JavaRuntimeLevel.of(String.valueOf(own)));
        assertEquals(0, JavaRuntimeLevel.of(String.valueOf(own + 1)));
    }
}
