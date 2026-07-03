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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Exposes the build version, stamped in at package time by Maven resource filtering
 * ({@code frostlake-version.properties} carries {@code version=${project.version}}). It therefore always
 * matches the built artifact's version. Falls back to {@link #FALLBACK_VERSION} when the filtered resource
 * is absent or unresolved (e.g. running from sources that were never processed through Maven).
 */
public final class BuildInfo {

    /** Shown when no filtered version resource is on the classpath. */
    public static final String FALLBACK_VERSION = "dev";

    private static final String VERSION = loadVersion();

    private BuildInfo() {
    }

    /** The build version (e.g. {@code "0.1.0-SNAPSHOT"}), or {@link #FALLBACK_VERSION} if it cannot be read. */
    public static String version() {
        return VERSION;
    }

    private static String loadVersion() {
        try (final InputStream in = BuildInfo.class.getResourceAsStream("/frostlake-version.properties")) {
            if (in == null) {
                return FALLBACK_VERSION;
            }
            final Properties props = new Properties();
            props.load(in);
            final String value = props.getProperty("version");
            // Guard against an unfiltered build (placeholder left verbatim) or a blank value.
            if (value == null || value.isEmpty() || value.startsWith("${")) {
                return FALLBACK_VERSION;
            }
            return value;
        } catch (final IOException e) {
            return FALLBACK_VERSION;
        }
    }
}
