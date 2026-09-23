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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.LiveSnowflake;
import dev.frostlake.jdbc.DatabaseDriver;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
 *
 * <p>A case whose {@code skip} names the backend is replayed anyway on {@code engine}, {@code jdbc} and
 * {@code http}, on a backend of its own: while it still fails it is reported as skipped with the skip's reason,
 * and once it passes it fails as {@code unexpected pass on <backend>: remove the skip ("<reason>")}, so a skip
 * cannot outlive the divergence it records. On {@code snowflake} a skipped case is not run.
 */
public class JsonSuiteTest {

    private static final Logger logger = LoggerFactory.getLogger(JsonSuiteTest.class);
    private static final Map<String, List<String>> CAPABILITY_SKIPS = new LinkedHashMap<>();
    /**
     * The backends that replay a case skipped on them. A live account does not: a replay there costs warehouse
     * time and can leave account state behind.
     */
    private static final Set<String> REPLAYED_SKIPS = new HashSet<>(Arrays.asList("engine", "jdbc", "http"));
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
                runCase(backendName, backend, suiteName + "/" + name, test);
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

    /**
     * Runs one case on a backend: its steps in order, or — when its {@code skip} names the backend — the skip
     * rule: a replay on {@code engine}, {@code jdbc} and {@code http}, an abort elsewhere.
     *
     * @param kind the backend's name, as a case's {@code skip.backends} spells it
     * @param target the run's backend
     * @param fullName the case's suite and name
     * @param test the case
     * @throws Exception when the transport itself fails
     */
    static void runCase(final String kind, final Backend target, final String fullName,
                        final Map<String, Object> test) throws Exception {
        final Map<String, Object> skip = Json.getObj(test, "skip");
        final List<Object> skippedOn = Json.getArr(skip, "backends");
        if (skippedOn != null) {
            for (final Object skipped : skippedOn) {
                if (kind.equalsIgnoreCase(String.valueOf(skipped))) {
                    final String reason = String.valueOf(Json.getStr(skip, "reason"));
                    if (!REPLAYED_SKIPS.contains(kind)) {
                        Assumptions.abort(reason);
                    }
                    replaySkipped(kind, fullName, test, reason);
                    return;
                }
            }
        }
        if ("snowflake".equals(kind)) {
            // A run whose claim on the account was lost stops here rather than disturb the run holding it now.
            LiveSnowflake.checkAccountClaim();
        }
        runSteps(fullName, test, target, true);
    }

    /**
     * Replays a case skipped on this backend, so a skip cannot outlive the divergence it records: a case that
     * now passes fails as an unexpected pass, naming the skip to remove, and a case that still fails is reported
     * as skipped, as before. The replay runs on a backend of its own — a new engine, a driver connection to a new
     * in-process engine, or a new server — so whatever a failing case leaves behind, objects or session settings,
     * cannot reach the cases after it. A run attached to a server's URL replays on a new session of that server
     * instead, which keeps only the session's state apart: every session shares the server's one engine, so the
     * objects the case creates outside {@code test_db} stay there, as any case's do.
     *
     * @param kind the backend's name
     * @param fullName the case's suite and name
     * @param test the case
     * @param reason the skip's reason
     * @throws Exception when the replay's backend cannot be opened or closed
     */
    private static void replaySkipped(final String kind, final String fullName, final Map<String, Object> test,
                                      final String reason) throws Exception {
        final String directName = "testkit_replay_" + System.nanoTime();
        final Backend fresh = "jdbc".equals(kind) ? jdbcBackend(directName) : createBackend(kind);
        boolean passed = false;
        try {
            runSteps(fullName, test, fresh, false);
            passed = true;
        } catch (final Exception | AssertionError stillDiverges) {
            logger.debug("{} still diverges on {}: {}", fullName, kind, stillDiverges.getMessage());
        } finally {
            fresh.close();
            final DatabaseEngine directEngine = DatabaseDriver.unregisterDirectEngine(directName);
            if (directEngine != null) {
                directEngine.shutdown();
            }
        }
        if (passed) {
            fail("unexpected pass on " + kind + ": remove the skip (\"" + reason + "\")");
        }
        Assumptions.abort(reason);
    }

    /**
     * Resets the backend's context and runs the case's steps in order; the first failed check fails the case.
     *
     * @param fullName the case's suite and name
     * @param test the case
     * @param target the backend to run on
     * @param recordSkips whether checks the transport cannot express join the missing-API summary
     * @throws Exception when the transport itself fails
     */
    private static void runSteps(final String fullName, final Map<String, Object> test, final Backend target,
                                 final boolean recordSkips) throws Exception {
        target.resetContext();
        final List<Object> steps = Json.getArr(test, "steps");
        if (steps == null) {
            return;
        }
        for (int i = 0; i < steps.size(); i++) {
            final Map<String, Object> step = Json.obj(steps.get(i));
            final String sql = Json.getStr(step, "sql");
            final ExecResult result = target.execute(sql);
            final Outcome outcome = Compare.check(Json.getObj(step, "expect"), result, target.capabilities());
            if (recordSkips) {
                for (final String check : outcome.capabilitySkips()) {
                    recordCapabilitySkip(check, fullName + " step " + (i + 1));
                }
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
                return jdbcBackend("testkit_" + System.nanoTime());
            case "http":
                return new HttpBackend(System.getProperty("testkit.url", System.getenv("TESTKIT_URL")));
            case "snowflake":
                return new JdbcBackend("snowflake", LiveSnowflake.open(), EnumSet.allOf(Capability.class));
            default:
                throw new IllegalArgumentException("unknown testkit backend: " + kind
                    + " (engine|jdbc|http|snowflake)");
        }
    }

    /**
     * The jdbc backend: Frostlake's driver, connected in-process to the engine a {@code direct:} URL names.
     *
     * @param directName the engine's name, new for a new engine
     * @return the backend
     * @throws Exception when the driver cannot connect
     */
    private static Backend jdbcBackend(final String directName) throws Exception {
        Class.forName("dev.frostlake.jdbc.DatabaseDriver");
        return new JdbcBackend("jdbc", DriverManager.getConnection("jdbc:frostlake:direct:" + directName),
            EnumSet.of(Capability.UPDATE_COUNT, Capability.COLUMN_NAMES, Capability.SESSION));
    }

    @AfterAll
    public static void closeBackendAndReportMissingApis() throws Exception {
        if (backend != null) {
            if ("snowflake".equals(backendName)) {
                dropLiveTestDatabase(backend, LiveSnowflake.holdsAccountClaim());
            }
            backend.close();
        }
        for (final Map.Entry<String, List<String>> entry : CAPABILITY_SKIPS.entrySet()) {
            logger.info("testkit missing API [{}]: {} ({} check(s), e.g. {})", backendName, entry.getKey(),
                entry.getValue().size(), entry.getValue().get(0));
        }
    }

    /**
     * Drops the live run's {@code test_db} before its session closes, only while this run holds the account's
     * claim: once the claim is lost, {@code test_db} belongs to the run that holds the account now.
     *
     * @param live the live backend
     * @param claimHeld whether this run holds the account's claim
     * @throws Exception when the transport itself fails
     */
    static void dropLiveTestDatabase(final Backend live, final boolean claimHeld) throws Exception {
        if (!claimHeld) {
            logger.info("testkit: test_db is not dropped, since this run no longer holds the live account's claim");
            return;
        }
        final ExecResult dropped = live.execute("DROP DATABASE IF EXISTS test_db");
        if (dropped.failed()) {
            logger.warn("testkit: cannot drop test_db on the live account: {}", dropped.getErrorMessage());
        }
    }
}
