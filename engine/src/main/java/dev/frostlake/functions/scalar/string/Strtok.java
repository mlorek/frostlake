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

package dev.frostlake.functions.scalar.string;

import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Strtok extends TextArgumentFunction {
    public Strtok() { super("STRTOK", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final String str = args.get(0).toString();
        final String delimiters = args.size() > 1 && args.get(1) != null ? args.get(1).toString() : " ";
        final int partNum = args.size() > 2 && args.get(2) != null ? ((Number) args.get(2)).intValue() : 1;
        final String regex = "[" + Pattern.quote(delimiters) + "]+";
        final String[] tokens = str.split(regex, -1);
        final List<String> nonEmpty = new ArrayList<>();
        for (final String t : tokens) { if (!t.isEmpty()) nonEmpty.add(t); }
        if (partNum < 1 || partNum > nonEmpty.size()) return null;
        return nonEmpty.get(partNum - 1);
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 3; }
}
