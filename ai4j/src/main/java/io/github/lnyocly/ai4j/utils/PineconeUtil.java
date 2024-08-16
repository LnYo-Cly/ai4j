package io.github.lnyocly.ai4j.utils;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.constant.Constants;
import io.github.lnyocly.ai4j.exception.CommonException;
import io.github.lnyocly.ai4j.vector.pinecone.PineconeDelete;
import io.github.lnyocly.ai4j.vector.pinecone.PineconeInsert;
import io.github.lnyocly.ai4j.vector.pinecone.PineconeQuery;
import io.github.lnyocly.ai4j.vector.pinecone.PineconeQueryResponse;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/8/14 18:16
 */
@Slf4j
public class PineconeUtil {
    /**
     *  使用Pinecone作为向量数据库需要填写(另外需要在表admin_apikey中插入一条记录，type为4，name为Pinecone的apikey)
     *  TODO
     */
    private static final String PINECONE_API_URL = "https://chatgpt-hxm5j0y.svc.aped-4627-b74a.pinecone.io";
private static final OkHttpClient okHttpClient = new OkHttpClient();
    // 插入Pinecone向量库
    public static String insertEmbedding(PineconeInsert pineconeInsertReq, String apiKey){
// {"upsertedCount":3}
        Request request = new Request.Builder()
                .url(PINECONE_API_URL + "/vectors/upsert")
                .post(RequestBody.create(MediaType.parse(Constants.APPLICATION_JSON), JSON.toJSONString(pineconeInsertReq).toString()))
                .header("accept", "application/json")
                .header("content-type", "application/json")
                .header("Api-Key", "c4e52c27-3fbb-462e-b0c7-a99753cb7b34")
                .build();
        Response response = null;
        try {


            response = okHttpClient.newCall(request).execute();

            if(!response.isSuccessful()){
                log.error("插入Pinecone向量库异常：{}", response.message());
                throw new CommonException(response.message());
            }

            String body = response.body().string();

            return body;

        } catch (IOException e) {
            log.error("okHttpClient异常! {}", e.getMessage());
        }
        finally {
            if(response != null){
                response.close();
            }
        }
        return "";
    }

      // 从Pinecone向量库中查询相似
         public static PineconeQueryResponse queryEmbedding(PineconeQuery pineconeQueryReq, String apiKey){

             Request request = new Request.Builder()
                     .url(PINECONE_API_URL + "/query")
                     .post(RequestBody.create(MediaType.parse(Constants.APPLICATION_JSON), JSON.toJSONString(pineconeQueryReq).toString()))
                     .header("accept", "application/json")
                     .header("content-type", "application/json")
                     .header("Api-Key", "c4e52c27-3fbb-462e-b0c7-a99753cb7b34")
                     .build();
             Response response = null;
             try {
                 response =okHttpClient.newCall(request).execute();

                 if(!response.isSuccessful()){
                     log.error("查询Pinecone向量库异常：{}", response.message());
                     throw new CommonException(response.message());
                 }

                 String body = response.body().string();

                 return JSON.parseObject(body, PineconeQueryResponse.class);

             }
             catch (IOException e) {
                 log.error("okHttpClient异常! {}", e.getMessage());
             }
             finally {
                 if(response != null) {
                     response.close();
                 }
             }
             return null;
         }

         // 从Pinecone向量库中删除向量
         public static Boolean deleteEmbedding(PineconeDelete pineconeDeleteReq, String apiKey){

             Request request = new Request.Builder()
                     .url(PINECONE_API_URL + "/vectors/delete")
                     .post(RequestBody.create(MediaType.parse(Constants.APPLICATION_JSON), JSON.toJSONString(pineconeDeleteReq).toString()))
                     .header("accept", "application/json")
                     .header("content-type", "application/json")
                     .header("Api-Key", "c4e52c27-3fbb-462e-b0c7-a99753cb7b34")
                     .build();
             Response response = null;
             try {
                 response = okHttpClient.newCall(request).execute();

                 if(!response.isSuccessful()){
                     log.error("删除Pinecone向量库异常：{}", response.message());
                     throw new CommonException(response.message());
                 }

                 return true;

             }
             catch (IOException e) {
                 log.error("okHttpClient异常! {}", e.getMessage());
             }
             finally {
                 if(response != null){
                     response.close();
                 }
             }
             return false;
         }
}
