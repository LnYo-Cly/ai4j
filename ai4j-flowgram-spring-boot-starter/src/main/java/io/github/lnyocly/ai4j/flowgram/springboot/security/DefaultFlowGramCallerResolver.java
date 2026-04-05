package io.github.lnyocly.ai4j.flowgram.springboot.security;

import io.github.lnyocly.ai4j.flowgram.springboot.config.FlowGramProperties;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;

public class DefaultFlowGramCallerResolver implements FlowGramCallerResolver {

    private static final String DEFAULT_TENANT_HEADER = "X-Tenant-Id";

    private final FlowGramProperties properties;

    public DefaultFlowGramCallerResolver(FlowGramProperties properties) {
        this.properties = properties;
    }

    @Override
    public FlowGramCaller resolve(HttpServletRequest request) {
        if (request == null || properties == null || properties.getAuth() == null || !properties.getAuth().isEnabled()) {
            return anonymousCaller();
        }
        String callerId = trimToNull(request.getHeader(properties.getAuth().getHeaderName()));
        String tenantId = trimToNull(request.getHeader(DEFAULT_TENANT_HEADER));
        if (callerId == null) {
            return anonymousCaller();
        }
        return FlowGramCaller.builder()
                .callerId(callerId)
                .tenantId(tenantId)
                .anonymous(false)
                .attributes(Collections.<String, Object>emptyMap())
                .build();
    }

    private FlowGramCaller anonymousCaller() {
        return FlowGramCaller.builder()
                .callerId("anonymous")
                .anonymous(true)
                .attributes(Collections.<String, Object>emptyMap())
                .build();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
