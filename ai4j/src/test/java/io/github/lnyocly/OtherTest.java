package io.github.lnyocly;

import io.github.lnyocly.ai4j.platform.openai.chat.enums.ChatMessageType;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @Author cly
 * @Description TODO
 */
@Slf4j
public class OtherTest {

    @Test
    public void test(){
        long currentTimeMillis = System.currentTimeMillis();

        String isoDateTime = "2024-07-22T20:33:28.123648Z";
        Instant instant = Instant.parse(isoDateTime);
        long epochSeconds = instant.getEpochSecond();
        System.out.println("Epoch seconds: " + epochSeconds);


        System.out.println(System.currentTimeMillis() - currentTimeMillis);
    }
}
