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

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.opentest4j.TestAbortedException;

/**
 * A live test that failed after a statement it sent was held back from the account (see
 * {@link LiveAccountStatements}) is skipped instead: it caught the skip as a refusal, and what failed was a check of
 * an answer the account never gave. Any other failure, and every failure of an embedded run, stands as it is.
 */
public final class LiveHeldBackFailures implements TestExecutionExceptionHandler {

    @Override
    public void handleTestExecutionException(final ExtensionContext context, final Throwable throwable)
            throws Throwable {
        final String words = LiveAccountStatements.heldBackInTest();
        if (words != null && LiveSnowflake.enabled() && !(throwable instanceof TestAbortedException)) {
            throw new TestAbortedException(LiveAccountStatements.reason(words), throwable);
        }
        throw throwable;
    }
}
