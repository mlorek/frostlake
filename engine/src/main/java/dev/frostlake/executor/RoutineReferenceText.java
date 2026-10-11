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

import dev.frostlake.executor.commands.DataTypeParser;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlCharStream;
import dev.frostlake.types.DataType;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;

/**
 * A routine named by a text, {@code name(TYPES)}, as GET_DDL, TAG_REFERENCES and SYSTEM$GET_TAG take one. The
 * text is read whole as a routine reference: its name as an identifier reference is, any blank between its
 * tokens, and its types as DROP FUNCTION's are. A text that is not exactly this — no parentheses, something
 * after them, a type nobody knows — names no routine.
 */
public final class RoutineReferenceText {

    private RoutineReferenceText() {
    }

    /**
     * The routine reference a text spells.
     *
     * @param written the text
     * @return the reference, or null when the text is not exactly one
     */
    public static FrostlakeParser.RoutineReferenceContext parse(final String written) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(written));
        lexer.removeErrorListeners();
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.setErrorHandler(new BailErrorStrategy());
        try {
            return parser.routineReference();
        } catch (final ParseCancellationException notAReference) {
            return null;
        }
    }

    /**
     * The types a reference lists, in order.
     *
     * @param list the reference's argument list, or null when the parentheses are empty
     * @return the types, or null when an argument is written with a name or is no type
     */
    public static List<DataType> writtenTypes(final FrostlakeParser.DataTypeListContext list) {
        final List<DataType> types = new ArrayList<DataType>();
        if (list == null) {
            return types;
        }
        for (int i = 0; i < list.getChildCount(); i++) {
            final ParseTree child = list.getChild(i);
            if (child instanceof FrostlakeParser.IdentifierContext) {
                return null;
            }
            if (child instanceof FrostlakeParser.DataTypeNameContext) {
                final ParseTree next = i + 1 < list.getChildCount() ? list.getChild(i + 1) : null;
                types.add(DataTypeParser.parse((FrostlakeParser.DataTypeNameContext) child,
                    next instanceof FrostlakeParser.TypeParametersContext
                        ? (FrostlakeParser.TypeParametersContext) next : null));
            }
        }
        return types;
    }
}
