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

package dev.frostlake.transaction;

/**
 * What a statement that waits for another session's table lock gives up while it waits: the engine's own lock,
 * which the holder's requests need to commit or roll back. The concurrent engine and an in-process connection hand
 * one to the request they run (see {@link TableLockGate#register}).
 */
public interface EngineLockRelease {

    /** Gives the engine's lock up, the request's session kept as it stands. */
    void release();

    /**
     * Waits a while with the engine's lock given up, before the wait looks at the holder again.
     *
     * @param millis how long
     * @throws InterruptedException when the waiting thread is interrupted
     */
    void pause(long millis) throws InterruptedException;

    /** Takes the engine's lock back and puts the request's session in force again. */
    void reacquire();
}
