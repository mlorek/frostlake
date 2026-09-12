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

package dev.frostlake.testkit;

import dev.frostlake.LiveSnowflake;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Runs the data-driven SQL suites in {@code src/test/resources/testkit/suites/*.json} — the
 * engine-owned, language-neutral test definitions: commands, expected values and expected refusals
 * (see {@code testkit/SCHEMA.md}). Driver repositories run the same files with their own thin runners;
 * this class is the engine's JUnit entry point.
 *
 * <p>The backend follows the suite's usual switches: {@code SF_LIVE=1} runs every statement on the live
 * account over JDBC, {@code FL_JDBC=1} through Frostlake's own driver, and otherwise an in-process engine
 * runs them. {@code -Dtestkit.backend=engine|jdbc|http|snowflake} (or {@code TESTKIT_BACKEND}) picks one
 * outright; {@code http} starts the engine's own HTTP server in-process, or attaches to
 * {@code -Dtestkit.url}. With {@code -Dtestkit.suites=a,b} (or {@code TESTKIT_SUITES}) only the suites
 * whose name contains one of the listed words run — a live replay of the whole corpus takes hours.
 *
 * <p>A check the transport cannot express, such as an error code over HTTP, is skipped and summarized at
 * the end rather than failed — that summary is the transport's missing-API list.
 */
public class JsonSuiteTest {

    private static final Logger logger = LoggerFactory.getLogger(JsonSuiteTest.class);
    private static final Map<String, List<String>> CAPABILITY_SKIPS = new LinkedHashMap<>();
    private static Backend backend;
    private static String backendName;

    @TestFactory
    public List<DynamicNode> jsonSuites() throws Exception {
        backendName = pickBackend();
        backend = createBackend(backendName);
        final List<String> wanted = wantedSuites();
        final List<DynamicNode> containers = new ArrayList<>();
        for (final Path file : suiteFiles()) {
            final Map<String, Object> suite = Json.obj(Json.parse(
                new String(Files.readAllBytes(file), StandardCharsets.UTF_8)));
            final String suiteName = Json.getStr(suite, "suite");
            if (!isWanted(wanted, suiteName)) {
                continue;
            }
            final List<DynamicTest> tests = new ArrayList<>();
            for (final Object each : Json.getArr(suite, "tests")) {
                tests.add(dynamicTest(suiteName, Json.obj(each)));
            }
            containers.add(DynamicContainer.dynamicContainer(suiteName + " [" + backendName + "]", tests));
        }
        return containers;
    }

    private static DynamicTest dynamicTest(final String suiteName, final Map<String, Object> test) {
        final String name = Json.getStr(test, "name");
        return DynamicTest.dynamicTest(name, new Executable() {
            @Override
            public void execute() throws Throwable {
                runTest(suiteName + "/" + name, test);
            }
        });
    }

    private static List<Path> suiteFiles() throws URISyntaxException, IOException {
        final Path dir = Paths.get(JsonSuiteTest.class.getResource("/testkit/suites").toURI());
        final List<Path> files = new ArrayList<>();
        try (final DirectoryStream<Path> listing = Files.newDirectoryStream(dir, "*.json")) {
            for (final Path file : listing) {
                files.add(file);
            }
        }
        Collections.sort(files);
        return files;
    }

    private static List<String> wantedSuites() {
        final String listed = System.getProperty("testkit.suites", System.getenv("TESTKIT_SUITES"));
        final List<String> wanted = new ArrayList<>();
        if (listed != null) {
            for (final String word : listed.split(",")) {
                if (!word.isBlank()) {
                    wanted.add(word.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return wanted;
    }

    private static boolean isWanted(final List<String> wanted, final String suiteName) {
        if (wanted.isEmpty()) {
            return true;
        }
        final String name = String.valueOf(suiteName).toLowerCase(Locale.ROOT);
        for (final String word : wanted) {
            if (name.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static void runTest(final String fullName, final Map<String, Object> test) throws Exception {
        final Map<String, Object> skip = Json.getObj(test, "skip");
        final List<Object> skippedOn = Json.getArr(skip, "backends");
        if (skippedOn != null) {
            for (final Object skipped : skippedOn) {
                if (backendName.equalsIgnoreCase(String.valueOf(skipped))) {
                    Assumptions.abort(String.valueOf(Json.getStr(skip, "reason")));
                }
            }
        }
        backend.resetContext();
        final List<Object> steps = Json.getArr(test, "steps");
        if (steps == null) {
            return;
        }
        for (int i = 0; i < steps.size(); i++) {
            final Map<String, Object> step = Json.obj(steps.get(i));
            final String sql = Json.getStr(step, "sql");
            final ExecResult result = backend.execute(sql);
            final Outcome outcome = Compare.check(Json.getObj(step, "expect"), result, backend.capabilities());
            for (final String check : outcome.capabilitySkips()) {
                recordCapabilitySkip(check, fullName + " step " + (i + 1));
            }
            if (!outcome.passed()) {
                fail(fullName + " step " + (i + 1) + ": " + outcome.detail() + "  [sql: " + sql + "]");
            }
        }
    }

    private static synchronized void recordCapabilitySkip(final String check, final String where) {
        List<String> places = CAPABILITY_SKIPS.get(check);
        if (places == null) {
            places = new ArrayList<>();
            CAPABILITY_SKIPS.put(check, places);
        }
        places.add(where);
    }

    private static String pickBackend() {
        final String explicit = System.getProperty("testkit.backend", System.getenv("TESTKIT_BACKEND"));
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim().toLowerCase(Locale.ROOT);
        }
        if (LiveSnowflake.enabled()) {
            return "snowflake";
        }
        if ("1".equals(System.getenv("FL_JDBC"))) {
            return "jdbc";
        }
        return "engine";
    }

    private static Backend createBackend(final String kind) throws Exception {
        switch (kind) {
            case "engine":
                return new EngineBackend();
            case "jdbc":
                Class.forName("dev.frostlake.jdbc.DatabaseDriver");
                return new JdbcBackend("jdbc",
                    DriverManager.getConnection("jdbc:frostlake:direct:testkit_" + System.nanoTime()),
                    EnumSet.of(Capability.UPDATE_COUNT, Capability.COLUMN_NAMES, Capability.SESSION), null);
            case "http":
                return new HttpBackend(System.getProperty("testkit.url", System.getenv("TESTKIT_URL")));
            case "snowflake":
                return new JdbcBackend("snowflake", LiveSnowflake.open(), EnumSet.allOf(Capability.class),
                    "DROP DATABASE IF EXISTS test_db");
            default:
                throw new IllegalArgumentException("unknown testkit backend: " + kind
                    + " (engine|jdbc|http|snowflake)");
        }
    }

    @AfterAll
    public static void closeBackendAndReportMissingApis() throws Exception {
        if (backend != null) {
            backend.close();
        }
        for (final Map.Entry<String, List<String>> entry : CAPABILITY_SKIPS.entrySet()) {
            logger.info("testkit missing API [{}]: {} ({} check(s), e.g. {})", backendName, entry.getKey(),
                entry.getValue().size(), entry.getValue().get(0));
        }
    }
}
