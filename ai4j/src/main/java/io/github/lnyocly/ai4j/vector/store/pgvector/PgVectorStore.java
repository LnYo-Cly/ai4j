package io.github.lnyocly.ai4j.vector.store.pgvector;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import io.github.lnyocly.ai4j.config.PgVectorConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.vector.store.VectorDeleteRequest;
import io.github.lnyocly.ai4j.vector.store.VectorRecord;
import io.github.lnyocly.ai4j.vector.store.VectorSearchRequest;
import io.github.lnyocly.ai4j.vector.store.VectorSearchResult;
import io.github.lnyocly.ai4j.vector.store.VectorStore;
import io.github.lnyocly.ai4j.vector.store.VectorStoreCapabilities;
import io.github.lnyocly.ai4j.vector.store.VectorUpsertRequest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PgVectorStore implements VectorStore {

    private final PgVectorConfig config;

    public PgVectorStore(Configuration configuration) {
        this(configuration == null ? null : configuration.getPgVectorConfig());
    }

    public PgVectorStore(PgVectorConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("pgVectorConfig is required");
        }
        this.config = config;
    }

    @Override
    public int upsert(VectorUpsertRequest request) throws Exception {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        List<VectorRecord> records = request == null || request.getRecords() == null
                ? Collections.<VectorRecord>emptyList()
                : request.getRecords();
        if (records.isEmpty()) {
            return 0;
        }

        String sql = "insert into " + identifier(config.getTableName()) + " (" +
                identifier(config.getIdColumn()) + ", " +
                identifier(config.getDatasetColumn()) + ", " +
                identifier(config.getContentColumn()) + ", " +
                identifier(config.getMetadataColumn()) + ", " +
                identifier(config.getVectorColumn()) +
                ") values (?, ?, ?, cast(? as jsonb), cast(? as vector)) " +
                "on conflict (" + identifier(config.getIdColumn()) + ") do update set " +
                identifier(config.getDatasetColumn()) + " = excluded." + identifier(config.getDatasetColumn()) + ", " +
                identifier(config.getContentColumn()) + " = excluded." + identifier(config.getContentColumn()) + ", " +
                identifier(config.getMetadataColumn()) + " = excluded." + identifier(config.getMetadataColumn()) + ", " +
                identifier(config.getVectorColumn()) + " = excluded." + identifier(config.getVectorColumn());

        int total = 0;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 0;
            for (VectorRecord record : records) {
                if (record == null || record.getVector() == null || record.getVector().isEmpty()) {
                    index++;
                    continue;
                }
                statement.setString(1, resolveId(record.getId(), index));
                statement.setString(2, dataset);
                statement.setString(3, record.getContent());
                String metadataJson = metadataJson(record);
                if (metadataJson == null) {
                    statement.setNull(4, Types.VARCHAR);
                } else {
                    statement.setString(4, metadataJson);
                }
                statement.setString(5, vectorLiteral(record.getVector()));
                statement.addBatch();
                index++;
            }
            int[] results = statement.executeBatch();
            for (int value : results) {
                total += value < 0 ? 1 : value;
            }
        }
        return total;
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) throws Exception {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        if (request == null || request.getVector() == null || request.getVector().isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder sql = new StringBuilder();
        sql.append("select ")
                .append(identifier(config.getIdColumn())).append(", ")
                .append(identifier(config.getContentColumn())).append(", ")
                .append(identifier(config.getMetadataColumn())).append("::text as metadata_json, ")
                .append(identifier(config.getVectorColumn())).append(" ").append(config.getDistanceOperator()).append(" cast(? as vector) as distance ")
                .append("from ").append(identifier(config.getTableName()))
                .append(" where ").append(identifier(config.getDatasetColumn())).append(" = ?");

        List<Object> parameters = new ArrayList<Object>();
        parameters.add(vectorLiteral(request.getVector()));
        parameters.add(dataset);
        appendMetadataFilters(sql, parameters, request.getFilter());
        sql.append(" order by ").append(identifier(config.getVectorColumn()))
                .append(" ").append(config.getDistanceOperator()).append(" cast(? as vector)")
                .append(" limit ?");
        parameters.add(vectorLiteral(request.getVector()));
        parameters.add(request.getTopK() == null || request.getTopK() <= 0 ? 10 : request.getTopK());

        List<VectorSearchResult> results = new ArrayList<VectorSearchResult>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParameters(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String metadataJson = resultSet.getString("metadata_json");
                    Map<String, Object> metadata = parseMetadata(metadataJson);
                    results.add(VectorSearchResult.builder()
                            .id(resultSet.getString(identifier(config.getIdColumn())))
                            .content(firstNonBlank(
                                    resultSet.getString(identifier(config.getContentColumn())),
                                    stringValue(metadata.get(Constants.METADATA_KEY))))
                            .metadata(metadata)
                            .score(1.0f - resultSet.getFloat("distance"))
                            .build());
                }
            }
        }
        return results;
    }

    @Override
    public boolean delete(VectorDeleteRequest request) throws Exception {
        String dataset = requiredDataset(request == null ? null : request.getDataset());
        if (request == null) {
            return false;
        }

        StringBuilder sql = new StringBuilder();
        sql.append("delete from ").append(identifier(config.getTableName()))
                .append(" where ").append(identifier(config.getDatasetColumn())).append(" = ?");
        List<Object> parameters = new ArrayList<Object>();
        parameters.add(dataset);

        if (request.getIds() != null && !request.getIds().isEmpty()) {
            sql.append(" and ").append(identifier(config.getIdColumn())).append(" in (");
            for (int i = 0; i < request.getIds().size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append("?");
                parameters.add(request.getIds().get(i));
            }
            sql.append(")");
        } else if (request.getFilter() != null && !request.getFilter().isEmpty()) {
            appendMetadataFilters(sql, parameters, request.getFilter());
        }

        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParameters(statement, parameters);
            statement.executeUpdate();
            return true;
        }
    }

    @Override
    public VectorStoreCapabilities capabilities() {
        return VectorStoreCapabilities.builder()
                .dataset(true)
                .metadataFilter(true)
                .deleteByFilter(true)
                .returnStoredVector(false)
                .build();
    }

    private void appendMetadataFilters(StringBuilder sql, List<Object> parameters, Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            if (entry == null || safeMetadataKey(entry.getKey()) == null || entry.getValue() == null) {
                continue;
            }
            String key = safeMetadataKey(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Iterable) {
                List<Object> values = new ArrayList<Object>();
                for (Object item : (Iterable<?>) value) {
                    values.add(item);
                }
                if (values.isEmpty()) {
                    continue;
                }
                sql.append(" and ").append(identifier(config.getMetadataColumn())).append(" ->> '").append(key).append("' in (");
                for (int i = 0; i < values.size(); i++) {
                    if (i > 0) {
                        sql.append(", ");
                    }
                    sql.append("?");
                    parameters.add(String.valueOf(values.get(i)));
                }
                sql.append(")");
            } else {
                sql.append(" and ").append(identifier(config.getMetadataColumn())).append(" ->> '").append(key).append("' = ?");
                parameters.add(String.valueOf(value));
            }
        }
    }

    private Connection connection() throws Exception {
        if (trimToNull(config.getUsername()) == null) {
            return DriverManager.getConnection(config.getJdbcUrl());
        }
        return DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword());
    }

    private void bindParameters(PreparedStatement statement, List<Object> parameters) throws Exception {
        for (int i = 0; i < parameters.size(); i++) {
            Object value = parameters.get(i);
            if (value == null) {
                statement.setNull(i + 1, Types.VARCHAR);
            } else {
                statement.setObject(i + 1, value);
            }
        }
    }

    private String metadataJson(VectorRecord record) {
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        if (record.getMetadata() != null) {
            metadata.putAll(record.getMetadata());
        }
        if (trimToNull(record.getContent()) != null && !metadata.containsKey(Constants.METADATA_KEY)) {
            metadata.put(Constants.METADATA_KEY, record.getContent().trim());
        }
        return metadata.isEmpty() ? null : JSON.toJSONString(metadata);
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        return JSON.parseObject(metadataJson, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    private String vectorLiteral(List<Float> vector) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(vector.get(i));
        }
        builder.append("]");
        return builder.toString();
    }

    private String safeMetadataKey(String key) {
        String value = trimToNull(key);
        if (value == null) {
            return null;
        }
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid metadata key: " + key);
        }
        return value;
    }

    private String identifier(String identifier) {
        String value = trimToNull(identifier);
        if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?")) {
            throw new IllegalArgumentException("Invalid sql identifier: " + identifier);
        }
        return value;
    }

    private String requiredDataset(String dataset) {
        String value = trimToNull(dataset);
        if (value == null) {
            throw new IllegalArgumentException("dataset is required");
        }
        return value;
    }

    private String resolveId(String id, int index) {
        String value = trimToNull(id);
        return value == null ? "id_" + index : value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
