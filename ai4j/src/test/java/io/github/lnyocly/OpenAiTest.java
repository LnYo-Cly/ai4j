package io.github.lnyocly;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.exception.chain.ErrorHandler;
import io.github.lnyocly.ai4j.exception.error.Error;
import io.github.lnyocly.ai4j.exception.error.OpenAiError;
import io.github.lnyocly.ai4j.interceptor.ErrorInterceptor;
import io.github.lnyocly.ai4j.listener.SseListener;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.Embedding;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.EmbeddingObject;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.EmbeddingResponse;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.IEmbeddingService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factor.AiService;
import io.github.lnyocly.ai4j.utils.OkHttpUtil;
import io.github.lnyocly.ai4j.utils.RecursiveCharacterTextSplitter;
import io.github.lnyocly.ai4j.utils.TikaUtil;
import io.github.lnyocly.ai4j.vector.VertorDataEntity;
import io.github.lnyocly.ai4j.vector.pinecone.PineconeDelete;
import io.github.lnyocly.ai4j.vector.pinecone.PineconeInsert;
import io.github.lnyocly.ai4j.vector.pinecone.PineconeQuery;
import io.github.lnyocly.ai4j.vector.pinecone.PineconeVectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.tika.exception.TikaException;
import org.junit.Before;
import org.junit.Test;
import org.reflections.Reflections;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @Author cly
 * @Description OpenAi测试类
 * @Date 2024/8/3 18:22
 */
@Slf4j
public class OpenAiTest {

    private IEmbeddingService embeddingService;

    private IChatService chatService;
    Reflections reflections = new Reflections();

    @Before
    public void test_init() throws NoSuchAlgorithmException, KeyManagementException {
        OpenAiConfig openAiConfig = new OpenAiConfig();
        openAiConfig.setApiKey("sk-123456789");

        Configuration configuration = new Configuration();
        configuration.setOpenAiConfig(openAiConfig);

        HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor();
        httpLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS);

        OkHttpClient okHttpClient = new OkHttpClient
                .Builder()
                .addInterceptor(httpLoggingInterceptor)
                .addInterceptor(new ErrorInterceptor())
                .connectTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .sslSocketFactory(OkHttpUtil.getIgnoreInitedSslContext().getSocketFactory(), OkHttpUtil.IGNORE_SSL_TRUST_MANAGER_X509)
                .hostnameVerifier(OkHttpUtil.getIgnoreSslHostnameVerifier())
                .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 10809)))
                .build();
        configuration.setOkHttpClient(okHttpClient);

        AiService aiService = new AiService(configuration);

        embeddingService = aiService.getEmbeddingService(PlatformType.OPENAI);

        //chatService = aiService.getChatService(PlatformType.getPlatform("OPENAI"));
        chatService = aiService.getChatService(PlatformType.OPENAI);

    }


    @Test
    public void test_embed() throws Exception {
        Embedding build = Embedding.builder()
                .input("The food was delicious and the waiter...")
                .model("text-embedding-ada-002")
                .build();
        System.out.println(build);

        EmbeddingResponse embedding = embeddingService.embedding(null, null, build);

        System.out.println(embedding);


    }


    @Test
    public void test_chatCompletions_common() throws Exception {
        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model("gpt-4o-mini")
                .message(ChatMessage.withUser("鲁迅为什么打周树人"))
                .build();

        System.out.println("请求参数");
        System.out.println(chatCompletion);

        ChatCompletionResponse chatCompletionResponse = chatService.chatCompletion(chatCompletion);

        System.out.println("请求成功");
        System.out.println(chatCompletionResponse);

    }

    @Test
    public void test_chatCompletions_multimodal() throws Exception {
        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model("yi-vision")
                .message(ChatMessage.withUser("这几张图片，分别有什么动物, 并且是什么品种",
                        "https://tse2-mm.cn.bing.net/th/id/OIP-C.SVxZtXIcz3LbcE4ZeS6jEgHaE7?w=231&h=180&c=7&r=0&o=5&dpr=1.3&pid=1.7",
                        "https://ts3.cn.mm.bing.net/th?id=OIP-C.BYyILFgs3ATnTEQ-B5ApFQHaFj&w=288&h=216&c=8&rs=1&qlt=90&o=6&dpr=1.3&pid=3.1&rm=2"))
                .build();

        System.out.println("请求参数");
        System.out.println(chatCompletion);

        ChatCompletionResponse chatCompletionResponse = chatService.chatCompletion(chatCompletion);

        System.out.println("请求成功");
        System.out.println(chatCompletionResponse);
    }


    @Test
    public void test_chatCompletions_stream() throws Exception {
        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model("gpt-4o-mini")
                .message(ChatMessage.withUser("鲁迅为什么打周树人"))
                .build();


        System.out.println("请求参数");
        System.out.println(chatCompletion);

        // 构造监听器
        SseListener sseListener = new SseListener() {
            @Override
            protected void send() {
                System.out.println(this.getCurrStr());
            }
        };

        chatService.chatCompletionStream(chatCompletion, sseListener);

        System.out.println("请求成功");
        System.out.println(sseListener.getOutput());
        System.out.println(sseListener.getUsage());

    }

    @Test
    public void test_chatCompletions_function() throws Exception {
        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model("gpt-4o-mini")
                .message(ChatMessage.withUser("查询洛阳明天的天气，并告诉我火车是否发车"))
                .functions("queryWeather", "queryTrainInfo")
                .build();

        System.out.println("请求参数");
        System.out.println(chatCompletion);

        ChatCompletionResponse chatCompletionResponse = chatService.chatCompletion(chatCompletion);

        System.out.println("请求成功");
        System.out.println(chatCompletionResponse);

        System.out.println(chatCompletion);

    }

    @Test
    public void test_chatCompletions_stream_function() throws Exception {

        // 构造请求参数
        ChatCompletion chatCompletion = ChatCompletion.builder()
                .model("yi-large-fc")
                .message(ChatMessage.withUser("查询洛阳明天的天气"))
                .functions("queryWeather", "queryTrainInfo")
                .build();


        // 构造监听器
        SseListener sseListener = new SseListener() {
            @Override
            protected void send() {
                System.out.println(this.getCurrStr());
            }
        };
        // 显示函数参数，默认不显示
        sseListener.setShowToolArgs(true);

        // 发送SSE请求
        chatService.chatCompletionStream(chatCompletion, sseListener);
        System.out.println("完整内容： ");
        System.out.println(sseListener.getOutput());
        System.out.println("内容花费： ");
        System.out.println(sseListener.getUsage());
    }

    @Test
    public void test__() throws Exception {
        // 读取文件

        // ......

        // 分割文本

        String word = " <br/> <div class=\"upload-container\"> <svg t=\"1693153944798\" class=\"icon\" viewBox=\"0 0 1024 1024\" version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" p-id=\"4946\" width=\"24\" height=\"24\"><path d=\"M804.23424 866.5344 630.5792 866.5344c-72.14592 0-129.62816-58.69568-129.62816-129.62816L500.95104 494.7712c0-9.78432 8.55552-18.34496 18.34496-18.34496 9.7792 0 18.34496 8.56064 18.34496 18.34496l0 240.91136c0 51.36896 41.57952 92.94336 92.93824 92.94336l173.65504 0c86.82496 0 158.98112-64.81408 163.87072-147.968 2.44736-44.02176-12.23168-86.82496-42.8032-118.62528-30.57152-31.80032-70.92736-50.14016-116.1728-50.14016l-3.67104 0c-6.11328 0-11.00288-2.44736-14.67392-6.11328-3.66592-4.8896-4.89472-9.7792-3.66592-15.89248 7.33696-35.46624 8.56064-72.15616 1.21856-107.6224-20.79232-118.62016-119.84384-209.11616-239.68768-222.5664-77.04576-8.56064-152.86272 15.8976-211.56352 68.48C279.6032 279.53664 245.36576 352.91136 245.36576 431.17568c0 8.56064-6.11328 17.11616-15.8976 18.34496-95.3856 13.45024-165.0944 97.83808-161.42336 195.66592 3.67104 100.2752 92.94336 183.43424 198.11328 183.43424l84.3776 0c9.78432 0 18.34496 8.55552 18.34496 18.33984 0 9.78944-8.56064 17.1264-19.56864 17.1264l-84.3776 0c-123.51488 0-228.6848-97.82784-233.5744-217.6768-4.89472-111.28832 70.92736-207.8976 177.32096-231.13728 4.8896-81.93536 41.57952-158.98112 103.94624-214.01088 66.03776-58.69568 151.63904-86.82496 240.91648-77.04064 135.74144 13.45024 248.24832 117.4016 271.48288 251.91936 6.11328 34.24256 7.33696 67.26144 2.44736 100.2752 47.6928 3.67104 92.93824 25.68704 124.73344 61.14304 37.90848 40.36096 56.25344 91.7248 52.58752 146.75456C998.67648 785.82272 910.62784 866.5344 804.23424 866.5344L804.23424 866.5344z\" fill=\"#2c2c2c\" p-id=\"4947\"></path><path d=\"M663.59808 631.73632c-4.8896 0-9.78432-1.22368-13.45536-4.8896l-132.0704-133.2992L385.9968 625.61792c-7.33696 7.34208-18.34496 7.34208-25.68192 0-7.33696-7.33696-7.33696-18.34496 0-25.68192l145.52576-145.52576c7.33696-7.33696 19.56352-7.33696 25.68192 0l145.53088 145.52576c7.33696 7.33696 7.33696 18.34496 0 25.68192C673.38752 629.29408 668.48768 631.73632 663.59808 631.73632L663.59808 631.73632z\" fill=\"#2c2c2c\" p-id=\"4948\"></path></svg> <input type=\"file\" onchange=\"uploadFile()\" id=\"myFile\" accept=\".pdf\"><div>上传pdf</div> </div>";

        RecursiveCharacterTextSplitter recursiveCharacterTextSplitter = new RecursiveCharacterTextSplitter(1000, 200);

        List<String> strings = recursiveCharacterTextSplitter.splitText(word);

        System.out.println(strings.size());


        // 转为向量
        Embedding build = Embedding.builder()
                .input(strings)
                .model("text-embedding-3-small")
                .build();
        System.out.println(build);

        EmbeddingResponse embedding = embeddingService.embedding(null, null, build);

        System.out.println(embedding);

        List<List<Float>> vectors = embedding.getData().stream().map(EmbeddingObject::getEmbedding).collect(Collectors.toList());

        // 存储转存
        VertorDataEntity vertorDataEntity = new VertorDataEntity();
        vertorDataEntity.setVector(vectors);
        vertorDataEntity.setContent(strings);


        /**
         * {
         *      id
         *      [0.1, 0.1]
         *      <k, v>
         * }
         *
         *
         */

        // 封装请求类
        int count = vectors.size();
        List<PineconeVectors> pineconeVectors = new ArrayList<>();
        List<String> ids = generateIDs(count); // 生成每个向量的id
        List<Map<String, String>> contents = generateContent(strings); // 生成每个向量对应的文本,元数据，kv

        for (int i = 0; i < count; ++i) {
            pineconeVectors.add(new PineconeVectors(ids.get(i), vectors.get(i), contents.get(i)));
        }
        PineconeInsert pineconeInsert = new PineconeInsert(pineconeVectors, "userId");

        // 执行插入
        //String res = PineconeUtil.insertEmbedding(pineconeInsert, "aa");

        //log.info("插入结果{}" ,res);
    }

    @Test
    public void test__query() throws Exception {

        // Given the following conversation and a follow up question, rephrase the follow up question to be a standalone English question.\nChat History is below:\n%s\nFollow Up Input: \n%s\nStandalone English question:
        // 聊天历史(role:content)  ,   新消息

        // String aaa

        // 构建要查询的问题，转为向量
        Embedding build = Embedding.builder()
                .input("aaaaa")
                .model("text-embedding-3-small")
                .build();
        EmbeddingResponse embedding = embeddingService.embedding(null, null, build);
        List<Float> question = embedding.getData().get(0).getEmbedding();


        // 构建向量数据库的查询对象
        PineconeQuery pineconeQueryReq = PineconeQuery.builder()
                .namespace("")
                .topK(20)
                .includeMetadata(true)
                .vector(question)
                .build();

        // 执行查询
        // PineconeQueryResponse response = PineconeUtil.queryEmbedding(pineconeQueryReq, "aa");

        // 从向量数据库拿出的数据, 拼接为一个String
        //String collect = response.getMatches().stream().map(match -> match.getMetadata().get("content")).collect(Collectors.joining(" "));

        // "You are an AI assistant providing helpful advice. You are given the following extracted parts of a long document and a part of the chat history, along with a current question. Provide a conversational answer based on the context and the chat histories provided (You can refer to the chat history to know what the user has asked and thus better answer the current question, but you are not allowed to reply to the previous question asked by the user again). If you can\'t find the answer in the context below, just say \"Hmm, I\'m not sure.\" Don\'t try to make up an answer. If the question is not related to the context, politely respond that you are tuned to only answer questions that are related to the context. \nContext information is below:\n=========\n%s\n=========\nChat history is below:\n=========\n%s\n=========\nCurrent Question: %s (Note: Remember, you only need to reply to me in Chinese and try to increase the content of the reply as much as possible to improve the user experience. I believe you can definitely)"
        // 上下文， 历史，原始消息

        // 发送chat请求进行对话

    }

    @Test
    public void test__delet() {
        PineconeDelete request = PineconeDelete.builder()
                .deleteAll(true)
                .namespace("userId")
                .build();

        // String res = String.valueOf(PineconeUtil.deleteEmbedding(request, "aa"));

        // System.out.println(res);
    }

    @Test
    public void test__tika() throws TikaException, IOException, SAXException {


        File file = new File("C:\\Users\\1\\Desktop\\新建文本文档 (2).txt");
        InputStream inputStream = new FileInputStream(file);
        String s = TikaUtil.parseInputStream(inputStream);
        System.out.println("文本内容");
        System.out.println(s);

        String s1 = TikaUtil.detectMimeType(file);

        System.out.println(s1);


        //AbstractListener abstractListener = new AbstractListener();

    }

    // 生成每个向量的id
    private List<String> generateIDs(int count) {
        List<String> ids = new ArrayList<>();
        for (long i = 0L; i < count; ++i) {
            ids.add("id_" + i);
        }
        return ids;
    }


    // 生成每个向量对应的文本
    private List<Map<String, String>> generateContent(List<String> contents) {
        List<Map<String, String>> finalcontents = new ArrayList<>();

        for (int i = 0; i < contents.size(); i++) {
            HashMap<String, String> map = new HashMap<>();
            map.put("content", contents.get(i));
            finalcontents.add(map);
        }
        return finalcontents;
    }
}
