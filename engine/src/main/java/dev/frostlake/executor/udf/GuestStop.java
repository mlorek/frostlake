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

import dev.frostlake.executor.StatementCancellation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One guest execution a {@link GuestWatch} watches: the token of the statement it runs for, and what ends it, run
 * once the statement is over.
 */
final class GuestStop {

    private static final Logger logger = LoggerFactory.getLogger(GuestStop.class);

    /** The token of the statement the guest runs for. */
    private final StatementCancellation token;

    /** What ends the guest's execution. */
    private final Runnable stop;

    /** Whether {@link #stop} has run. */
    private final AtomicBoolean stopped = new AtomicBoolean();

    GuestStop(final StatementCancellation token, final Runnable stop) {
        this.token = token;
        this.stop = stop;
    }

    /**
     * End the guest if its statement is over. The end runs on a thread of its own: closing an interpreter can block
     * on the guest's own locks, and the watcher must go on watching the other statements.
     */
    void stopIfOver() {
        if (!token.isOver() || !stopped.compareAndSet(false, true)) {
            return;
        }
        final Thread stopper = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    stop.run();
                } catch (final RuntimeException unstoppable) {
                    logger.debug("A guest execution could not be stopped: {}", unstoppable.getMessage());
                }
            }
        }, "frostlake-guest-stop");
        stopper.setDaemon(true);
        stopper.start();
    }
}
