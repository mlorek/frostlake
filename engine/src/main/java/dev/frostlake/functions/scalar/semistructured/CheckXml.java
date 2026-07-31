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

package dev.frostlake.functions.scalar.semistructured;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * CHECK_XML(text [, disable_auto_convert]) — NULL when the input is NULL, empty, or a valid XML
 * document; otherwise the parse-error message (without the {@code Error parsing XML:} prefix
 * PARSE_XML uses). The optional second argument is accepted for Snowflake compatibility; it cannot
 * affect validity.
 */
public class CheckXml extends BuiltInFunction {

    public CheckXml() { super("CHECK_XML", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        try {
            ParseXml.parseDocument(args.get(0).toString(), true);
            return null;
        } catch (final IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 2; }
}
