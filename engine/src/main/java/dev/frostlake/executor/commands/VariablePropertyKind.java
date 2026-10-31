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

/** How a property judges the session variable a statement gives it as its value (see {@link PropertyVariables}). */
enum VariablePropertyKind {

    /** A COMMENT: a text, and no comment for NULL or a Boolean; a number is no type of it. */
    COMMENT,

    /** An alert's SCHEDULE: a text; a number is no type of it. */
    TEXT,

    /** A task's SCHEDULE: a text, and no schedule for NULL or a Boolean; a number is no type of it. */
    SCHEDULE,

    /** A table's or a view's switch, CHANGE_TRACKING among them, which no variable is a value of. */
    SWITCH,

    /** A task's switch, ALLOW_OVERLAPPING_EXECUTION, which no variable is a value of. */
    TASK_SWITCH,

    /** A parameter, judged by the type the account lists for it. */
    PARAMETER,

    /** A file format's option, or one of a stage's format or copy options: a text alone. */
    OPTION,

    /** The name of a stage's file format: a text, a number, a date or a Boolean read as a name. */
    NAME,

    /** A stage's encryption TYPE: a text or a number read as the type's text. */
    ENCRYPTION_TYPE,

    /** A stage's DIRECTORY switch ENABLE, which no variable is a value of. */
    DIRECTORY_SWITCH,

    /**
     * A password or a session policy's property other than its COMMENT, or a list given no list, which no variable is
     * a type of.
     */
    POLICY_PROPERTY,

    /** One of a network rule's or a secret's choices: a text, read as the choice's word; a number is no type of it. */
    CHOICE,

    /** An item of a security object's list: a text alone. */
    LIST_ITEM
}
