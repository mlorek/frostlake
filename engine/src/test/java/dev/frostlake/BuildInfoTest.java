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

package dev.frostlake;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confirms the build-version wiring works end to end: Maven resource filtering must stamp the real
 * {@code ${project.version}} into {@code frostlake-version.properties}, and {@link BuildInfo#version()} must
 * read it — so the console banner tracks the jar version. Asserts on the SHAPE of the version rather than a
 * literal so it survives version bumps.
 */
public class BuildInfoTest {

    @Test
    public void versionIsStampedFromTheBuildNotTheFallback() {
        final String version = BuildInfo.version();
        assertNotNull(version);
        assertFalse(version.isEmpty(), "version must not be blank");
        // Resource filtering must have resolved the placeholder to a concrete Maven version.
        assertFalse(version.startsWith("${"), "the version placeholder was not filtered: " + version);
        assertFalse(BuildInfo.FALLBACK_VERSION.equals(version),
            "expected the stamped build version, got the fallback — filtering/resource wiring is broken");
        // A Maven version begins with a digit (e.g. 0.1.0-SNAPSHOT).
        assertTrue(Character.isDigit(version.charAt(0)), "unexpected version format: " + version);
    }
}
