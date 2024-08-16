package io.github.lnyocly.ai4j.vector.service;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.config.PineconeConfig;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.vector.VertorDataEntity;
import io.github.lnyocly.ai4j.vector.pinecone.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/8/16 17:09
 */
@Slf4j
public class PineconeService {
    private final PineconeConfig pineconeConfig;
    private final OkHttpClient okHttpClient;


    public PineconeService(Configuration configuration) {
        this.pineconeConfig = configuration.getPineconeConfig();
        this.okHttpClient = configuration.getOkHttpClient();
    }

    // 插入Pinecone向量库
    public Integer insert(PineconeInsert pineconeInsertReq){
        Request request = new Request.Builder()
                .url(pineconeConfig.getUrl() + pineconeConfig.getUpsert())
                .post(RequestBody.create(JSON.toJSONString(pineconeInsertReq), MediaType.parse(Constants.APPLICATION_JSON)))
                .header("accept", Constants.APPLICATION_JSON)
                .header("content-type", Constants.APPLICATION_JSON)
                .header("Api-Key", pineconeConfig.getKey())
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("Error inserting into Pinecone vector store: {}", response.message());
                throw new CommonException("Error inserting into Pinecone: " + response.message());
            }

            // {"upsertedCount":3}
            return JSON.parseObject(response.body().string(), PineconeInsertResponse.class).getUpsertedCount();
        } catch (Exception e) {
            log.error("OkHttpClient exception! {}", e.getMessage(), e);
            throw new CommonException("Failed to insert into Pinecone due to network error." + e.getMessage());
        }
    }

    public Integer insert(VertorDataEntity vertorDataEntity, String namespace) {
        int count = vertorDataEntity.getContent().size();
        List<PineconeVectors> pineconeVectors = new ArrayList<>();
        // 生成每个向量的id
        List<String> ids = generateIDs(count);
        // 生成每个向量对应的文本,元数据，kv
        List<Map<String, String>> metadatas = generateContent(vertorDataEntity.getContent());

        for(int i = 0;i < count; ++i){
            pineconeVectors.add(new PineconeVectors(ids.get(i), vertorDataEntity.getVector().get(i), metadatas.get(i)));
        }
        PineconeInsert pineconeInsert = new PineconeInsert(pineconeVectors, namespace);
        return this.insert(pineconeInsert);
    }

    // 从Pinecone向量库中查询相似向量
    public PineconeQueryResponse query(PineconeQuery pineconeQueryReq){
        Request request = new Request.Builder()
                .url(pineconeConfig.getUrl() + pineconeConfig.getQuery())
                .post(RequestBody.create(JSON.toJSONString(pineconeQueryReq), MediaType.parse(Constants.APPLICATION_JSON)))
                .header("accept", Constants.APPLICATION_JSON)
                .header("content-type", Constants.APPLICATION_JSON)
                .header("Api-Key", pineconeConfig.getKey())
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("Error querying Pinecone vector store: {}", response.message());
                throw new CommonException("Error querying Pinecone: " + response.message());
            }

            String body = response.body().string();
            return JSON.parseObject(body, PineconeQueryResponse.class);
        } catch (IOException e) {
            log.error("OkHttpClient exception! {}", e.getMessage(), e);
            throw new CommonException("Failed to query Pinecone due to network error." + e.getMessage());
        }
    }

    public String query(PineconeQuery pineconeQuery, String delimiter){
        PineconeQueryResponse queryResponse = this.query(pineconeQuery);
        if(delimiter == null) delimiter = "";
        return queryResponse.getMatches().stream().map(match -> match.getMetadata().get(Constants.METADATA_KEY)).collect(Collectors.joining(delimiter));
    }

    // 从Pinecone向量库中删除向量
    public Boolean delete(PineconeDelete pineconeDeleteReq){
        Request request = new Request.Builder()
                .url(pineconeConfig.getUrl() + pineconeConfig.getDelete())
                .post(RequestBody.create(JSON.toJSONString(pineconeDeleteReq), MediaType.parse(Constants.APPLICATION_JSON)))
                .header("accept", Constants.APPLICATION_JSON)
                .header("content-type", Constants.APPLICATION_JSON)
                .header("Api-Key", pineconeConfig.getKey())
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("Error deleting from Pinecone vector store: {}", response.message());
                throw new CommonException("Error deleting from Pinecone: " + response.message());
            }
            return true;
        } catch (IOException e) {
            log.error("OkHttpClient exception! {}", e.getMessage(), e);
            throw new CommonException("Failed to delete from Pinecone due to network error." + e.getMessage());
        }
    }

    // 生成每个向量的id
    public List<String> generateIDs(int count){
        List<String> ids = new ArrayList<>();
        for (long i = 0L; i < count; ++i) {
            ids.add("id_" + i);
        }
        return ids;
    }


    // 生成每个向量对应的文本
    public List<Map<String, String>> generateContent(List<String> contents){
        List<Map<String, String>> finalcontents = new ArrayList<>();

        for(int i = 0; i < contents.size(); i++){
            HashMap<String, String> map = new HashMap<>();
            map.put(Constants.METADATA_KEY, contents.get(i));
            finalcontents.add(map);
        }
        return finalcontents;
    }

}
