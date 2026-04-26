package io.github.lnyocly.ai4j.flowgram.springboot.autoconfigure;

import io.github.lnyocly.ai4j.AiConfigAutoConfiguration;
import io.github.lnyocly.ai4j.agent.flowgram.Ai4jFlowGramLlmNodeRunner;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramLlmNodeRunner;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutor;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramRuntimeListener;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramRuntimeService;
import io.github.lnyocly.ai4j.agent.trace.TracePricingResolver;
import io.github.lnyocly.ai4j.flowgram.springboot.adapter.FlowGramProtocolAdapter;
import io.github.lnyocly.ai4j.flowgram.springboot.config.FlowGramProperties;
import io.github.lnyocly.ai4j.flowgram.springboot.controller.FlowGramTaskController;
import io.github.lnyocly.ai4j.flowgram.springboot.exception.FlowGramExceptionHandler;
import io.github.lnyocly.ai4j.flowgram.springboot.node.FlowGramCodeNodeExecutor;
import io.github.lnyocly.ai4j.flowgram.springboot.node.FlowGramHttpNodeExecutor;
import io.github.lnyocly.ai4j.flowgram.springboot.node.FlowGramKnowledgeRetrieveNodeExecutor;
import io.github.lnyocly.ai4j.flowgram.springboot.node.FlowGramToolNodeExecutor;
import io.github.lnyocly.ai4j.flowgram.springboot.node.FlowGramVariableNodeExecutor;
import io.github.lnyocly.ai4j.flowgram.springboot.security.DefaultFlowGramAccessChecker;
import io.github.lnyocly.ai4j.flowgram.springboot.security.DefaultFlowGramCallerResolver;
import io.github.lnyocly.ai4j.flowgram.springboot.security.DefaultFlowGramTaskOwnershipStrategy;
import io.github.lnyocly.ai4j.flowgram.springboot.security.FlowGramAccessChecker;
import io.github.lnyocly.ai4j.flowgram.springboot.security.FlowGramCallerResolver;
import io.github.lnyocly.ai4j.flowgram.springboot.security.FlowGramTaskOwnershipStrategy;
import io.github.lnyocly.ai4j.flowgram.springboot.support.FlowGramRuntimeFacade;
import io.github.lnyocly.ai4j.flowgram.springboot.support.FlowGramRuntimeTraceCollector;
import io.github.lnyocly.ai4j.flowgram.springboot.support.FlowGramTaskStore;
import io.github.lnyocly.ai4j.flowgram.springboot.support.InMemoryFlowGramTaskStore;
import io.github.lnyocly.ai4j.flowgram.springboot.support.JdbcFlowGramTaskStore;
import io.github.lnyocly.ai4j.flowgram.springboot.support.RegistryBackedFlowGramModelClientResolver;
import io.github.lnyocly.ai4j.rag.RagContextAssembler;
import io.github.lnyocly.ai4j.rag.Reranker;
import io.github.lnyocly.ai4j.service.factory.AiServiceRegistry;
import io.github.lnyocly.ai4j.vector.store.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;

import javax.sql.DataSource;

@Configuration
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "ai4j.flowgram", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(AiConfigAutoConfiguration.class)
@EnableConfigurationProperties(FlowGramProperties.class)
public class FlowGramAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FlowGramTaskStore flowGramTaskStore(FlowGramProperties properties,
                                               ObjectProvider<DataSource> dataSourceProvider) {
        String type = properties == null || properties.getTaskStore() == null
                ? "memory"
                : properties.getTaskStore().getType();
        if (type == null || "memory".equalsIgnoreCase(type.trim())) {
            return new InMemoryFlowGramTaskStore();
        }
        if ("jdbc".equalsIgnoreCase(type.trim())) {
            DataSource dataSource = dataSourceProvider.getIfAvailable();
            if (dataSource == null) {
                throw new IllegalStateException("FlowGram jdbc task store requires a DataSource bean");
            }
            FlowGramProperties.TaskStoreProperties taskStore = properties.getTaskStore();
            return new JdbcFlowGramTaskStore(
                    dataSource,
                    taskStore == null ? null : taskStore.getTableName(),
                    taskStore == null || taskStore.isInitializeSchema()
            );
        }
        throw new IllegalStateException("Unsupported FlowGram task store type: " + type);
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowGramCallerResolver flowGramCallerResolver(FlowGramProperties properties) {
        return new DefaultFlowGramCallerResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowGramAccessChecker flowGramAccessChecker() {
        return new DefaultFlowGramAccessChecker();
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowGramTaskOwnershipStrategy flowGramTaskOwnershipStrategy(FlowGramProperties properties) {
        return new DefaultFlowGramTaskOwnershipStrategy(properties == null ? null : properties.getTaskRetention());
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowGramProtocolAdapter flowGramProtocolAdapter() {
        return new FlowGramProtocolAdapter();
    }

    @Bean
    @ConditionalOnMissingBean(FlowGramLlmNodeRunner.class)
    public FlowGramLlmNodeRunner flowGramLlmNodeRunner(AiServiceRegistry aiServiceRegistry,
                                                       FlowGramProperties properties,
                                                       ObjectProvider<TracePricingResolver> pricingResolverProvider) {
        return new Ai4jFlowGramLlmNodeRunner(
                new RegistryBackedFlowGramModelClientResolver(aiServiceRegistry, properties),
                pricingResolverProvider.getIfAvailable()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowGramRuntimeService flowGramRuntimeService(FlowGramLlmNodeRunner flowGramLlmNodeRunner,
                                                         ObjectProvider<FlowGramNodeExecutor> executors) {
        FlowGramRuntimeService runtimeService = new FlowGramRuntimeService(flowGramLlmNodeRunner);
        for (FlowGramNodeExecutor executor : executors) {
            runtimeService.registerNodeExecutor(executor);
        }
        return runtimeService;
    }

    @Bean
    @ConditionalOnMissingBean(name = "flowGramHttpNodeExecutor")
    public FlowGramNodeExecutor flowGramHttpNodeExecutor() {
        return new FlowGramHttpNodeExecutor();
    }

    @Bean
    @ConditionalOnMissingBean(name = "flowGramVariableNodeExecutor")
    public FlowGramNodeExecutor flowGramVariableNodeExecutor() {
        return new FlowGramVariableNodeExecutor();
    }

    @Bean
    @ConditionalOnMissingBean(name = "flowGramCodeNodeExecutor")
    public FlowGramNodeExecutor flowGramCodeNodeExecutor() {
        return new FlowGramCodeNodeExecutor();
    }

    @Bean
    @ConditionalOnMissingBean(name = "flowGramToolNodeExecutor")
    public FlowGramNodeExecutor flowGramToolNodeExecutor() {
        return new FlowGramToolNodeExecutor();
    }

    @Bean
    @ConditionalOnBean(AiServiceRegistry.class)
    @ConditionalOnSingleCandidate(VectorStore.class)
    @ConditionalOnMissingBean(name = "flowGramKnowledgeRetrieveNodeExecutor")
    public FlowGramNodeExecutor flowGramKnowledgeRetrieveNodeExecutor(AiServiceRegistry aiServiceRegistry,
                                                                      VectorStore vectorStore,
                                                                      Reranker ragReranker,
                                                                      RagContextAssembler ragContextAssembler) {
        return new FlowGramKnowledgeRetrieveNodeExecutor(
                aiServiceRegistry,
                vectorStore,
                ragReranker,
                ragContextAssembler
        );
    }

    @Bean
    public SmartInitializingSingleton flowGramNodeExecutorRegistrar(FlowGramRuntimeService runtimeService,
                                                                    ObjectProvider<FlowGramNodeExecutor> executors) {
        return new SmartInitializingSingleton() {
            @Override
            public void afterSingletonsInstantiated() {
                for (FlowGramNodeExecutor executor : executors) {
                    runtimeService.registerNodeExecutor(executor);
                }
            }
        };
    }

    @Bean
    public SmartInitializingSingleton flowGramRuntimeListenerRegistrar(FlowGramRuntimeService runtimeService,
                                                                       ObjectProvider<FlowGramRuntimeListener> listeners) {
        return new SmartInitializingSingleton() {
            @Override
            public void afterSingletonsInstantiated() {
                for (FlowGramRuntimeListener listener : listeners) {
                    runtimeService.registerListener(listener);
                }
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowGramRuntimeTraceCollector flowGramRuntimeTraceCollector() {
        return new FlowGramRuntimeTraceCollector();
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowGramRuntimeFacade flowGramRuntimeFacade(FlowGramRuntimeService runtimeService,
                                                       FlowGramProtocolAdapter protocolAdapter,
                                                       FlowGramTaskStore taskStore,
                                                       FlowGramCallerResolver callerResolver,
                                                       FlowGramAccessChecker accessChecker,
                                                       FlowGramTaskOwnershipStrategy ownershipStrategy,
                                                       FlowGramProperties properties,
                                                       FlowGramRuntimeTraceCollector traceCollector) {
        return new FlowGramRuntimeFacade(runtimeService, protocolAdapter, taskStore, callerResolver, accessChecker, ownershipStrategy, properties, traceCollector);
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowGramTaskController flowGramTaskController(FlowGramRuntimeFacade runtimeFacade) {
        return new FlowGramTaskController(runtimeFacade);
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowGramExceptionHandler flowGramExceptionHandler() {
        return new FlowGramExceptionHandler();
    }
}

