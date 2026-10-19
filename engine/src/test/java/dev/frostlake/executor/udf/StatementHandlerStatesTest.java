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

import dev.frostlake.executor.expressions.SourcePosition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * What handler runtimes keep for a statement lasts as long as the statement: a state is made once for a routine
 * and the place that calls it, another place and another routine get their own, a statement run inside another
 * has states of its own, and ending a statement closes its states and those of any statement left open inside it.
 */
public class StatementHandlerStatesTest {

    private final List<String> closed = new ArrayList<>();

    private HandlerStateFactory named(final String name) {
        return new HandlerStateFactory() {
            @Override
            public AutoCloseable create() {
                return new AutoCloseable() {
                    @Override
                    public void close() {
                        closed.add(name);
                    }

                    @Override
                    public String toString() {
                        return name;
                    }
                };
            }
        };
    }

    @Test
    public void noStateIsKeptOutsideAStatement() {
        assertNull(StatementHandlerStates.state("f", "UTC", named("a")));
    }

    @Test
    public void aStateIsMadeOnceForARoutineAndItsCallSite() {
        final Object routine = new Object();
        final Object site = new Object();
        final HandlerStateScope scope = StatementHandlerStates.begin();
        try {
            final Object outer = StatementHandlerStates.enterCall(site, "F", 0, null);
            final AutoCloseable first = StatementHandlerStates.state(routine, "UTC", named("a"));
            assertSame(first, StatementHandlerStates.state(routine, "UTC", named("b")));
            assertNotSame(first, StatementHandlerStates.state(routine, "Europe/Warsaw", named("c")));
            assertNotSame(first, StatementHandlerStates.state(new Object(), "UTC", named("d")));
            final Object inner = StatementHandlerStates.enterCall(new Object(), "F", 0, null);
            assertNotSame(first, StatementHandlerStates.state(routine, "UTC", named("e")));
            StatementHandlerStates.exitSite(inner);
            assertSame(first, StatementHandlerStates.state(routine, "UTC", named("f")));
            StatementHandlerStates.exitSite(outer);
        } finally {
            StatementHandlerStates.end(scope);
        }
        assertEquals(4, closed.size());
        assertNull(StatementHandlerStates.state(routine, "UTC", named("g")));
    }

    @Test
    public void oneCallWrittenInTwoPlacesHasTwoStates() {
        final Object routine = new Object();
        final Object call = new Object();
        final HandlerStateScope scope = StatementHandlerStates.begin();
        try {
            final Object first = StatementHandlerStates.enterCall(call, "f", 1, new SourcePosition(1, 7));
            final AutoCloseable atFirst = StatementHandlerStates.state(routine, "UTC", named("a"));
            StatementHandlerStates.exitSite(first);
            final Object again = StatementHandlerStates.enterCall(call, "F", 1, new SourcePosition(1, 7));
            assertSame(atFirst, StatementHandlerStates.state(routine, "UTC", named("b")));
            StatementHandlerStates.exitSite(again);
            final Object second = StatementHandlerStates.enterCall(call, "F", 1, new SourcePosition(1, 15));
            assertNotSame(atFirst, StatementHandlerStates.state(routine, "UTC", named("c")));
            StatementHandlerStates.exitSite(second);
        } finally {
            StatementHandlerStates.end(scope);
        }
    }

    @Test
    public void aCallInABodyIsToldByItsNameAndCount() {
        final Object routine = new Object();
        final HandlerStateScope scope = StatementHandlerStates.begin();
        try {
            final Object outer = StatementHandlerStates.enterCall(new Object(), "WRAP", 1, new SourcePosition(1, 7));
            final Object firstRow = StatementHandlerStates.enterCall(new Object(), "CNT", 1, null);
            final AutoCloseable kept = StatementHandlerStates.state(routine, "UTC", named("a"));
            StatementHandlerStates.exitSite(firstRow);
            final Object secondRow = StatementHandlerStates.enterCall(new Object(), "cnt", 1, null);
            assertSame(kept, StatementHandlerStates.state(routine, "UTC", named("b")));
            StatementHandlerStates.exitSite(secondRow);
            final Object secondCall = StatementHandlerStates.enterCall(new Object(), "CNT", 2, null);
            assertNotSame(kept, StatementHandlerStates.state(routine, "UTC", named("c")));
            StatementHandlerStates.exitSite(secondCall);
            StatementHandlerStates.exitSite(outer);
        } finally {
            StatementHandlerStates.end(scope);
        }
    }

    @Test
    public void aStatementInsideAnotherHasStatesOfItsOwn() {
        final Object routine = new Object();
        final HandlerStateScope outer = StatementHandlerStates.begin();
        final AutoCloseable kept = StatementHandlerStates.state(routine, "UTC", named("outer"));
        final HandlerStateScope inner = StatementHandlerStates.begin();
        assertNotSame(kept, StatementHandlerStates.state(routine, "UTC", named("inner")));
        StatementHandlerStates.end(inner);
        assertEquals(List.of("inner"), closed);
        assertSame(kept, StatementHandlerStates.state(routine, "UTC", named("again")));
        StatementHandlerStates.end(outer);
        assertEquals(List.of("inner", "outer"), closed);
    }

    @Test
    public void endingAStatementEndsWhatWasLeftOpenInsideIt() {
        final HandlerStateScope outer = StatementHandlerStates.begin();
        StatementHandlerStates.state("f", "UTC", named("outer"));
        final HandlerStateScope left = StatementHandlerStates.begin();
        StatementHandlerStates.state("f", "UTC", named("left"));
        StatementHandlerStates.end(outer);
        assertEquals(List.of("left", "outer"), closed);
        // The scope left open was closed with the statement around it; ending it again changes nothing.
        StatementHandlerStates.end(left);
        assertEquals(List.of("left", "outer"), closed);
        assertNull(StatementHandlerStates.state("f", "UTC", named("after")));
    }
}
