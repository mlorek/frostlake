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

package dev.frostlake.session;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.http.SessionContext;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Sessions running window queries at once each get their own query's answer: the window computation a thread runs
 * keeps its state to itself, so a grouped RANK or LAG is never computed from another session's state.
 */
public class WindowStateAcrossSessionsTest {

    private static final String GROUPED = "SELECT g, SUM(v) s, RANK() OVER (ORDER BY SUM(v) DESC) r, "
        + "LAG(SUM(v)) OVER (ORDER BY g) l FROM t GROUP BY g ORDER BY g";
    private static final String RUNNING = "SELECT v, ROW_NUMBER() OVER (PARTITION BY g ORDER BY v) rn, "
        + "SUM(v) OVER (PARTITION BY g ORDER BY v) cs FROM t ORDER BY v";
    private static final int THREADS = 4;
    private static final int ROUNDS = 25;

    private ConcurrentDatabaseEngine concurrent;

    @AfterEach
    public void tearDown() {
        if (concurrent != null) {
            concurrent.shutdown();
        }
    }

    /** A result's rows as one text, which two answers are compared by. */
    private static String rows(final ResultSet result) {
        final StringBuilder text = new StringBuilder();
        for (final Row row : result.getRows()) {
            text.append(row.getValues()).append(';');
        }
        return text.toString();
    }

    @Test
    public void eachSessionGetsItsOwnWindowAnswer() throws InterruptedException {
        concurrent = new ConcurrentDatabaseEngine();
        final SessionContext setup = concurrent.createSession();
        concurrent.execute("CREATE DATABASE w_db", setup);
        concurrent.execute("USE SCHEMA w_db.PUBLIC", setup);
        concurrent.execute("CREATE TABLE t (g INT, v INT)", setup);
        concurrent.execute("INSERT INTO t SELECT SEQ4() % 7, SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => 200))", setup);
        final String grouped = rows(concurrent.executeQuery(GROUPED, setup));
        final String running = rows(concurrent.executeQuery(RUNNING, setup));
        final List<String> wrong = Collections.synchronizedList(new ArrayList<String>());
        final List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            final SessionContext session = concurrent.createSession();
            concurrent.execute("USE SCHEMA w_db.PUBLIC", session);
            threads.add(new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int round = 0; round < ROUNDS; round++) {
                        if (!grouped.equals(rows(concurrent.executeQuery(GROUPED, session)))) {
                            wrong.add("grouped");
                        }
                        if (!running.equals(rows(concurrent.executeQuery(RUNNING, session)))) {
                            wrong.add("running");
                        }
                    }
                }
            }));
        }
        for (final Thread thread : threads) {
            thread.start();
        }
        for (final Thread thread : threads) {
            thread.join();
        }
        assertEquals(new ArrayList<String>(), wrong);
    }
}
