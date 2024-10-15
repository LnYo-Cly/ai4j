package io.github.lnyocly.ai4j.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author : isxuwl
 * @Date: 2024/10/15 16:08
 * @Model Description:
 * @Description:
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MinimaxConfig {
    private String apiHost = "https://api.minimax.chat/";
    private String apiKey = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJHcm91cE5hbWUiOiLorrjlpJoiLCJVc2VyTmFtZSI6IuiuuOWkmiIsIkFjY291bnQiOiIiLCJTdWJqZWN0SUQiOiIxODEwNTQwODE4MzQ0MTk4Nzc0IiwiUGhvbmUiOiIxNzM1Njk4OTkwNCIsIkdyb3VwSUQiOiIxODEwNTQwODE4MzM1ODEwMTY2IiwiUGFnZU5hbWUiOiIiLCJNYWlsIjoiIiwiQ3JlYXRlVGltZSI6IjIwMjQtMTAtMTUgMTk6NDg6NTMiLCJpc3MiOiJtaW5pbWF4In0.RRF5KG8_R91q4TNHPuk7EaPiOel5FvJw6TkHwnzDArtnpQRDORfbv2Q6yEIutYRx4eeDMokJkE4g2Bnh52RozGmu25CcLRGVkC-z5tGU5zigDZqHSIknRzIHuHniZrfZNQZLABVovWIxal9ifei7dC7ZzeDgdmi3JTLCNBqFXyPuWt6alUonOhrT6U6brbXnrB2Sp9-u9YX3bc9_dkG1X58SY8rjWcZ66juH62aHNtkJGzkqJGMtQcRqx_TowmH23yschL2lKBVomP2lfVAgLf1JZrW01V9uXd7sTG8YXODJ_gttO-O9MOUV6dRUExThD8X_BPYOl2LXg2tDJFr7Fg";
    private String chatCompletionUrl = "v1/text/chatcompletion_v2";
}
