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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.PropertyValue;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the {@code KEY = value} properties of an integration or an external volume off the parse tree into
 * {@link PropertyValue}s, keyed by upper-case property name in their written order. A property given twice is
 * refused; which properties an object takes is its handler's judgement.
 */
final class ObjectProperties {

    private ObjectProperties() {
    }

    /**
     * The properties, keyed by upper-case name.
     *
     * @throws RuntimeException for a property given twice
     */
    static Map<String, PropertyValue> read(final List<FrostlakeParser.ObjectPropertyContext> properties) {
        final Map<String, PropertyValue> out = new LinkedHashMap<>();
        for (final FrostlakeParser.ObjectPropertyContext property : properties) {
            final String key = key(property.optionKey());
            if (out.containsKey(key)) {
                throw new RuntimeException(SqlCompilationError.duplicateProperty(key));
            }
            out.put(key, value(property.objectPropertyValue()));
        }
        return out;
    }

    /** A property name, upper-cased. */
    static String key(final FrostlakeParser.OptionKeyContext key) {
        return key.getText().toUpperCase(Locale.ROOT);
    }

    /** One value, remembering how the statement wrote it. */
    static PropertyValue value(final FrostlakeParser.ObjectPropertyValueContext ctx) {
        return parsed(ctx).writtenAs(ctx.getText());
    }

    private static PropertyValue parsed(final FrostlakeParser.ObjectPropertyValueContext ctx) {
        if (ctx.LPAREN() == null) {
            if (!ctx.STRING_LITERAL().isEmpty()) {
                return PropertyValue.text(SqlStringLiterals.decode(ctx.STRING_LITERAL(0).getText()));
            }
            if (ctx.INTEGER_LITERAL() != null) {
                return PropertyValue.number((ctx.MINUS() != null ? "-" : "") + ctx.INTEGER_LITERAL().getText());
            }
            if (ctx.booleanValue() != null) {
                return PropertyValue.bool(ctx.booleanValue().TRUE() != null);
            }
            if (ctx.ALL() != null) {
                return PropertyValue.word("ALL");
            }
            return PropertyValue.word(QualifiedName.join(ParseTreeText.qualifiedNameParts(ctx.qualifiedName())));
        }
        if (!ctx.objectProperty().isEmpty()) {
            return PropertyValue.properties(read(ctx.objectProperty()));
        }
        if (!ctx.objectPropertyValue().isEmpty()) {
            final List<PropertyValue> items = new ArrayList<>();
            for (final FrostlakeParser.ObjectPropertyValueContext item : ctx.objectPropertyValue()) {
                items.add(value(item));
            }
            return PropertyValue.list(items);
        }
        if (!ctx.STRING_LITERAL().isEmpty()) {
            final Map<String, PropertyValue> pairs = new LinkedHashMap<>();
            for (int i = 0; i + 1 < ctx.STRING_LITERAL().size(); i += 2) {
                pairs.put(SqlStringLiterals.decode(ctx.STRING_LITERAL(i).getText()),
                    PropertyValue.text(SqlStringLiterals.decode(ctx.STRING_LITERAL(i + 1).getText())));
            }
            return PropertyValue.pairs(pairs);
        }
        return PropertyValue.list(new ArrayList<PropertyValue>());
    }

    /**
     * A flag property's value.
     *
     * @throws RuntimeException when it is not TRUE or FALSE
     */
    static boolean flag(final String key, final PropertyValue value) {
        final Boolean flag = value.flag();
        if (flag == null) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(value.describe(), key));
        }
        return flag.booleanValue();
    }
}
