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

import java.io.PrintStream;

/**
 * The {@link PrintStream} type {@link UdfConsoleCapture} installs over {@code System.out}/{@code
 * System.err} — a plain auto-flushing stream whose distinct class lets the capture recognize (via
 * {@code instanceof}) whether its wrapper is still installed after a test harness swaps the system
 * streams.
 */
public final class UdfConsolePrintStream extends PrintStream {

    public UdfConsolePrintStream(final UdfConsoleStream target) {
        super(target, true);
    }
}
