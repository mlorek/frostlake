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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Live-mode housekeeping for ACCOUNT-level objects (databases, roles, users, warehouses).
 *
 * <p>The test bases recreate {@code test_db} per test, which isolates everything that lives INSIDE a
 * database. Roles, users, warehouses and sibling databases do not live inside {@code test_db}, so a
 * test that creates one and never drops it leaves it on the account: harmless embedded (every test
 * gets a fresh {@link DatabaseEngine}) but cumulative live, where the next test that creates the same
 * name fails with {@code Object 'X' already exists}. Round 6 lost sixteen tests exactly that way
 * ({@code BREADTH_ROLE}, {@code WH}, {@code ALICE}, {@code DB1}, …).
 *
 * <p>The fix is a diff, not a list of names: the first live test records which account-level objects
 * already existed, and each test's teardown drops whatever is on the account beyond that baseline.
 * Because it compares state rather than parsing statements, it also cleans up objects created
 * indirectly — through {@code EXECUTE IMMEDIATE}, a stored procedure, or {@code CLONE}.
 *
 * <p>The diff costs one {@code SHOW} per object kind, so {@link BaseDatabaseTest} only runs it when
 * {@link LiveSnowflakeEngine} actually saw an account-object statement ({@link
 * #markAccountObjectStatement()}); {@link BaseJdbcTest} talks straight JDBC with nothing to observe
 * its statements, and runs the diff unconditionally. Nothing here executes unless {@code SF_LIVE=1}.
 */
public final class LiveAccountObjects {

    private static final Logger logger = LoggerFactory.getLogger(LiveAccountObjects.class);

    /** The {@code SHOW} target enumerating each account-level object kind, and that kind's DROP verb. */
    private static final String[][] KINDS = {
        {"DATABASES", "DATABASE"},
        {"ROLES", "ROLE"},
        {"USERS", "USER"},
        {"WAREHOUSES", "WAREHOUSE"},
    };

    /**
     * The harness's own databases, which no teardown drops whatever the baseline says: the working database,
     * recreated per test by the bases and dropped at JVM exit, and the one holding the account claim, which
     * outlives every run so the next run can read who holds the account.
     */
    private static final Set<String> HARNESS_DATABASES = new HashSet<String>(Arrays.asList(
        "TEST_DB", LiveAccountClaim.DATABASE));

    /** How many names each kind had before the first test ran; a null entry means "could not read". */
    private static List<Set<String>> baseline;

    private static boolean sawAccountObjectStatement;

    private LiveAccountObjects() {
    }

    /**
     * Records what the account already held, once per JVM. Called from both test bases' live setup so
     * whichever runs first establishes the baseline — everything beyond it belongs to the suite.
     */
    public static synchronized void captureBaseline(final Connection connection) {
        if (baseline != null) {
            return;
        }
        baseline = currentNames(connection);
    }

    /** Clears the per-test flag; called once the base's own setup statements are out of the way. */
    public static synchronized void beginTest() {
        sawAccountObjectStatement = false;
    }

    /** Records that the running test issued a CREATE/DROP/ALTER/UNDROP against an account-level object. */
    public static synchronized void markAccountObjectStatement() {
        sawAccountObjectStatement = true;
    }

    /** Whether the running test touched an account-level object, so the teardown diff is worth running. */
    public static synchronized boolean sawAccountObjectStatement() {
        return sawAccountObjectStatement;
    }

    /**
     * Drops every account-level object that is not in the baseline, so the next test starts from the
     * account state the run started with. Best effort: a kind whose {@code SHOW} cannot be read is
     * skipped entirely rather than treated as empty (treating an unreadable listing as empty would
     * make every existing object look new). Nothing is dropped once this run no longer holds the
     * account's claim: the objects beyond the baseline are then the holder's.
     */
    public static synchronized void dropNewAccountObjects(final Connection connection) {
        if (baseline == null || !LiveSnowflake.holdsAccountClaim()) {
            return;
        }
        final List<Set<String>> now = currentNames(connection);
        for (int i = 0; i < KINDS.length; i++) {
            final Set<String> before = baseline.get(i);
            final Set<String> after = now.get(i);
            if (before == null || after == null) {
                continue;
            }
            for (final String name : after) {
                if (before.contains(name) || isHarnessObject(KINDS[i][1], name)) {
                    continue;
                }
                drop(connection, KINDS[i][1], name);
            }
        }
    }

    /**
     * Whether the teardown keeps an account-level object whatever the baseline says: the harness's own
     * databases, named in any case.
     *
     * @param kind the object kind, as its DROP verb spells it
     * @param name the object's name, as SHOW reports it
     * @return whether the object belongs to the harness
     */
    static boolean isHarnessObject(final String kind, final String name) {
        return "DATABASE".equals(kind) && HARNESS_DATABASES.contains(name.toUpperCase(Locale.ROOT));
    }

    private static List<Set<String>> currentNames(final Connection connection) {
        final List<Set<String>> names = new ArrayList<Set<String>>();
        for (final String[] kind : KINDS) {
            names.add(namesOf(connection, kind[0]));
        }
        return names;
    }

    /** The {@code name} column of {@code SHOW <target>}, uppercased; null when the listing is unreadable. */
    private static Set<String> namesOf(final Connection connection, final String showTarget) {
        final Set<String> names = new HashSet<String>();
        try (final Statement statement = connection.createStatement();
             final ResultSet rs = statement.executeQuery("SHOW " + showTarget)) {
            while (rs.next()) {
                // The name is kept EXACTLY as SHOW reports it. Uppercasing it here and then
                // quoting it in drop() produced DROP DATABASE IF EXISTS "MIXEDDB" for a database
                // actually named mixedDb: an exact-match miss that IF EXISTS turns into a silent
                // no-op, so every quoted mixed-case object leaked for good. Baseline and current
                // listings both come from here, so they still compare like for like.
                names.add(rs.getString("name"));
            }
        } catch (final SQLException e) {
            logger.warn("Live cleanup: cannot list {} ({}) — that kind will not be cleaned up",
                showTarget, e.getMessage());
            return null;
        }
        return names;
    }

    private static void drop(final Connection connection, final String kind, final String name) {
        try (final Statement statement = connection.createStatement()) {
            statement.execute("DROP " + kind + " IF EXISTS \"" + name.replace("\"", "\"\"") + "\"");
            logger.info("Live cleanup: dropped leftover {} {}", kind, name);
        } catch (final SQLException e) {
            logger.warn("Live cleanup: cannot drop {} {} ({})", kind, name, e.getMessage());
        }
    }
}
