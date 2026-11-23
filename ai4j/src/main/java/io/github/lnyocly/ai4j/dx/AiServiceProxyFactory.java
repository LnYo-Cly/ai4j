package io.github.lnyocly.ai4j.dx;

import com.fasterxml.jackson.databind.JavaType;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.stream.AiReply;
import io.github.lnyocly.ai4j.stream.AiStream;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * JDK 8 dynamic proxy factory for declarative {@link AiService} interfaces.
 *
 * <p>Uses {@link Proxy#newProxyInstance} with zero bytecode generation, keeping
 * Java 8 and GraalVM / AOT compatibility.</p>
 */
public final class AiServiceProxyFactory {

    private AiServiceProxyFactory() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T create(Class<T> serviceInterface, AiClient aiClient, Object... toolBeans) {
        Objects.requireNonNull(serviceInterface, "serviceInterface is required");
        Objects.requireNonNull(aiClient, "aiClient is required");
        if (!serviceInterface.isInterface()) {
            throw new IllegalArgumentException("AiService target must be an interface: " + serviceInterface.getName());
        }
        AiService serviceAnn = serviceInterface.getAnnotation(AiService.class);
        InvocationHandler handler = new ServiceInvocationHandler(serviceInterface, serviceAnn, aiClient, toolBeans);
        return (T) Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class<?>[]{serviceInterface},
                handler);
    }

    private static final class ServiceInvocationHandler implements InvocationHandler {

        private final Class<?> serviceInterface;
        private final AiService serviceAnn;
        private final AiClient aiClient;
        private final Object[] toolBeans;

        private ServiceInvocationHandler(Class<?> serviceInterface, AiService serviceAnn,
                                         AiClient aiClient, Object[] toolBeans) {
            this.serviceInterface = serviceInterface;
            this.serviceAnn = serviceAnn;
            this.aiClient = aiClient;
            this.toolBeans = toolBeans == null ? new Object[0] : toolBeans;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }
            AiPromptSpec spec = buildPromptSpec(method, args);
            Type genericReturn = method.getGenericReturnType();
            Class<?> rawReturn = method.getReturnType();

            if (AiStream.class.isAssignableFrom(rawReturn)) {
                JavaType payloadType = extractTypeArg(genericReturn, 0);
                if (payloadType == null || payloadType.getRawClass() == String.class
                        || payloadType.getRawClass() == Void.class) {
                    return spec.stream();
                }
                return spec.stream(payloadType);
            }
            if (AiReply.class.isAssignableFrom(rawReturn)) {
                JavaType payloadType = extractTypeArg(genericReturn, 0);
                if (payloadType == null || payloadType.getRawClass() == String.class
                        || payloadType.getRawClass() == Void.class) {
                    return spec.call();
                }
                return spec.call(payloadType);
            }
            if (rawReturn == String.class) {
                return spec.call().getText();
            }
            if (rawReturn == void.class || rawReturn == Void.class) {
                spec.call();
                return null;
            }
            JavaType targetType = JsonCodec.protocolMapper().getTypeFactory().constructType(genericReturn);
            return spec.call(targetType).getData();
        }

        private AiPromptSpec buildPromptSpec(Method method, Object[] args) {
            String modelSpec = serviceAnn != null && !serviceAnn.model().trim().isEmpty()
                    ? serviceAnn.model().trim() : null;
            AiPromptSpec spec = modelSpec != null ? aiClient.prompt(modelSpec) : aiClient.prompt();
            if (serviceAnn != null && serviceAnn.maxToolRounds() > 0) {
                spec.maxToolRounds(serviceAnn.maxToolRounds());
            }
            if (toolBeans.length > 0) {
                spec.tools(toolBeans);
            }

            String system = resolveSystemMessage(method);
            if (system != null && !system.trim().isEmpty()) {
                spec.system(system);
            }

            String memorySessionParam = serviceAnn == null ? "" : serviceAnn.memorySessionParam().trim();
            Parameter[] params = method.getParameters();
            Object[] safeArgs = args == null ? new Object[0] : args;
            Map<String, Object> variables = new LinkedHashMap<String, Object>();
            String explicitUserParamValue = null;
            String singleCandidateUserText = null;
            int nonSessionParamCount = 0;

            for (int i = 0; i < params.length && i < safeArgs.length; i++) {
                Parameter p = params[i];
                Object value = safeArgs[i];
                if (p.isAnnotationPresent(MemoryId.class)
                        || (!memorySessionParam.isEmpty() && memorySessionParam.equals(p.getName()))) {
                    if (value != null) {
                        spec.sessionId(String.valueOf(value));
                    }
                    continue;
                }
                nonSessionParamCount++;
                if (p.isAnnotationPresent(UserMessage.class)) {
                    explicitUserParamValue = value == null ? "" : String.valueOf(value);
                }
                AiToolParam paramAnn = p.getAnnotation(AiToolParam.class);
                String varName = (paramAnn != null && !paramAnn.name().trim().isEmpty())
                        ? paramAnn.name().trim() : p.getName();
                variables.put(varName, value);
                if (nonSessionParamCount == 1) {
                    singleCandidateUserText = value == null ? "" : String.valueOf(value);
                }
            }

            UserMessage methodUser = method.getAnnotation(UserMessage.class);
            if (methodUser != null && !methodUser.value().trim().isEmpty()) {
                spec.user(methodUser.value(), variables);
            } else if (explicitUserParamValue != null) {
                spec.user(explicitUserParamValue);
            } else if (singleCandidateUserText != null && nonSessionParamCount == 1) {
                spec.user(singleCandidateUserText);
            } else if (!variables.isEmpty()) {
                spec.user(JsonCodec.toJson(variables));
            }
            return spec;
        }

        private String resolveSystemMessage(Method method) {
            SystemMessage methodSys = method.getAnnotation(SystemMessage.class);
            if (methodSys != null && !methodSys.value().trim().isEmpty()) {
                return methodSys.value();
            }
            SystemMessage typeSys = serviceInterface.getAnnotation(SystemMessage.class);
            if (typeSys != null && !typeSys.value().trim().isEmpty()) {
                return typeSys.value();
            }
            if (serviceAnn != null && !serviceAnn.system().trim().isEmpty()) {
                return serviceAnn.system();
            }
            return null;
        }

        private static JavaType extractTypeArg(Type genericReturn, int index) {
            if (!(genericReturn instanceof ParameterizedType)) {
                return null;
            }
            Type[] args = ((ParameterizedType) genericReturn).getActualTypeArguments();
            if (index < 0 || index >= args.length) {
                return null;
            }
            return JsonCodec.protocolMapper().getTypeFactory().constructType(args[index]);
        }

        private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("toString".equals(name)) {
                return "AiServiceProxy(" + serviceInterface.getName() + ")";
            }
            if ("hashCode".equals(name)) {
                return Integer.valueOf(System.identityHashCode(proxy));
            }
            if ("equals".equals(name)) {
                return Boolean.valueOf(proxy == (args != null && args.length > 0 ? args[0] : null));
            }
            return null;
        }
    }
}
