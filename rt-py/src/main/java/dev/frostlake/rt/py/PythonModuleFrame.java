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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

/**
 * One routine call's hold on the module namespace of this thread's interpreter (see
 * {@link PythonRuntime#enterModule()}): the interpreter it was entered on, and what the namespace held before, which
 * leaving it puts back.
 */
final class PythonModuleFrame {

    /** The interpreter the frame was entered on. */
    private final Context context;

    /** What the namespace held before the frame was entered. */
    private final Value saved;

    /** Whether the frame holds the namespace a call site kept (see PythonRuntime#resumeModule). */
    private final boolean resumed;

    /**
     * @param context the interpreter the frame was entered on
     * @param saved   what the namespace held before the frame was entered
     */
    PythonModuleFrame(final Context context, final Value saved) {
        this(context, saved, false);
    }

    /**
     * @param context the interpreter the frame was entered on
     * @param saved   what the namespace held before the frame was entered
     * @param resumed whether the frame holds the namespace a call site kept
     */
    PythonModuleFrame(final Context context, final Value saved, final boolean resumed) {
        this.context = context;
        this.saved = saved;
        this.resumed = resumed;
    }

    /** The interpreter the frame was entered on. */
    Context getContext() {
        return context;
    }

    /** What the namespace held before the frame was entered. */
    Value getSaved() {
        return saved;
    }

    /** Whether the frame holds the namespace a call site kept. */
    boolean isResumed() {
        return resumed;
    }
}
