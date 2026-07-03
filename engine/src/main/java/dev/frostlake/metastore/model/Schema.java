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

package dev.frostlake.metastore.model;

import dev.frostlake.metastore.SqlObject;
import dev.frostlake.types.DataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Schema extends SqlObject {

    private final Map<String, Table> tables;
    private final Map<String, View> views;
    private final Map<String, MaterializedView> materializedViews;
    private final Map<String, DynamicTable> dynamicTables;
    private final Map<String, List<Procedure>> procedures;
    private final Map<String, List<Function>> functions;
    private final Map<String, Stream> streams;
    private final Map<String, Task> tasks;
    private final Map<String, Pipe> pipes;
    private final Map<String, Sequence> sequences;
    private final Map<String, MaskingPolicy> maskingPolicies;
    private final Map<String, RowAccessPolicy> rowAccessPolicies;
    private final Map<String, Tag> tags;
    private final Map<String, Stage> stages;
    private final Map<String, FileFormat> fileFormats;

    public Schema(final String name) {
        super(name);
        this.tables = new ConcurrentHashMap<>();
        this.views = new ConcurrentHashMap<>();
        this.materializedViews = new ConcurrentHashMap<>();
        this.dynamicTables = new ConcurrentHashMap<>();
        this.procedures = new ConcurrentHashMap<>();
        this.functions = new ConcurrentHashMap<>();
        this.streams = new ConcurrentHashMap<>();
        this.tasks = new ConcurrentHashMap<>();
        this.pipes = new ConcurrentHashMap<>();
        this.sequences = new ConcurrentHashMap<>();
        this.maskingPolicies = new ConcurrentHashMap<>();
        this.rowAccessPolicies = new ConcurrentHashMap<>();
        this.tags = new ConcurrentHashMap<>();
        this.stages = new ConcurrentHashMap<>();
        this.fileFormats = new ConcurrentHashMap<>();
    }

    // Tables
    public void addTable(final Table table) {
        // Protect INFORMATION_SCHEMA from modifications
        if ("INFORMATION_SCHEMA".equals(this.getName())) {
            throw new RuntimeException("Cannot create tables in INFORMATION_SCHEMA");
        }
        String upperName = table.getName().toUpperCase();
        if (tables.containsKey(upperName)) {
            throw new RuntimeException("Table already exists: " + table.getName());
        }
        tables.put(upperName, table);
    }

    public void dropTable(final String name) {
        if (!tables.containsKey(name.toUpperCase())) {
            throw new RuntimeException("Table does not exist: " + name);
        }
        tables.remove(name.toUpperCase());
    }

    public Table getTable(final String name) {
        Table table = tables.get(name.toUpperCase());
        if (table == null) {
            throw new RuntimeException("Table does not exist: " + name);
        }
        return table;
    }

    public List<Table> getTables() {
        return new ArrayList<>(tables.values());
    }

    // Views
    public void addView(final View view) {
        // Allow system views to be added during initialization, but protect after that
        String upperName = view.getName().toUpperCase();
        if (views.containsKey(upperName)) {
            throw new RuntimeException("View already exists: " + view.getName());
        }
        views.put(upperName, view);
    }

    public void dropView(final String name) {
        String upperName = name.toUpperCase();
        if (!views.containsKey(upperName)) {
            throw new RuntimeException("View does not exist: " + name);
        }
        // Protect system views from being dropped
        if (isSystemView(upperName)) {
            throw new RuntimeException("Cannot drop system view: " + name);
        }
        views.remove(upperName);
    }

    private boolean isSystemView(final String name) {
        // System views in INFORMATION_SCHEMA
        return "DATABASES".equals(name) || "SCHEMATA".equals(name) ||
               "TABLES".equals(name) || "COLUMNS".equals(name) ||
               "VIEWS".equals(name) || "TABLE_CONSTRAINTS".equals(name) ||
               "INDEXES".equals(name) || "INDEX_COLUMNS".equals(name);
    }

    public View getView(final String name) {
        View view = views.get(name.toUpperCase());
        if (view == null) {
            throw new RuntimeException("View does not exist: " + name);
        }
        return view;
    }

    public List<View> getViews() {
        return new ArrayList<>(views.values());
    }

    // Materialized Views
    public void addMaterializedView(final MaterializedView materializedView) {
        String upperName = materializedView.getName().toUpperCase();
        if (materializedViews.containsKey(upperName)) {
            throw new RuntimeException("Materialized view already exists: " + materializedView.getName());
        }
        materializedViews.put(upperName, materializedView);
    }

    public void dropMaterializedView(final String name) {
        String upperName = name.toUpperCase();
        if (!materializedViews.containsKey(upperName)) {
            throw new RuntimeException("Materialized view does not exist: " + name);
        }
        materializedViews.remove(upperName);
    }

    public MaterializedView getMaterializedView(final String name) {
        MaterializedView mv = materializedViews.get(name.toUpperCase());
        if (mv == null) {
            throw new RuntimeException("Materialized view does not exist: " + name);
        }
        return mv;
    }

    public List<MaterializedView> getMaterializedViews() {
        return new ArrayList<>(materializedViews.values());
    }

    // Dynamic Tables
    public void addDynamicTable(final DynamicTable dt) {
        String upperName = dt.getName().toUpperCase();
        if (dynamicTables.containsKey(upperName)) {
            throw new RuntimeException("Dynamic table already exists: " + dt.getName());
        }
        dynamicTables.put(upperName, dt);
    }

    public void dropDynamicTable(final String name) {
        String upperName = name.toUpperCase();
        if (!dynamicTables.containsKey(upperName)) {
            throw new RuntimeException("Dynamic table does not exist: " + name);
        }
        dynamicTables.remove(upperName);
    }

    public DynamicTable getDynamicTable(final String name) {
        DynamicTable dt = dynamicTables.get(name.toUpperCase());
        if (dt == null) throw new RuntimeException("Dynamic table does not exist: " + name);
        return dt;
    }

    public boolean hasDynamicTable(final String name) {
        return dynamicTables.containsKey(name.toUpperCase());
    }

    public List<DynamicTable> getDynamicTables() {
        return new ArrayList<>(dynamicTables.values());
    }

    // Masking Policies
    public void addMaskingPolicy(final MaskingPolicy policy) {
        maskingPolicies.put(policy.getName().toUpperCase(), policy);
    }
    public void dropMaskingPolicy(final String name) { maskingPolicies.remove(name.toUpperCase()); }
    public MaskingPolicy getMaskingPolicy(final String name) { return maskingPolicies.get(name.toUpperCase()); }
    public boolean hasMaskingPolicy(final String name) { return maskingPolicies.containsKey(name.toUpperCase()); }
    public List<MaskingPolicy> getMaskingPolicies() { return new ArrayList<>(maskingPolicies.values()); }
    public void renameMaskingPolicy(final String oldName, final String newName) {
        final MaskingPolicy policy = maskingPolicies.get(oldName.toUpperCase());
        if (policy == null) {
            throw new RuntimeException("Masking policy not found: " + oldName);
        }
        maskingPolicies.remove(oldName.toUpperCase());
        policy.rename(newName.toUpperCase());
        maskingPolicies.put(newName.toUpperCase(), policy);
    }

    // Row Access Policies
    public void addRowAccessPolicy(final RowAccessPolicy policy) {
        rowAccessPolicies.put(policy.getName().toUpperCase(), policy);
    }
    public void dropRowAccessPolicy(final String name) { rowAccessPolicies.remove(name.toUpperCase()); }
    public RowAccessPolicy getRowAccessPolicy(final String name) { return rowAccessPolicies.get(name.toUpperCase()); }
    public boolean hasRowAccessPolicy(final String name) { return rowAccessPolicies.containsKey(name.toUpperCase()); }
    public List<RowAccessPolicy> getRowAccessPolicies() { return new ArrayList<>(rowAccessPolicies.values()); }
    public void renameRowAccessPolicy(final String oldName, final String newName) {
        final RowAccessPolicy policy = rowAccessPolicies.get(oldName.toUpperCase());
        if (policy == null) {
            throw new RuntimeException("Row access policy not found: " + oldName);
        }
        rowAccessPolicies.remove(oldName.toUpperCase());
        policy.rename(newName.toUpperCase());
        rowAccessPolicies.put(newName.toUpperCase(), policy);
    }

    // Procedures
    public void addProcedure(final Procedure procedure) {
        String upperName = procedure.getName().toUpperCase();
        List<Procedure> overloads = procedures.computeIfAbsent(upperName, (final var k) -> new ArrayList<>());

        // Check for duplicate signature
        for (final Procedure existing : overloads) {
            if (hasSameSignature(existing.getParameters(), procedure.getParameters())) {
                throw new RuntimeException("Procedure with same signature already exists: " + procedure.getName() +
                    " with parameters " + formatParameters(procedure.getParameters()));
            }
        }

        overloads.add(procedure);
    }

    public void dropProcedure(final String name) {
        String upperName = name.toUpperCase();
        if (!procedures.containsKey(upperName)) {
            throw new RuntimeException("Procedure does not exist: " + name);
        }
        procedures.remove(upperName);
    }

    public void dropProcedureBySignature(final String name, final List<DataType> argumentTypes) {
        String upperName = name.toUpperCase();
        List<Procedure> overloads = procedures.get(upperName);
        if (overloads == null || overloads.isEmpty()) {
            throw new RuntimeException("Procedure does not exist: " + name);
        }

        Procedure toRemove = null;
        for (final Procedure proc : overloads) {
            if (matchesSignature(proc.getParameters(), argumentTypes)) {
                toRemove = proc;
                break;
            }
        }

        if (toRemove == null) {
            throw new RuntimeException("Procedure " + name + " with specified parameter types does not exist");
        }

        overloads.remove(toRemove);
        if (overloads.isEmpty()) {
            procedures.remove(upperName);
        }
    }

    public Procedure getProcedure(final String name) {
        String upperName = name.toUpperCase();
        List<Procedure> overloads = procedures.get(upperName);
        if (overloads == null || overloads.isEmpty()) {
            throw new RuntimeException("Procedure does not exist: " + name);
        }

        if (overloads.size() > 1) {
            throw new RuntimeException("Procedure " + name + " has multiple overloads. Use getProcedureBySignature() with parameter types.");
        }

        return overloads.get(0);
    }

    public Procedure getProcedureBySignature(final String name, final List<DataType> argumentTypes) {
        String upperName = name.toUpperCase();
        List<Procedure> overloads = procedures.get(upperName);
        if (overloads == null || overloads.isEmpty()) {
            throw new RuntimeException("Procedure does not exist: " + name);
        }

        for (final Procedure proc : overloads) {
            if (matchesSignature(proc.getParameters(), argumentTypes)) {
                return proc;
            }
        }

        // Check if it's a type mismatch or wrong parameter count
        for (final Procedure proc : overloads) {
            if (proc.getParameters().size() == argumentTypes.size()) {
                throw new RuntimeException("Procedure " + name + " does not have overload with specified parameter type mismatch");
            }
        }

        throw new RuntimeException("Procedure " + name + " does not have overload with " + argumentTypes.size() + " parameters");
    }

    public List<Procedure> getProcedures() {
        List<Procedure> allProcedures = new ArrayList<>();
        for (final List<Procedure> overloads : procedures.values()) {
            allProcedures.addAll(overloads);
        }
        return allProcedures;
    }

    public List<Procedure> getProcedureOverloads(final String name) {
        String upperName = name.toUpperCase();
        List<Procedure> overloads = procedures.get(upperName);
        return overloads != null ? new ArrayList<>(overloads) : new ArrayList<>();
    }

    // Functions
    public void addFunction(final Function function) {
        String upperName = function.getName().toUpperCase();
        List<Function> overloads = functions.computeIfAbsent(upperName, (final var k) -> new ArrayList<>());

        // Check for duplicate signature
        for (final Function existing : overloads) {
            if (hasSameSignature(existing.getParameters(), function.getParameters())) {
                throw new RuntimeException("Function with same signature already exists: " + function.getName() +
                    " with parameters " + formatParameters(function.getParameters()));
            }
        }

        overloads.add(function);
    }

    public void dropFunction(final String name) {
        String upperName = name.toUpperCase();
        if (!functions.containsKey(upperName)) {
            throw new RuntimeException("Function does not exist: " + name);
        }
        functions.remove(upperName);
    }

    public void dropFunctionBySignature(final String name, final List<DataType> argumentTypes) {
        String upperName = name.toUpperCase();
        List<Function> overloads = functions.get(upperName);
        if (overloads == null || overloads.isEmpty()) {
            throw new RuntimeException("Function does not exist: " + name);
        }

        Function toRemove = null;
        for (final Function func : overloads) {
            if (matchesSignature(func.getParameters(), argumentTypes)) {
                toRemove = func;
                break;
            }
        }

        if (toRemove == null) {
            throw new RuntimeException("Function " + name + " with specified parameter types does not exist");
        }

        overloads.remove(toRemove);
        if (overloads.isEmpty()) {
            functions.remove(upperName);
        }
    }

    public Function getFunction(final String name) {
        String upperName = name.toUpperCase();
        List<Function> overloads = functions.get(upperName);
        if (overloads == null || overloads.isEmpty()) {
            throw new RuntimeException("Function does not exist: " + name);
        }

        if (overloads.size() > 1) {
            throw new RuntimeException("Function " + name + " has multiple overloads. Use getFunctionBySignature() with parameter types.");
        }

        return overloads.get(0);
    }

    public Function getFunctionBySignature(final String name, final List<DataType> argumentTypes) {
        String upperName = name.toUpperCase();
        List<Function> overloads = functions.get(upperName);
        if (overloads == null || overloads.isEmpty()) {
            throw new RuntimeException("Function does not exist: " + name);
        }

        for (final Function func : overloads) {
            if (matchesSignature(func.getParameters(), argumentTypes)) {
                return func;
            }
        }

        // Check if it's a type mismatch or wrong parameter count
        for (final Function func : overloads) {
            if (func.getParameters().size() == argumentTypes.size()) {
                throw new RuntimeException("Function " + name + " does not have overload with specified parameter type mismatch");
            }
        }

        throw new RuntimeException("Function " + name + " does not have overload with " + argumentTypes.size() + " parameters");
    }

    public List<Function> getFunctions() {
        List<Function> allFunctions = new ArrayList<>();
        for (final List<Function> overloads : functions.values()) {
            allFunctions.addAll(overloads);
        }
        return allFunctions;
    }

    public List<Function> getFunctionOverloads(final String name) {
        String upperName = name.toUpperCase();
        List<Function> overloads = functions.get(upperName);
        return overloads != null ? new ArrayList<>(overloads) : new ArrayList<>();
    }

    // Helper methods for signature matching
    private boolean hasSameSignature(final List<Parameter> params1, final List<Parameter> params2) {
        if (params1.size() != params2.size()) {
            return false;
        }

        for (int i = 0; i < params1.size(); i++) {
            if (!params1.get(i).getDataType().equals(params2.get(i).getDataType())) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesSignature(final List<Parameter> params, final List<DataType> argumentTypes) {
        if (params.size() != argumentTypes.size()) {
            return false;
        }

        for (int i = 0; i < params.size(); i++) {
            if (!params.get(i).getDataType().equals(argumentTypes.get(i))) {
                return false;
            }
        }

        return true;
    }

    private String formatParameters(final List<Parameter> params) {
        if (params.isEmpty()) {
            return "()";
        }

        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(params.get(i).getDataType());
        }
        sb.append(")");
        return sb.toString();
    }

    // Streams
    public void addStream(final Stream stream) {
        String upperName = stream.getName().toUpperCase();
        if (streams.containsKey(upperName)) {
            throw new RuntimeException("Stream already exists: " + stream.getName());
        }
        streams.put(upperName, stream);
    }

    public void dropStream(final String name) {
        if (!streams.containsKey(name.toUpperCase())) {
            throw new RuntimeException("Stream does not exist: " + name);
        }
        streams.remove(name.toUpperCase());
    }

    public Stream getStream(final String name) {
        Stream stream = streams.get(name.toUpperCase());
        if (stream == null) {
            throw new RuntimeException("Stream does not exist: " + name);
        }
        return stream;
    }

    public List<Stream> getStreams() {
        return new ArrayList<>(streams.values());
    }

    // Tasks
    public void addTask(final Task task) {
        String upperName = task.getName().toUpperCase();
        if (tasks.containsKey(upperName)) {
            throw new RuntimeException("Task already exists: " + task.getName());
        }
        tasks.put(upperName, task);
    }

    public void dropTask(final String name) {
        if (!tasks.containsKey(name.toUpperCase())) {
            throw new RuntimeException("Task does not exist: " + name);
        }
        tasks.remove(name.toUpperCase());
    }

    public Task getTask(final String name) {
        Task task = tasks.get(name.toUpperCase());
        if (task == null) {
            throw new RuntimeException("Task does not exist: " + name);
        }
        return task;
    }

    public List<Task> getTasks() {
        return new ArrayList<>(tasks.values());
    }

    // Pipes
    public void addPipe(final Pipe pipe) {
        String upperName = pipe.getName().toUpperCase();
        if (pipes.containsKey(upperName)) {
            throw new RuntimeException("Pipe already exists: " + pipe.getName());
        }
        pipes.put(upperName, pipe);
    }

    public void dropPipe(final String name) {
        String upperName = name.toUpperCase();
        if (!pipes.containsKey(upperName)) {
            throw new RuntimeException("Pipe does not exist: " + name);
        }
        pipes.remove(upperName);
    }

    public Pipe getPipe(final String name) {
        Pipe pipe = pipes.get(name.toUpperCase());
        if (pipe == null) {
            throw new RuntimeException("Pipe does not exist: " + name);
        }
        return pipe;
    }

    public List<Pipe> getPipes() {
        return new ArrayList<>(pipes.values());
    }

    // Sequences
    public void addSequence(final Sequence sequence) {
        String upperName = sequence.getName().toUpperCase();
        if (sequences.containsKey(upperName)) {
            throw new RuntimeException("Sequence already exists: " + sequence.getName());
        }
        sequences.put(upperName, sequence);
    }

    public void dropSequence(final String name) {
        String upperName = name.toUpperCase();
        if (!sequences.containsKey(upperName)) {
            throw new RuntimeException("Sequence does not exist: " + name);
        }
        sequences.remove(upperName);
    }

    public Sequence getSequence(final String name) {
        Sequence sequence = sequences.get(name.toUpperCase());
        if (sequence == null) {
            throw new RuntimeException("Sequence does not exist: " + name);
        }
        return sequence;
    }

    public List<Sequence> getSequences() {
        return new ArrayList<>(sequences.values());
    }

    // File formats
    public void addFileFormat(final FileFormat fileFormat) {
        fileFormats.put(fileFormat.getName().toUpperCase(), fileFormat);
    }

    public void dropFileFormat(final String name) {
        final String upperName = name.toUpperCase();
        if (!fileFormats.containsKey(upperName)) {
            throw new RuntimeException("File format does not exist: " + name);
        }
        fileFormats.remove(upperName);
    }

    public FileFormat getFileFormat(final String name) {
        return fileFormats.get(name.toUpperCase());
    }

    public boolean hasFileFormat(final String name) {
        return fileFormats.containsKey(name.toUpperCase());
    }

    public List<FileFormat> getFileFormats() {
        return new ArrayList<>(fileFormats.values());
    }

    /**
     * Create a deep clone of this schema
     * Note: Table data cloning must be handled separately at the storage engine level
     */
    public Schema clone() {
        Schema clonedSchema = new Schema(this.getName());
        clonedSchema.setComment(this.getComment());

        // Clone tables (final structure only, final not data)
        for (final Table table : tables.values()) {
            List<TableColumn> clonedColumns = new ArrayList<>();
            for (final TableColumn col : table.getColumns()) {
                TableColumn clonedCol = new TableColumn(
                    col.getName(),
                    col.getDataType(),
                    col.isNullable(),
                    col.getDefaultValue(),
                    col.isPrimaryKey(),
                    col.isUnique(),
                    col.isAutoIncrement()
                );
                clonedCol.setComment(col.getComment());
                clonedCol.setCollation(col.getCollation());
                clonedColumns.add(clonedCol);
            }

            Table clonedTable = new Table(table.getName(), clonedColumns, table.isTemporary(), table.isTransient());
            clonedTable.setComment(table.getComment());
            clonedTable.setClusterKeys(table.getClusterKeys());
            clonedSchema.tables.put(table.getName().toUpperCase(), clonedTable);
        }

        // Clone views
        for (final View view : views.values()) {
            // Skip system views
            if (!isSystemView(view.getName().toUpperCase())) {
                View clonedView = new View(view.getName(), view.getDefinition());
                clonedView.setComment(view.getComment());
                clonedSchema.views.put(view.getName().toUpperCase(), clonedView);
            }
        }

        // Clone procedures
        for (final Map.Entry<String, List<Procedure>> entry : procedures.entrySet()) {
            List<Procedure> clonedOverloads = new ArrayList<>();
            for (final Procedure proc : entry.getValue()) {
                Procedure clonedProc = new Procedure(
                    proc.getName(),
                    proc.getParameters(),
                    proc.getReturnType(),
                    proc.getBody()
                );
                clonedProc.setComment(proc.getComment());
                clonedOverloads.add(clonedProc);
            }
            clonedSchema.procedures.put(entry.getKey(), clonedOverloads);
        }

        // Clone functions
        for (final Map.Entry<String, List<Function>> entry : functions.entrySet()) {
            List<Function> clonedOverloads = new ArrayList<>();
            for (final Function func : entry.getValue()) {
                Function clonedFunc = new Function(
                    func.getName(),
                    func.getParameters(),
                    func.getReturnType(),
                    func.getBody(),
                    func.isTableFunction()
                );
                clonedFunc.setComment(func.getComment());
                clonedOverloads.add(clonedFunc);
            }
            clonedSchema.functions.put(entry.getKey(), clonedOverloads);
        }

        // Clone streams
        for (final Stream stream : streams.values()) {
            Stream clonedStream = new Stream(
                stream.getName(),
                stream.getSourceTableName(),
                stream.getStreamType()
            );
            clonedStream.setComment(stream.getComment());
            clonedSchema.streams.put(stream.getName().toUpperCase(), clonedStream);
        }

        // Clone tasks
        for (final Task task : tasks.values()) {
            Task clonedTask = new Task(
                task.getName(),
                task.getSchedule(),
                task.getScheduleType(),
                task.getSqlStatement(),
                task.getWarehouse()
            );
            clonedTask.setComment(task.getComment());
            clonedTask.setState(task.getState());
            clonedSchema.tasks.put(task.getName().toUpperCase(), clonedTask);
        }

        // Clone pipes
        for (final Pipe pipe : pipes.values()) {
            Pipe clonedPipe = new Pipe(
                pipe.getName(),
                pipe.getCopyStatement(),
                pipe.isAutoIngest(),
                pipe.getNotificationChannel()
            );
            clonedPipe.setComment(pipe.getComment());
            clonedPipe.setPaused(pipe.isPaused());
            if (pipe.getErrorIntegration() != null) {
                clonedPipe.setErrorIntegration(pipe.getErrorIntegration());
            }
            if (pipe.getAwsSnsTopicArn() != null) {
                clonedPipe.setAwsSnsTopicArn(pipe.getAwsSnsTopicArn());
            }
            clonedPipe.setLastLoadedFileCount(pipe.getLastLoadedFileCount());
            if (pipe.getLastLoadedTime() != null) {
                clonedPipe.setLastLoadedTime(pipe.getLastLoadedTime());
            }
            clonedSchema.pipes.put(pipe.getName().toUpperCase(), clonedPipe);
        }

        // Clone sequences
        for (final Sequence sequence : sequences.values()) {
            Sequence clonedSequence = new Sequence(
                sequence.getName(),
                sequence.getStartValue(),
                sequence.getIncrement(),
                sequence.isOrder(),
                sequence.getComment()
            );
            // Preserve current value (use raw to avoid CURRVAL check)
            clonedSequence.setCurrentValue(sequence.getCurrentValueRaw());
            clonedSchema.sequences.put(sequence.getName().toUpperCase(), clonedSequence);
        }

        return clonedSchema;
    }

    // Stages
    public void addStage(final Stage stage) {
        String key = stage.getName().toUpperCase();
        if (stages.containsKey(key)) throw new RuntimeException("Stage already exists: " + stage.getName());
        stages.put(key, stage);
    }

    public void dropStage(final String name) {
        String upper = name.toUpperCase();
        if (!stages.containsKey(upper)) throw new RuntimeException("Stage does not exist: " + name);
        stages.remove(upper);
    }

    public Stage getStage(final String name) {
        Stage s = stages.get(name.toUpperCase());
        if (s == null) throw new RuntimeException("Stage does not exist: " + name);
        return s;
    }

    public boolean hasStage(final String name) {
        return stages.containsKey(name.toUpperCase());
    }

    public List<Stage> getStages() {
        return new ArrayList<>(stages.values());
    }

    // Tags
    public void addTag(final Tag tag) {
        tags.put(tag.getName().toUpperCase(), tag);
    }

    public void dropTag(final String name) {
        String upper = name.toUpperCase();
        if (!tags.containsKey(upper)) throw new RuntimeException("Tag does not exist: " + name);
        tags.remove(upper);
    }

    public Tag getTag(final String name) {
        Tag tag = tags.get(name.toUpperCase());
        if (tag == null) throw new RuntimeException("Tag does not exist: " + name);
        return tag;
    }

    public boolean hasTag(final String name) {
        return tags.containsKey(name.toUpperCase());
    }

    public List<Tag> getTags() {
        return new ArrayList<>(tags.values());
    }

    public void renameTag(final String oldName, final String newName) {
        Tag tag = getTag(oldName);
        tags.remove(oldName.toUpperCase());
        tag.rename(newName);
        tags.put(newName.toUpperCase(), tag);
    }

    @Override
    public String getObjectType() {
        return "SCHEMA";
    }
}
