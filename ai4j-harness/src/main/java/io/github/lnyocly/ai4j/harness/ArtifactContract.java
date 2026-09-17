package io.github.lnyocly.ai4j.harness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Application-declared contract for the artifacts a submission must produce.
 * The contract is domain-owned: it names deliverables and states which fields,
 * identifier values, and vocabulary they must preserve from declared sources.
 * The kernel only sees an opaque declaration; {@link ArtifactContractGate}
 * performs the deterministic checks.
 */
public final class ArtifactContract {

    private final Map<String, String> sources;
    private final List<ArtifactSpec> artifacts;

    private ArtifactContract(Map<String, String> sources, List<ArtifactSpec> artifacts) {
        this.sources = Collections.unmodifiableMap(new LinkedHashMap<String, String>(sources));
        this.artifacts = Collections.unmodifiableList(new ArrayList<ArtifactSpec>(artifacts));
    }

    /** Named source documents, keyed for reference from identifier fields. */
    public Map<String, String> getSources() {
        return sources;
    }

    public List<ArtifactSpec> getArtifacts() {
        return artifacts;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Contract for one expected artifact. */
    public static final class ArtifactSpec {
        private final String name;
        private final String location;
        private final Set<String> requiredFields;
        private final Set<String> allowedFields;
        private final Map<String, String> identifierFields;
        private final Map<String, String[]> coverageFields;
        private final Map<String, Set<String>> controlledVocabulary;

        private ArtifactSpec(Builder builder) {
            this.name = builder.name;
            this.location = builder.location;
            this.requiredFields = Collections.unmodifiableSet(new LinkedHashSet<String>(builder.requiredFields));
            this.allowedFields = builder.allowedFields == null
                    ? null
                    : Collections.unmodifiableSet(new LinkedHashSet<String>(builder.allowedFields));
            this.identifierFields = Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.identifierFields));
            this.coverageFields = Collections.unmodifiableMap(new LinkedHashMap<String, String[]>(builder.coverageFields));
            Map<String, Set<String>> vocabulary = new LinkedHashMap<String, Set<String>>();
            for (Map.Entry<String, Set<String>> entry : builder.controlledVocabulary.entrySet()) {
                vocabulary.put(entry.getKey(),
                        Collections.unmodifiableSet(new LinkedHashSet<String>(entry.getValue())));
            }
            this.controlledVocabulary = Collections.unmodifiableMap(vocabulary);
        }

        public String getName() {
            return name;
        }

        /** Location used both for deliverable matching and reader resolution. */
        public String getLocation() {
            return location;
        }

        /** Fields that must be present. */
        public Set<String> getRequiredFields() {
            return requiredFields;
        }

        /** Whitelist of permitted fields; null means unconstrained. Extra fields fail. */
        public Set<String> getAllowedFields() {
            return allowedFields;
        }

        /** Field whose every value must appear verbatim in the named source text. */
        public Map<String, String> getIdentifierFields() {
            return identifierFields;
        }

        /**
         * Field that must contain every value found in {@code sourceField} of the
         * named source. Entry maps artifact field to {@code [sourceName, sourceField]}.
         */
        public Map<String, String[]> getCoverageFields() {
            return coverageFields;
        }

        /** Field whose every value must belong to the declared term set. */
        public Map<String, Set<String>> getControlledVocabulary() {
            return controlledVocabulary;
        }

        public static Builder builder(String name, String location) {
            return new Builder(name, location);
        }

        public static final class Builder {
            private final String name;
            private final String location;
            private final Set<String> requiredFields = new LinkedHashSet<String>();
            private Set<String> allowedFields;
            private final Map<String, String> identifierFields = new LinkedHashMap<String, String>();
            private final Map<String, String[]> coverageFields = new LinkedHashMap<String, String[]>();
            private final Map<String, Set<String>> controlledVocabulary = new LinkedHashMap<String, Set<String>>();

            private Builder(String name, String location) {
                this.name = name;
                this.location = location;
            }

            public Builder requiredFields(String... fields) {
                Collections.addAll(requiredFields, fields);
                return this;
            }

            /** Declares the exact permitted field set; undeclared fields fail the gate. */
            public Builder allowedFields(String... fields) {
                allowedFields = new LinkedHashSet<String>();
                Collections.addAll(allowedFields, fields);
                return this;
            }

            /** Every value of {@code field} must occur verbatim in source {@code sourceName}. */
            public Builder identifierField(String field, String sourceName) {
                identifierFields.put(field, sourceName);
                return this;
            }

            /**
             * Every value of {@code sourceField} in source {@code sourceName} must
             * appear in this artifact's {@code field}. The source must be csv or
             * json so its rows can be read back.
             */
            public Builder coverageField(String field, String sourceName, String sourceField) {
                coverageFields.put(field, new String[]{sourceName, sourceField});
                return this;
            }

            /** Every value of {@code field} must be one of {@code terms}. */
            public Builder controlledVocabulary(String field, String... terms) {
                Set<String> set = new LinkedHashSet<String>();
                Collections.addAll(set, terms);
                controlledVocabulary.put(field, set);
                return this;
            }

            public ArtifactSpec build() {
                return new ArtifactSpec(this);
            }
        }
    }

    public static final class Builder {
        private final Map<String, String> sources = new LinkedHashMap<String, String>();
        private final List<ArtifactSpec> artifacts = new ArrayList<ArtifactSpec>();

        /** Registers a named source document at {@code location}. */
        public Builder source(String sourceName, String location) {
            if (sourceName != null && location != null) {
                sources.put(sourceName, location);
            }
            return this;
        }

        public Builder artifact(ArtifactSpec spec) {
            if (spec != null) {
                artifacts.add(spec);
            }
            return this;
        }

        public ArtifactContract build() {
            return new ArtifactContract(sources, artifacts);
        }
    }
}
