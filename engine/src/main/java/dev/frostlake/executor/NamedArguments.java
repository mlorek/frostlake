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

import dev.frostlake.metastore.model.Parameter;

import java.util.ArrayList;
import java.util.List;

/**
 * The names a call writes its arguments under — {@code f(a => 1, b => 2)}, {@code CALL p(x => 1)} — judged
 * against a routine's parameters as a real account judges them (live-verified).
 */
public final class NamedArguments {

    private NamedArguments() {
    }

    /**
     * The names as a refusal lists them: each once, an unquoted name upper-cased and a quoted one as its quotes
     * hold it. The names of parameters come first, in the order the routine declares them — a quoted name of
     * another case stands where its parameter does — and the names that are no parameter's follow as written:
     * {@code p(zeta, alpha)} called with {@code alpha => 5, zeta => 5} lists {@code [ZETA, ALPHA]}, and
     * {@code f(a, b)} called with {@code "a" => 1, b => 2} lists {@code [a, B]}.
     *
     * @param params  the routine's parameters, in declaration order
     * @param written the names as written, one per argument, null for a positional one
     * @return the names, joined by a comma and a space
     */
    public static String listed(final List<Parameter> params, final List<String> written) {
        final List<String> names = new ArrayList<>();
        for (final Parameter param : params) {
            for (final String name : written) {
                if (name != null && SqlIdentifiers.canonicalText(name).equalsIgnoreCase(param.getName())
                        && !names.contains(SqlIdentifiers.canonicalText(name))) {
                    names.add(SqlIdentifiers.canonicalText(name));
                }
            }
        }
        for (final String name : written) {
            if (name != null && !names.contains(SqlIdentifiers.canonicalText(name))) {
                names.add(SqlIdentifiers.canonicalText(name));
            }
        }
        return String.join(", ", names);
    }

    /**
     * Whether a parameter list takes a call's arguments by their names: the positional ones fill the leading
     * parameters, each name is a parameter not yet filled — matched exactly, so a quoted name of another case is
     * none — and no parameter without a default is left out.
     *
     * @param params  the routine's parameters, in declaration order
     * @param written the names as written, one per argument, null for a positional one
     * @return whether the routine takes the call
     */
    public static boolean fit(final List<Parameter> params, final List<String> written) {
        final boolean[] filled = new boolean[params.size()];
        for (int i = 0; i < written.size(); i++) {
            if (written.get(i) == null) {
                if (i >= params.size()) {
                    return false;
                }
                filled[i] = true;
                continue;
            }
            final String name = SqlIdentifiers.canonicalText(written.get(i));
            int target = -1;
            for (int p = 0; p < params.size() && target < 0; p++) {
                if (!filled[p] && params.get(p).getName().equals(name)) {
                    target = p;
                }
            }
            if (target < 0) {
                return false;
            }
            filled[target] = true;
        }
        for (int p = 0; p < params.size(); p++) {
            if (!filled[p] && !params.get(p).hasDefault()) {
                return false;
            }
        }
        return true;
    }
}
