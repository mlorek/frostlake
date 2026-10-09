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

import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.parser.FrostlakeLexer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Live-mode housekeeping for ACCOUNT-level objects (databases, roles, users, warehouses, network policies).
 *
 * <p>The test bases recreate {@code test_db} per test, which isolates everything that lives INSIDE a
 * database. Roles, users, warehouses and sibling databases do not live inside {@code test_db}, so a
 * test that creates one and never drops it leaves it on the account: harmless embedded (every test
 * gets a fresh {@link DatabaseEngine}) but cumulative live, where the next test that creates the same
 * name fails with {@code Object 'X' already exists}. Round 6 lost sixteen tests exactly that way
 * ({@code BREADTH_ROLE}, {@code WH}, {@code ALICE}, {@code DB1}, …).
 *
 * <p>The account is shared, so the teardown drops only what THIS run created: every statement the suite
 * sends is read first ({@link #observe(String)} — through {@link LiveSnowflakeEngine}, or through the
 * connection {@link #observing(Connection)} wraps for the tests that talk straight JDBC), and the name each
 * {@code CREATE} of a database, role, user, warehouse or network policy writes is recorded, a {@code CLONE} target and a
 * statement inside an {@code EXECUTE IMMEDIATE} text included. The teardown drops the recorded names and
 * nothing else. An object that was on the account before the first test is never dropped, whoever names it.
 *
 * <p>What the suite cannot see written — an object a stored procedure creates — is not dropped but
 * reported: a second pass compares the account with the baseline the first live test recorded and logs
 * every object beyond it that this run did not create. Another session's work shows up there too, and is
 * left alone. That pass costs one {@code SHOW} per object kind, so it runs only after a test that touched
 * an account-level object. Nothing here executes unless {@code SF_LIVE=1}.
 */
public final class LiveAccountObjects {

    private static final Logger logger = LoggerFactory.getLogger(LiveAccountObjects.class);

    /** The {@code SHOW} target enumerating each account-level object kind, and that kind's DROP verb. */
    private static final String[][] KINDS = {
        {"DATABASES", "DATABASE"},
        {"ROLES", "ROLE"},
        {"USERS", "USER"},
        {"WAREHOUSES", "WAREHOUSE"},
        {"NETWORK POLICIES", "NETWORK POLICY"},
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

    /** The account-level objects this run's statements created and has not dropped yet, per DROP verb. */
    private static final Map<String, Set<String>> CREATED = new LinkedHashMap<String, Set<String>>();

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
     * Reads a statement the suite is about to send to the account: records the account-level objects it
     * creates, and flags the test as one that touched such an object.
     *
     * @param sql the statement text
     */
    public static synchronized void observe(final String sql) {
        if (sql == null) {
            return;
        }
        for (final String[] made : createdBy(sql)) {
            Set<String> names = CREATED.get(made[0]);
            if (names == null) {
                names = new LinkedHashSet<String>();
                CREATED.put(made[0], names);
            }
            if (made.length > 2) {
                // A rename: the object answers to its new name alone, and stays this run's only if it was.
                if (!names.remove(made[2])) {
                    continue;
                }
            }
            names.add(made[1]);
            sawAccountObjectStatement = true;
        }
    }

    /**
     * The account-level objects this run has created and not dropped, per kind; for the harness's own tests.
     *
     * @param kind the object kind, as its DROP verb spells it
     * @return the names recorded for it
     */
    static synchronized Set<String> recorded(final String kind) {
        final Set<String> names = CREATED.get(kind);
        return names == null ? new LinkedHashSet<String>() : new LinkedHashSet<String>(names);
    }

    /** Forgets every recorded name; for the harness's own tests. */
    static synchronized void forgetRecorded() {
        CREATED.clear();
    }

    /**
     * The account-level objects a statement creates, read off its tokens: each {@code CREATE} or
     * {@code UNDROP} of a database, role, user, warehouse or network policy — a {@code CLONE} target is the name after the
     * kind like any other — wherever it stands, the text of a string the statement carries included, since
     * that is how {@code EXECUTE IMMEDIATE} and a procedure body write one. An {@code ALTER … RENAME TO}
     * answers the new name with the old one beside it.
     *
     * @param sql the statement text
     * @return one entry per object: the kind as its DROP verb spells it and the name as the account holds
     *         it, and for a rename the name it had
     */
    static List<String[]> createdBy(final String sql) {
        final List<String[]> made = new ArrayList<String[]>();
        final List<Token> tokens = new ArrayList<Token>();
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        lexer.removeErrorListeners();
        Token token = lexer.nextToken();
        while (token.getType() != Token.EOF) {
            if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                tokens.add(token);
            }
            token = lexer.nextToken();
        }
        for (int i = 0; i < tokens.size(); i++) {
            final Token at = tokens.get(i);
            if (at.getType() == FrostlakeLexer.STRING_LITERAL) {
                made.addAll(createdBy(SqlStringLiterals.decode(at.getText())));
                continue;
            }
            if (at.getType() == FrostlakeLexer.DOLLAR_QUOTED_STRING && at.getText().length() >= 4) {
                made.addAll(createdBy(at.getText().substring(2, at.getText().length() - 2)));
                continue;
            }
            final String word = at.getText().toUpperCase(Locale.ROOT);
            final boolean creates = "CREATE".equals(word) || "UNDROP".equals(word);
            if (!creates && !"ALTER".equals(word)) {
                continue;
            }
            int next = i + 1;
            for (int skipped = 0; skipped < 6 && next < tokens.size()
                    && isObjectKindModifier(tokens.get(next).getText().toUpperCase(Locale.ROOT)); skipped++) {
                next++;
            }
            if (next >= tokens.size()) {
                continue;
            }
            String kind = tokens.get(next).getText().toUpperCase(Locale.ROOT);
            if ("NETWORK".equals(kind) && next + 1 < tokens.size()
                    && "POLICY".equalsIgnoreCase(tokens.get(next + 1).getText())) {
                // The one account-level kind two words name; a network RULE lives inside a schema.
                kind = "NETWORK POLICY";
                next++;
            }
            if (!isAccountObjectKind(kind)) {
                continue;
            }
            next++;
            if ("DATABASE".equals(kind) && next < tokens.size()
                    && "ROLE".equalsIgnoreCase(tokens.get(next).getText())) {
                // A database role lives inside its database, and goes with it.
                continue;
            }
            next = afterIfExists(tokens, next);
            final String name = next < tokens.size() ? nameOf(tokens.get(next)) : null;
            if (name == null || next + 1 < tokens.size() && "(".equals(tokens.get(next + 1).getText())) {
                // No plain name: an IDENTIFIER(…) call names the object by a value the text does not show.
                continue;
            }
            if (creates) {
                made.add(new String[] {kind, name});
            } else if (next + 3 < tokens.size() && "RENAME".equalsIgnoreCase(tokens.get(next + 1).getText())
                    && "TO".equalsIgnoreCase(tokens.get(next + 2).getText())) {
                final String renamed = nameOf(tokens.get(next + 3));
                if (renamed != null) {
                    made.add(new String[] {kind, renamed, name});
                }
            }
        }
        return made;
    }

    /** The position after an {@code IF [NOT] EXISTS} written at {@code from}, or {@code from} itself. */
    private static int afterIfExists(final List<Token> tokens, final int from) {
        if (from >= tokens.size() || !"IF".equalsIgnoreCase(tokens.get(from).getText())) {
            return from;
        }
        int next = from + 1;
        if (next < tokens.size() && "NOT".equalsIgnoreCase(tokens.get(next).getText())) {
            next++;
        }
        return next < tokens.size() && "EXISTS".equalsIgnoreCase(tokens.get(next).getText()) ? next + 1 : from;
    }

    /** The name a token writes, as the account holds it; null for anything that is not a plain name. */
    private static String nameOf(final Token token) {
        final String text = token.getText();
        if (token.getType() == FrostlakeLexer.QUOTED_IDENTIFIER && text.length() >= 2) {
            return text.substring(1, text.length() - 1).replace("\"\"", "\"");
        }
        if (text.isEmpty() || !(Character.isLetter(text.charAt(0)) || text.charAt(0) == '_')) {
            return null;
        }
        return text.toUpperCase(Locale.ROOT);
    }

    private static boolean isObjectKindModifier(final String keyword) {
        switch (keyword) {
            case "OR":
            case "REPLACE":
            case "TRANSIENT":
            case "TEMPORARY":
            case "TEMP":
            case "VOLATILE":
            case "SECURE":
            case "LOCAL":
            case "GLOBAL":
                return true;
            default:
                return false;
        }
    }

    private static boolean isAccountObjectKind(final String keyword) {
        switch (keyword) {
            case "DATABASE":
            case "ROLE":
            case "USER":
            case "WAREHOUSE":
            case "NETWORK POLICY":
                return true;
            default:
                return false;
        }
    }

    /**
     * A connection whose statements are read before they are sent, for the tests that talk straight JDBC:
     * every SQL text handed to it, or to a statement it makes, passes through {@link #observe(String)}.
     *
     * @param connection the live connection
     * @return the same connection, observed
     */
    public static Connection observing(final Connection connection) {
        return (Connection) Proxy.newProxyInstance(LiveAccountObjects.class.getClassLoader(),
            new Class<?>[] {Connection.class}, new InvocationHandler() {
                @Override
                public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
                    observeFirstArgument(args);
                    final Object answered = invokeOn(connection, method, args);
                    if (answered instanceof CallableStatement) {
                        return observingStatement((Statement) answered, CallableStatement.class);
                    }
                    if (answered instanceof PreparedStatement) {
                        return observingStatement((Statement) answered, PreparedStatement.class);
                    }
                    if (answered instanceof Statement) {
                        return observingStatement((Statement) answered, Statement.class);
                    }
                    return answered;
                }
            });
    }

    private static Object observingStatement(final Statement statement, final Class<?> kind) {
        return Proxy.newProxyInstance(LiveAccountObjects.class.getClassLoader(), new Class<?>[] {kind},
            new InvocationHandler() {
                @Override
                public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
                    observeFirstArgument(args);
                    return invokeOn(statement, method, args);
                }
            });
    }

    /** A SQL text is always a call's first argument: prepareStatement, execute, executeQuery, addBatch. */
    private static void observeFirstArgument(final Object[] args) {
        if (args != null && args.length > 0 && args[0] instanceof String) {
            observe((String) args[0]);
        }
    }

    private static Object invokeOn(final Object target, final Method method, final Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (final InvocationTargetException thrown) {
            throw thrown.getCause();
        }
    }

    /**
     * Drops the account-level objects this run created, so the next test starts from the account state the run
     * started with, and reports what else is on the account beyond the baseline without touching it: an object
     * the suite's statements did not name is another session's, or one a procedure made. An object that was
     * there before the run is never dropped. Best effort: a kind whose {@code SHOW} cannot be read is not
     * reported. Nothing is dropped once this run no longer holds the account's claim.
     */
    public static synchronized void dropNewAccountObjects(final Connection connection) {
        if (baseline == null || !LiveSnowflake.holdsAccountClaim()) {
            return;
        }
        for (int i = 0; i < KINDS.length; i++) {
            final String kind = KINDS[i][1];
            final Set<String> created = CREATED.remove(kind);
            if (created == null) {
                continue;
            }
            final Set<String> before = baseline.get(i);
            for (final String name : created) {
                if (before == null || before.contains(name) || isHarnessObject(kind, name)) {
                    continue;
                }
                drop(connection, kind, name);
            }
        }
        final List<Set<String>> now = currentNames(connection);
        for (int i = 0; i < KINDS.length; i++) {
            final Set<String> before = baseline.get(i);
            final Set<String> after = now.get(i);
            if (before == null || after == null) {
                continue;
            }
            for (final String name : after) {
                if (!before.contains(name) && !isHarnessObject(KINDS[i][1], name)) {
                    logger.info("Live cleanup: {} {} is on the account beyond the baseline and was not created by"
                        + " this run's statements; left alone", KINDS[i][1], name);
                }
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
