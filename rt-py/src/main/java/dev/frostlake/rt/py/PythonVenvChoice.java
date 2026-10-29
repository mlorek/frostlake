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

/**
 * The venv a JVM's GraalPy interpreters import from (see {@link PythonRuntime}). Each thread keeps one interpreter
 * whichever engine calls it, so the JVM runs one venv. The choice stays open while engines are configured: the last
 * one configured names it. It is fixed by the first Python call that names a venv, or else by the first interpreter
 * built. From then on a call naming another venv is refused. A first call fixes the choice in the same step that
 * checks it, so two engines' first calls on two threads, naming two venvs, cannot both pass: the later one is
 * refused instead of being built with the earlier one's venv. A call naming none takes whatever venv the choice holds.
 */
final class PythonVenvChoice {

    /** The venv, or null for the standard library alone. */
    private String venv;
    /** Whether the venv can no longer change. */
    private boolean fixed;

    /**
     * An engine's setting, as it is configured: the venv from now on, unless the choice is fixed.
     *
     * @param valid the venv the engine names (see {@link PythonRuntime#validVenv}), or null for none
     */
    synchronized void configure(final String valid) {
        if (valid != null && !fixed) {
            venv = valid;
        }
    }

    /**
     * An engine's Python call. One naming a venv fixes the choice to it if it is still open, and is refused if the
     * choice is fixed to another venv, or to none.
     *
     * @param requested the venv the calling engine names (see {@link PythonRuntime#validVenv}), or null for none
     */
    synchronized void require(final String requested) {
        if (!fixed) {
            if (requested != null) {
                venv = requested;
                fixed = true;
            }
            return;
        }
        if (requested != null && !requested.equals(venv)) {
            throw new IllegalStateException("python.venv '" + requested + "' cannot be used: GraalPy runs one venv a "
                + "JVM, and its interpreters here " + (venv == null ? "were started without one"
                    : "import from '" + venv + "'"));
        }
    }

    /**
     * The venv an interpreter is built with, which fixes the choice.
     *
     * @return the venv, or null for none
     */
    synchronized String build() {
        fixed = true;
        return venv;
    }
}
