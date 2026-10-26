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

import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.StringResultWidths;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.HashMap;
import java.util.Map;

/**
 * The RETURN values of a SQL function's block body, judged for their argument types before the body runs. The
 * account compiles the whole body with the statement that calls the function, so a RETURN no call reaches is refused
 * at the call as a reached one is, the first as written (all live-verified):
 *
 * <pre>
 *   BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN RETURN 'a' + TRUE; END        refused at the call: a handler's RETURN
 *   BEGIN IF (FALSE) THEN RETURN 'a' + TRUE; END IF; RETURN 1; END          a branch never taken
 *   BEGIN RETURN 1; RETURN 'a' + TRUE; END                                  a RETURN after the one that ends it
 * </pre>
 *
 * <p>A value is judged over the names declared before it — the parameters, as bound, and the body's typed and
 * inferred declarations in their scopes — with every name written out as a bind. A value that reads a name of
 * no type known here, a FOR counter or a record's field, is left to be judged when it is reached, and so is a
 * refusal that echoes the value while it reads a name, which echoes the name's bound value.
 */
final class FunctionBodyReturnTypes {

    private FunctionBodyReturnTypes() {
    }

    /**
     * Judge every RETURN value of a function's body, in the order written.
     *
     * @param body          the body as parsed
     * @param executor      the procedural executor running the body, its parameters bound
     * @param visitor       reads a declared type
     * @param queryExecutor types the declarations a value is inferred from
     * @throws FunctionBodyCompileError the first value refused
     */
    static void judge(final FrostlakeParser.SqlScriptContext body, final ProceduralExecutor executor,
                      final SQLCommandVisitor visitor, final QueryExecutor queryExecutor) {
        final Map<String, DataType> types = executor.typesForJudging();
        for (final FrostlakeParser.FlowChainContext chain : body.flowChain()) {
            for (final FrostlakeParser.StatementContext statement : chain.statement()) {
                walk(statement, types, executor, visitor, queryExecutor);
            }
        }
    }

    private static void walk(final ParseTree node, final Map<String, DataType> types,
                             final ProceduralExecutor executor, final SQLCommandVisitor visitor,
                             final QueryExecutor queryExecutor) {
        if (node instanceof FrostlakeParser.ReturnStatementContext) {
            judgeValue(((FrostlakeParser.ReturnStatementContext) node).booleanExpr(), types, executor);
            return;
        }
        if (node instanceof FrostlakeParser.DeclarationItemContext) {
            final FrostlakeParser.DeclarationItemContext item = (FrostlakeParser.DeclarationItemContext) node;
            if (item.dataTypeName() != null) {
                declare(types, item.identifier().getText(), item.dataTypeName(), item.typeParameters(), visitor);
            }
            return;
        }
        if (node instanceof FrostlakeParser.UntypedDeclarationItemContext) {
            final FrostlakeParser.UntypedDeclarationItemContext item =
                (FrostlakeParser.UntypedDeclarationItemContext) node;
            infer(types, item.identifier().getText(), item.booleanExpr(), queryExecutor);
            return;
        }
        if (node instanceof FrostlakeParser.LetStatementContext) {
            final FrostlakeParser.LetStatementContext let = (FrostlakeParser.LetStatementContext) node;
            if (let.CURSOR() != null || let.RESULTSET() != null || let.variableName() == null) {
                return;
            }
            if (let.dataTypeName() != null) {
                declare(types, let.variableName().getText(), let.dataTypeName(), let.typeParameters(), visitor);
            } else {
                infer(types, let.variableName().getText(), let.booleanExpr(), queryExecutor);
            }
            return;
        }
        // A block, a branch, a loop's body and a handler each declare for themselves.
        final Map<String, DataType> inner = node instanceof FrostlakeParser.StatementListContext
            || node instanceof FrostlakeParser.BeginEndBlockContext ? new HashMap<String, DataType>(types) : types;
        for (int i = 0; i < node.getChildCount(); i++) {
            walk(node.getChild(i), inner, executor, visitor, queryExecutor);
        }
    }

    private static void judgeValue(final FrostlakeParser.BooleanExprContext value, final Map<String, DataType> types,
                                   final ProceduralExecutor executor) {
        if (value == null) {
            return;
        }
        final int names = namesRead(value, types);
        if (names < 0) {
            return;
        }
        try {
            executor.judgeAsCompiled(value, types);
        } catch (final BlockExpressionTypeError refused) {
            final String message = String.valueOf(refused.getMessage());
            if (names == 0 || message.indexOf('[') < 0) {
                throw new FunctionBodyCompileError(refused);
            }
        }
    }

    /** How many names a value reads, every one of a type known here; -1 when it reads one of no known type. */
    private static int namesRead(final ParseTree node, final Map<String, DataType> types) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameContext name = ((FrostlakeParser.QualifiedNameExprContext) node)
                .qualifiedName();
            return name.nameStartPart() != null && name.namePart().isEmpty()
                && types.containsKey(SqlIdentifiers.canonicalText(name.getText())) ? 1 : -1;
        }
        if (node instanceof FrostlakeParser.BindVarExprContext) {
            final FrostlakeParser.BindVarExprContext bind = (FrostlakeParser.BindVarExprContext) node;
            return bind.variableName() != null
                && types.containsKey(SqlIdentifiers.canonicalText(bind.variableName().getText())) ? 1 : -1;
        }
        int names = 0;
        for (int i = 0; i < node.getChildCount(); i++) {
            final int read = namesRead(node.getChild(i), types);
            if (read < 0) {
                return -1;
            }
            names += read;
        }
        return names;
    }

    private static void declare(final Map<String, DataType> types, final String name,
                                final FrostlakeParser.DataTypeNameContext type,
                                final FrostlakeParser.TypeParametersContext parameters,
                                final SQLCommandVisitor visitor) {
        final String key = SqlIdentifiers.canonicalText(name);
        try {
            final DataType declared = visitor.parseDataType(type, parameters);
            // A text declared without a width is compiled at the full scripting width, a binary at its maximum.
            types.put(key, parameters == null && declared instanceof StringType
                ? new StringType("VARCHAR", StringResultWidths.UNBOUNDED)
                : parameters == null && declared instanceof BinaryType ? BinaryType.AT_MAXIMUM : declared);
        } catch (final RuntimeException unreadable) {
            types.remove(key);
        }
    }

    private static void infer(final Map<String, DataType> types, final String name,
                              final FrostlakeParser.BooleanExprContext initialiser,
                              final QueryExecutor queryExecutor) {
        final String key = SqlIdentifiers.canonicalText(name);
        DataType inferred = null;
        if (initialiser != null && queryExecutor != null) {
            try {
                inferred = DeclarationTypes.ofInitialiser(initialiser, types, queryExecutor);
            } catch (final RuntimeException untyped) {
                inferred = null;
            }
        }
        if (inferred == null || inferred instanceof VariantType) {
            types.remove(key);
        } else {
            types.put(key, inferred);
        }
    }
}
