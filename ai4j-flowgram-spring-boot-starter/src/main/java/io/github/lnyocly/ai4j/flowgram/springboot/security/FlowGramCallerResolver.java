package io.github.lnyocly.ai4j.flowgram.springboot.security;

import javax.servlet.http.HttpServletRequest;

public interface FlowGramCallerResolver {

    FlowGramCaller resolve(HttpServletRequest request);
}
