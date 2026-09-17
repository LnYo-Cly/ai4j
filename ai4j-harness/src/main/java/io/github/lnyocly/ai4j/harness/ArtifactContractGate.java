package io.github.lnyocly.ai4j.harness;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reusable completion gate that checks submitted artifacts against an
 * application-declared {@link ArtifactContract}. It verifies, deterministically:
 * expected artifacts exist and parse, required fields are present, no fields
 * outside the declared whitelist appear (which also rejects shadow columns),
 * identifier values occur verbatim in their declared sources, and field values
 * stay inside the declared controlled vocabulary.
 *
 * <p>CSV artifacts are read as a header row plus data rows; JSON artifacts as
 * an object or an array of objects. Anything else fails as unparsable.</p>
 */
public class ArtifactContractGate implements HarnessGate {

    private final String name;
    private final ArtifactContract contract;
    private final ArtifactReader reader;

    public ArtifactContractGate(String name, ArtifactContract contract, ArtifactReader reader) {
        this.name = name == null || name.trim().isEmpty() ? "artifact-contract" : name;
        this.contract = contract;
        this.reader = reader;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public GateResult evaluate(TaskRecord task, SubmissionRecord submission, HarnessState state) {
        if (contract == null || contract.getArtifacts().isEmpty()) {
            return GateResult.pass(name);
        }
        if (reader == null) {
            return GateResult.fail(name, "no ArtifactReader configured");
        }
        List<String> failures = new ArrayList<String>();
        Map<String, String> sourceText = new LinkedHashMap<String, String>();
        Map<String, ParsedArtifact> sourceParsed = new LinkedHashMap<String, ParsedArtifact>();
        for (ArtifactContract.ArtifactSpec spec : contract.getArtifacts()) {
            checkArtifact(spec, submission, state, sourceText, sourceParsed, failures);
        }
        if (!failures.isEmpty()) {
            StringBuilder reason = new StringBuilder();
            for (String failure : failures) {
                if (reason.length() > 0) {
                    reason.append("; ");
                }
                reason.append(failure);
            }
            return GateResult.fail(name, reason.toString());
        }
        return GateResult.pass(name);
    }

    private void checkArtifact(ArtifactContract.ArtifactSpec spec,
                               SubmissionRecord submission,
                               HarnessState state,
                               Map<String, String> sourceText,
                               Map<String, ParsedArtifact> sourceParsed,
                               List<String> failures) {
        String label = spec.getName() == null ? spec.getLocation() : spec.getName();
        String content = resolve(spec, submission, state);
        if (content == null) {
            failures.add(label + ": artifact not found at " + spec.getLocation());
            return;
        }
        ParsedArtifact parsed = parse(content);
        if (parsed == null) {
            failures.add(label + ": content is neither csv nor json");
            return;
        }
        Set<String> required = spec.getRequiredFields();
        for (String field : required) {
            if (!parsed.fields.contains(field)) {
                failures.add(label + ": missing required field '" + field + "'");
            }
        }
        Set<String> allowed = spec.getAllowedFields();
        if (allowed != null) {
            for (String field : parsed.fields) {
                if (!allowed.contains(field)) {
                    failures.add(label + ": undeclared field '" + field + "'");
                }
            }
        }
        for (Map.Entry<String, String> entry : spec.getIdentifierFields().entrySet()) {
            String field = entry.getKey();
            String sourceName = entry.getValue();
            String text = sourceText.get(sourceName);
            if (text == null && !sourceText.containsKey(sourceName)) {
                String location = contract.getSources().get(sourceName);
                text = location == null ? null : reader.read(location);
                sourceText.put(sourceName, text);
            }
            if (text == null) {
                failures.add(label + ": source '" + sourceName + "' unavailable for field '" + field + "'");
                continue;
            }
            for (String value : parsed.columnValues(field)) {
                if (!value.isEmpty() && !text.contains(value)) {
                    failures.add(label + ": identifier '" + value + "' in field '" + field
                            + "' not found verbatim in source '" + sourceName + "'");
                }
            }
        }
        for (Map.Entry<String, String[]> entry : spec.getCoverageFields().entrySet()) {
            String field = entry.getKey();
            String sourceName = entry.getValue()[0];
            String sourceField = entry.getValue()[1];
            ParsedArtifact source = sourceParsed.get(sourceName);
            if (source == null && !sourceParsed.containsKey(sourceName)) {
                String text = sourceText.get(sourceName);
                if (text == null && !sourceText.containsKey(sourceName)) {
                    String location = contract.getSources().get(sourceName);
                    text = location == null ? null : reader.read(location);
                    sourceText.put(sourceName, text);
                }
                source = text == null ? null : parse(text);
                sourceParsed.put(sourceName, source);
            }
            if (source == null) {
                failures.add(label + ": source '" + sourceName
                        + "' unavailable or unparsable for coverage of field '" + field + "'");
                continue;
            }
            Set<String> present = parsed.columnValues(field);
            for (String expected : source.columnValues(sourceField)) {
                if (!expected.isEmpty() && !present.contains(expected)) {
                    failures.add(label + ": missing '" + expected + "' from source '"
                            + sourceName + "' field '" + sourceField + "'");
                }
            }
        }
        for (Map.Entry<String, Set<String>> entry : spec.getControlledVocabulary().entrySet()) {
            String field = entry.getKey();
            Set<String> terms = entry.getValue();
            for (String value : parsed.columnValues(field)) {
                if (!value.isEmpty() && !terms.contains(value)) {
                    failures.add(label + ": value '" + value + "' in field '" + field
                            + "' outside declared vocabulary");
                }
            }
        }
    }

    private String resolve(ArtifactContract.ArtifactSpec spec,
                           SubmissionRecord submission,
                           HarnessState state) {
        String declared = spec.getLocation();
        List<String> candidates = new ArrayList<String>();
        candidates.add(declared);
        if (submission != null && submission.getDeliverables() != null) {
            for (String deliverable : submission.getDeliverables()) {
                if (matches(deliverable, declared)) {
                    candidates.add(deliverable);
                }
            }
        }
        if (state != null && state.getEvidence() != null) {
            for (EvidenceRecord record : state.getEvidence().values()) {
                String location = record == null ? null : record.getLocation();
                if (matches(location, declared)) {
                    candidates.add(location);
                }
            }
        }
        for (String candidate : candidates) {
            String content = candidate == null ? null : reader.read(candidate);
            if (content != null) {
                return content;
            }
        }
        return null;
    }

    private boolean matches(String candidate, String declared) {
        return candidate != null && declared != null
                && (candidate.equals(declared) || candidate.endsWith("/" + declared)
                || candidate.endsWith("\\" + declared));
    }

    private ParsedArtifact parse(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return parseJson(trimmed);
        }
        return parseCsv(content);
    }

    private ParsedArtifact parseJson(String trimmed) {
        Object document;
        try {
            document = JSON.parse(trimmed);
        } catch (RuntimeException error) {
            return null;
        }
        ParsedArtifact parsed = new ParsedArtifact();
        List<JSONObject> rows = new ArrayList<JSONObject>();
        if (document instanceof JSONObject) {
            rows.add((JSONObject) document);
        } else if (document instanceof JSONArray) {
            for (Object element : (JSONArray) document) {
                if (element instanceof JSONObject) {
                    rows.add((JSONObject) element);
                }
            }
        } else {
            return null;
        }
        for (JSONObject row : rows) {
            parsed.fields.addAll(row.keySet());
            parsed.rows.add(row);
        }
        return parsed;
    }

    private ParsedArtifact parseCsv(String content) {
        String[] lines = content.split("\\r?\\n");
        List<List<String>> records = new ArrayList<List<String>>();
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                records.add(csvFields(line));
            }
        }
        if (records.isEmpty()) {
            return null;
        }
        List<String> header = records.get(0);
        ParsedArtifact parsed = new ParsedArtifact();
        parsed.fields.addAll(header);
        for (int i = 1; i < records.size(); i++) {
            List<String> values = records.get(i);
            JSONObject row = new JSONObject(new LinkedHashMap<String, Object>());
            for (int c = 0; c < header.size(); c++) {
                row.put(header.get(c), c < values.size() ? values.get(c) : "");
            }
            parsed.rows.add(row);
        }
        return parsed;
    }

    private List<String> csvFields(String line) {
        List<String> fields = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private static final class ParsedArtifact {
        private final Set<String> fields = new LinkedHashSet<String>();
        private final List<JSONObject> rows = new ArrayList<JSONObject>();

        private Set<String> columnValues(String field) {
            Set<String> values = new LinkedHashSet<String>();
            for (JSONObject row : rows) {
                Object value = row.get(field);
                if (value != null) {
                    values.add(String.valueOf(value).trim());
                }
            }
            return values;
        }
    }
}
