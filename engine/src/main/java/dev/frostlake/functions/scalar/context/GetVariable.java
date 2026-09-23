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

package dev.frostlake.functions.scalar.context;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.security.SessionContext;
import dev.frostlake.types.WidthlessStringType;

import java.util.List;
import java.util.Map;

/**
 * {@code GETVARIABLE(<name>)} — the session variable of that name, as TEXT.
 *
 * <p>The name is matched EXACTLY, where {@code SET} stores it upper-cased, so {@code GETVARIABLE('SV')}
 * reads the variable {@code SET sv = 5} defined and {@code GETVARIABLE('sv')} and
 * {@code GETVARIABLE('"SV"')} read nothing. A name no SET defined, and a NULL name, answer NULL rather
 * than refusing — where {@code $sv} refuses an unset variable outright. The value comes back as text
 * whatever it was set to, so a numeric variable reads {@code '5'} and {@code GETVARIABLE('SV') + 1} is 6
 * (all live-verified).
 *
 * <p>The result is a string of no width — {@code SYSTEM$TYPEOF} spells it the bare word VARCHAR — and the
 * call is not a CONSTANT: an argument slot that folds its argument before the statement runs refuses it
 * (see {@code ConstantArgumentWalk}). Its own argument must be constant TEXT, and a name that is not is
 * refused while the statement compiles.
 */
public class GetVariable extends BuiltInFunction {

    private final SessionContext sessionContext;

    /**
     * @param sessionContext the session whose variables this reads, or null outside one
     */
    public GetVariable(final SessionContext sessionContext) {
        super("GETVARIABLE", WidthlessStringType.WIDTHLESS);
        this.sessionContext = sessionContext;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object name = args.isEmpty() ? null : args.get(0);
        if (name == null || sessionContext == null) {
            return null;
        }
        final Map<String, Object> variables = sessionContext.getAllSessionVariables();
        if (!variables.containsKey(name.toString())) {
            return null;
        }
        final Object value = variables.get(name.toString());
        return value == null ? null : String.valueOf(value);
    }

    @Override
    public int getMinArgCount() {
        return 1;
    }

    @Override
    public int getMaxArgCount() {
        return 1;
    }
}
