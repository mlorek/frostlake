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

package dev.frostlake.executor;

import dev.frostlake.metastore.CounterDraw;
import dev.frostlake.metastore.StatementAnswers;
import dev.frostlake.metastore.StatementDraws;
import dev.frostlake.storage.RecordedResult;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.StatementResults;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * What a statement a block or a procedure runs draws, reads by RESULT_SCAN and answers of its identity, recorded apart
 * from the statement that runs it. A statement that commits work in a session of its own there — EXECUTE TASK,
 * EXECUTE ALERT, ALTER PIPE … REFRESH — goes to the write-ahead log at once, in a record of its own, with what it
 * recorded; any other statement's record is the enclosing statement's again, as if never set apart.
 */
public final class ApartRecording {

    private final List<CounterDraw> displacedDraws;
    private final Map<String, ResultSet> displacedResults;
    private final Map<String, List<String>> displacedAnswers;
    private final boolean ranBefore;
    private final Instant started;
    private boolean completed;
    private List<CounterDraw> draws;
    private Map<String, ResultSet> results;
    private Map<String, List<String>> answers;

    private ApartRecording(final boolean ranBefore, final Instant started) {
        this.displacedDraws = StatementDraws.beginApart();
        this.displacedResults = StatementResults.beginApart();
        this.displacedAnswers = StatementAnswers.beginApart();
        this.ranBefore = ranBefore;
        this.started = started;
    }

    /**
     * Starts recording a statement apart, when the calling thread records its statement for the log.
     *
     * @param ranBefore whether work ran in a session of its own before the statement, which stays the enclosing
     *                  statement's
     * @param started   the instant the statement starts at
     * @return the recording, or null when the thread records nothing
     */
    static ApartRecording begin(final boolean ranBefore, final Instant started) {
        return StatementResults.isRecording() ? new ApartRecording(ranBefore, started) : null;
    }

    /** Notes that the statement completed. */
    public void complete() {
        completed = true;
    }

    /** Whether the statement completed. */
    boolean isCompleted() {
        return completed;
    }

    /** Ends recording apart: what the statement recorded is held here, and the enclosing statement records again. */
    void end() {
        draws = StatementDraws.endApart(displacedDraws);
        results = StatementResults.endApart(displacedResults);
        answers = StatementAnswers.endApart(displacedAnswers);
    }

    /** Gives what the statement recorded back to the enclosing statement: it went with no record of its own. */
    void rejoin() {
        StatementDraws.rejoin(displacedDraws, draws);
        StatementResults.rejoin(displacedResults, results);
        StatementAnswers.rejoin(displacedAnswers, answers);
    }

    /** Whether work ran in a session of its own before the statement. */
    boolean ranBefore() {
        return ranBefore;
    }

    /** The instant the statement started at. */
    public Instant getStarted() {
        return started;
    }

    /** What the statement drew from counters, in order. */
    public List<CounterDraw> getDraws() {
        return draws;
    }

    /** The results the statement read, in the order first asked for. */
    public List<RecordedResult> getResults() {
        return StatementResults.recorded(results);
    }

    /** What the statement's identities answered (see {@link StatementAnswers}). */
    public Map<String, List<String>> getAnswers() {
        return answers;
    }
}
