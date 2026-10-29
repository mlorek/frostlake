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

package dev.frostlake.rt.py;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The venv a JVM's GraalPy interpreters import from: open while engines are configured, fixed by the first Python call
 * naming a venv or by the first interpreter built, in the step that checks it, so two first calls naming two venvs
 * cannot both pass. Each case holds a choice of its own, leaving the JVM's interpreters alone.
 */
public class PythonVenvChoiceTest {

    private static String refusal(final PythonVenvChoice choice, final String requested) {
        return assertThrows(IllegalStateException.class, new Executable() {
            @Override
            public void execute() {
                choice.require(requested);
            }
        }).getMessage();
    }

    @Test
    public void aFirstCallNamingAVenvFixesItAgainstAnother() {
        final PythonVenvChoice choice = new PythonVenvChoice();
        choice.configure("/venvs/b");
        choice.require("/venvs/a");
        assertEquals("python.venv '/venvs/b' cannot be used: GraalPy runs one venv a JVM, and its interpreters here"
            + " import from '/venvs/a'", refusal(choice, "/venvs/b"));
        // The engines that agree, and those naming none, go on calling.
        choice.require("/venvs/a");
        choice.require(null);
        choice.configure("/venvs/c");
        assertEquals("/venvs/a", choice.build());
    }

    @Test
    public void theLastEngineConfiguredNamesTheVenvUntilAnInterpreterIsBuilt() {
        final PythonVenvChoice choice = new PythonVenvChoice();
        choice.configure("/venvs/a");
        choice.configure("/venvs/b");
        // A call naming no venv leaves the choice open.
        choice.require(null);
        choice.configure("/venvs/c");
        assertEquals("/venvs/c", choice.build());
        choice.configure("/venvs/d");
        assertEquals("/venvs/c", choice.build());
        assertEquals("python.venv '/venvs/d' cannot be used: GraalPy runs one venv a JVM, and its interpreters here"
            + " import from '/venvs/c'", refusal(choice, "/venvs/d"));
    }

    @Test
    public void interpretersBuiltWithoutAVenvRefuseOne() {
        final PythonVenvChoice choice = new PythonVenvChoice();
        assertNull(choice.build());
        assertEquals("python.venv '/venvs/a' cannot be used: GraalPy runs one venv a JVM, and its interpreters here"
            + " were started without one", refusal(choice, "/venvs/a"));
        choice.require(null);
    }

    @Test
    public void twoFirstCallsNamingTwoVenvsCannotBothPass() throws InterruptedException {
        final PythonVenvChoice choice = new PythonVenvChoice();
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger refused = new AtomicInteger();
        final Thread[] callers = new Thread[2];
        for (int i = 0; i < callers.length; i++) {
            final String venv = "/venvs/" + i;
            callers[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                        choice.require(venv);
                    } catch (final InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } catch (final IllegalStateException other) {
                        refused.incrementAndGet();
                    }
                }
            });
            callers[i].start();
        }
        start.countDown();
        for (final Thread caller : callers) {
            caller.join();
        }
        assertEquals(1, refused.get());
    }
}
