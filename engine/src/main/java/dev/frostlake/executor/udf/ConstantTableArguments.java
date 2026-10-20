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

/**
 * Which arguments of the handler table function call that runs on this thread are constants of the statement — a
 * literal, a cast of one, an operation over constants — as the call's planner judged them. A runtime that tells its
 * handler about its arguments reads it: a JavaScript handler's {@code argumentInfo} marks a constant argument
 * {@code isConst} and gives its {@code constValue}.
 */
public final class ConstantTableArguments {

    private static final ThreadLocal<boolean[]> CONSTANT = new ThreadLocal<boolean[]>();

    private ConstantTableArguments() {
    }

    /**
     * A call starts running its partitions.
     *
     * @param constant for each parameter, whether its argument is a constant
     * @return what the thread held before, to hand to {@link #exit}
     */
    public static boolean[] enter(final boolean[] constant) {
        final boolean[] outer = CONSTANT.get();
        CONSTANT.set(constant);
        return outer;
    }

    /**
     * The call {@link #enter} was told of has run.
     *
     * @param outer what {@link #enter} answered
     */
    public static void exit(final boolean[] outer) {
        if (outer == null) {
            CONSTANT.remove();
        } else {
            CONSTANT.set(outer);
        }
    }

    /**
     * Whether the running call's argument for a parameter is a constant.
     *
     * @param parameter the parameter's place, from 0
     * @return whether it is; false when no call runs
     */
    public static boolean isConstant(final int parameter) {
        final boolean[] constant = CONSTANT.get();
        return constant != null && parameter >= 0 && parameter < constant.length && constant[parameter];
    }
}
