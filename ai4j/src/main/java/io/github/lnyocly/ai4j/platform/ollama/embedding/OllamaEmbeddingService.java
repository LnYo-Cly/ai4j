package io.github.lnyocly.ai4j.platform.ollama.embedding;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.config.OllamaConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.convert.embedding.EmbeddingParameterConvert;
import io.github.lnyocly.ai4j.convert.embedding.EmbeddingResultConvert;
import io.github.lnyocly.ai4j.platform.ollama.embedding.entity.OllamaEmbedding;
import io.github.lnyocly.ai4j.platform.ollama.embedding.entity.OllamaEmbeddingResponse;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.Embedding;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.EmbeddingObject;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.EmbeddingResponse;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IEmbeddingService;
import io.github.lnyocly.ai4j.utils.ValidateUtil;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author cly
 * @Description TODO
 * @Date 2025/2/28 15:52
 */
public class OllamaEmbeddingService implements IEmbeddingService, EmbeddingParameterConvert<OllamaEmbedding>, EmbeddingResultConvert<OllamaEmbeddingResponse> {
    private final OllamaConfig ollamaConfig;
    private final OkHttpClient okHttpClient;

    public OllamaEmbeddingService(Configuration configuration) {
        this.ollamaConfig = configuration.getOllamaConfig();
        this.okHttpClient = configuration.getOkHttpClient();
    }


    @Override
    public EmbeddingResponse embedding(String baseUrl, String apiKey, Embedding embeddingReq) throws Exception {
        if(baseUrl == null || "".equals(baseUrl)) baseUrl = ollamaConfig.getApiHost();
        if(apiKey == null || "".equals(apiKey)) apiKey = ollamaConfig.getApiKey();
        String jsonString = JSON.toJSONString(convertEmbeddingRequest(embeddingReq));

        Request.Builder builder = new Request.Builder()
                .url(ValidateUtil.concatUrl(baseUrl, ollamaConfig.getEmbeddingUrl()))
                .post(RequestBody.create(MediaType.parse(Constants.APPLICATION_JSON), jsonString));
        if(StringUtils.isNotBlank(apiKey)) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        Request request = builder.build();

        Response execute = okHttpClient.newCall(request).execute();
        if (execute.isSuccessful() && execute.body() != null) {
            OllamaEmbeddingResponse ollamaEmbeddingResponse = JSON.parseObject(execute.body().string(), OllamaEmbeddingResponse.class);
            return convertEmbeddingResponse(ollamaEmbeddingResponse);
        }
        return null;
    }

    @Override
    public EmbeddingResponse embedding(Embedding embeddingReq) throws Exception {
        return this.embedding(null, null, embeddingReq);
    }

    @Override
    public OllamaEmbedding convertEmbeddingRequest(Embedding embeddingRequest) {
        // 判断embeddingRequest.getInput() object的类型，是String还是List<String>
        if (embeddingRequest.getInput() instanceof List) {
            return OllamaEmbedding.builder().model(embeddingRequest.getModel()).input((List<String>) embeddingRequest.getInput()).build();
        }else {
            return OllamaEmbedding.builder().model(embeddingRequest.getModel()).input((String) embeddingRequest.getInput()).build();
        }

    }

    @Override
    public EmbeddingResponse convertEmbeddingResponse(OllamaEmbeddingResponse ollamaEmbeddingResponse) {
        EmbeddingResponse.EmbeddingResponseBuilder builder = EmbeddingResponse.builder()
                .model(ollamaEmbeddingResponse.getModel())
                .object("list")
                .usage(new Usage(ollamaEmbeddingResponse.getPromptEvalCount(), 0, ollamaEmbeddingResponse.getPromptEvalCount()));
        List<EmbeddingObject> embeddingObjects = new ArrayList<>();
        List<List<Float>> embeddings = ollamaEmbeddingResponse.getEmbeddings();
        for (int i = 0; i < embeddings.size(); i++) {
            EmbeddingObject embeddingObject = new EmbeddingObject();
            embeddingObject.setIndex(i);
            embeddingObject.setEmbedding(embeddings.get(i));
            embeddingObject.setObject("embedding");
            embeddingObjects.add(embeddingObject);
        }
        builder.data(embeddingObjects);
        return builder.build();
    }
}
