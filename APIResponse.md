
# ollama 输出格式
## ollama deepseek流式思考格式
```plaintext
{"model":"deepseek-r1:1.5b","created_at":"2025-12-02T07:05:39.5082547Z","message":{"role":"assistant","content":"\u003cthink\u003e"},"done":false}
{"model":"deepseek-r1:1.5b","created_at":"2025-12-02T07:05:39.526028Z","message":{"role":"assistant","content":"\n"},"done":false}
{"model":"deepseek-r1:1.5b","created_at":"2025-12-02T07:05:39.5438742Z","message":{"role":"assistant","content":"嗯"},"done":false}
{"model":"deepseek-r1:1.5b","created_at":"2025-12-02T07:05:39.5621237Z","message":{"role":"assistant","content":"，"},"done":false}
{"model":"deepseek-r1:1.5b","created_at":"2025-12-02T07:05:39.579897Z","message":{"role":"assistant","content":"这个问题"},"done":false}
......
......
{"model":"deepseek-r1:1.5b","created_at":"2025-12-02T07:05:55.2277024Z","message":{"role":"assistant","content":"角度"},"done":false}
{"model":"deepseek-r1:1.5b","created_at":"2025-12-02T07:05:55.2431824Z","message":{"role":"assistant","content":"。\n"},"done":false}
{"model":"deepseek-r1:1.5b","created_at":"2025-12-02T07:05:55.2582235Z","message":{"role":"assistant","content":"\u003c/think\u003e"},"done":false}
{"model":"deepseek-r1:1.5b","created_at":"2025-12-02T07:05:55.2739571Z","message":{"role":"assistant","content":"\n\n"},"done":false}
{"model":"deepseek-r1:1.5b","created_at":"2025-12-02T07:05:55.2892043Z","message":{"role":"assistant","content":"根据"},"done":false}
{"model":"deepseek-r1:1.5b","created_at":"2025-12-02T07:05:55.3044901Z","message":{"role":"assistant","content":"上述"},"done":false}
{"model":"deepseek-r1:1.5b","created_at":"2025-12-02T07:05:55.3198267Z","message":{"role":"assistant","content":"思考"},"done":false}
{"model":"deepseek-r1:1.5b","created_at":"2025-12-02T07:05:55.3351357Z","message":{"role":"assistant","content":"，"},"done":false}
......
......
{"model":"deepseek-r1:1.5b","created_at":"2025-12-02T07:05:59.0737987Z","message":{"role":"assistant","content":"角度"},"done":false}
{"model":"deepseek-r1:1.5b","created_at":"2025-12-02T07:05:59.0897372Z","message":{"role":"assistant","content":"来"},"done":false}
{"model":"deepseek-r1:1.5b","created_at":"2025-12-02T07:05:59.106668Z","message":{"role":"assistant","content":"考虑"},"done":false}
{"model":"deepseek-r1:1.5b","created_at":"2025-12-02T07:05:59.1230133Z","message":{"role":"assistant","content":"。"},"done":false}
{"model":"deepseek-r1:1.5b","created_at":"2025-12-02T07:05:59.1393648Z","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop","total_duration":20167437800,"load_duration":144564100,"prompt_eval_count":11,"prompt_eval_duration":377083900,"eval_count":1263,"eval_duration":17517683300}

```
ollama的deepseek-r1模型，思考和内容都在content字段，思考内容在content<think></think>标签中包裹。

## ollama qwen模型的思考格式
```plaintext
{"model":"qwen3:1.7b","created_at":"2025-12-02T08:09:09.0216364Z","message":{"role":"assistant","content":"","thinking":"嗯"},"done":false}
{"model":"qwen3:1.7b","created_at":"2025-12-02T08:09:09.0395495Z","message":{"role":"assistant","content":"","thinking":"，"},"done":false}
{"model":"qwen3:1.7b","created_at":"2025-12-02T08:09:09.057339Z","message":{"role":"assistant","content":"","thinking":"我现在"},"done":false}
......
......
{"model":"qwen3:1.7b","created_at":"2025-12-02T08:09:24.4065087Z","message":{"role":"assistant","content":"","thinking":"9"},"done":false}
{"model":"qwen3:1.7b","created_at":"2025-12-02T08:09:24.4223848Z","message":{"role":"assistant","content":"","thinking":"。\n"},"done":false}
{"model":"qwen3:1.7b","created_at":"2025-12-02T08:09:24.4724457Z","message":{"role":"assistant","content":"要"},"done":false}
{"model":"qwen3:1.7b","created_at":"2025-12-02T08:09:24.4879096Z","message":{"role":"assistant","content":"计算"},"done":false}
......
......
{"model":"qwen3:1.7b","created_at":"2025-12-02T08:09:27.2373334Z","message":{"role":"assistant","content":"}\n"},"done":false}
{"model":"qwen3:1.7b","created_at":"2025-12-02T08:09:27.2542881Z","message":{"role":"assistant","content":"$$"},"done":false}
{"model":"qwen3:1.7b","created_at":"2025-12-02T08:09:27.2709359Z","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop","total_duration":20340585000,"load_duration":1869728500,"prompt_eval_count":22,"prompt_eval_duration":163005200,"eval_count":1186,"eval_duration":17930876800}

```
可以看到，当前使用的qwen3:1.7b模型的思考，放在了thinking字段里面，与deepseek-r1的不同.

## ollama qwen模型的思考以及函数调用
```plaintext
{"model":"qwen3:1.7b","created_at":"2025-12-02T07:56:28.4999977Z","message":{"role":"assistant","content":"","thinking":"好的"},"done":false}
{"model":"qwen3:1.7b","created_at":"2025-12-02T07:56:28.5157549Z","message":{"role":"assistant","content":"","thinking":"，"},"done":false}
{"model":"qwen3:1.7b","created_at":"2025-12-02T07:56:28.5303794Z","message":{"role":"assistant","content":"","thinking":"用户"},"done":false}
......
{"model":"qwen3:1.7b","created_at":"2025-12-02T07:56:34.2064512Z","message":{"role":"assistant","content":"","thinking":"函数"},"done":false}
{"model":"qwen3:1.7b","created_at":"2025-12-02T07:56:34.2227057Z","message":{"role":"assistant","content":"","thinking":"。\n"},"done":false}
{"model":"qwen3:1.7b","created_at":"2025-12-02T07:56:34.6729406Z","message":{"role":"assistant","content":"","tool_calls":[{"id":"call_mted8wc5","function":{"index":0,"name":"get_current_weather","arguments":{"format":"celsius","location":"北京"}}}]},"done":false}
{"model":"qwen3:1.7b","created_at":"2025-12-02T07:56:35.1152019Z","message":{"role":"assistant","content":"","tool_calls":[{"id":"call_l6ddyzav","function":{"index":1,"name":"get_history_weather","arguments":{"format":"celsius","location":"洛阳"}}}]},"done":false}
{"model":"qwen3:1.7b","created_at":"2025-12-02T07:56:35.1470038Z","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop","total_duration":40551745400,"load_duration":32677479300,"prompt_eval_count":302,"prompt_eval_duration":1156607100,"eval_count":431,"eval_duration":6539173200}

```
可以看到，当前使用的qwen3:1.7b模型的思考，放在了thinking字段里面，与deepseek-r1的不同.
并且一条data里面的tool_calls就是完整的内容（包含函数名、参数，无需额外拼接）

## ollama流式输出，无思考内容
```plaintext
{"model":"qwen:0.5b","created_at":"2025-12-02T08:14:34.3521847Z","message":{"role":"assistant","content":"北京"},"done":false}
{"model":"qwen:0.5b","created_at":"2025-12-02T08:14:34.3618598Z","message":{"role":"assistant","content":"今天的"},"done":false}
{"model":"qwen:0.5b","created_at":"2025-12-02T08:14:34.372362Z","message":{"role":"assistant","content":"天气"},"done":false}
{"model":"qwen:0.5b","created_at":"2025-12-02T08:14:34.3812687Z","message":{"role":"assistant","content":"晴"},"done":false}
{"model":"qwen:0.5b","created_at":"2025-12-02T08:14:34.3904175Z","message":{"role":"assistant","content":"朗"},"done":false}
{"model":"qwen:0.5b","created_at":"2025-12-02T08:14:34.400542Z","message":{"role":"assistant","content":"，"},"done":false}
{"model":"qwen:0.5b","created_at":"2025-12-02T08:14:34.4093323Z","message":{"role":"assistant","content":"气温"},"done":false}
{"model":"qwen:0.5b","created_at":"2025-12-02T08:14:34.4191185Z","message":{"role":"assistant","content":"较低"},"done":false}
{"model":"qwen:0.5b","created_at":"2025-12-02T08:14:34.4280426Z","message":{"role":"assistant","content":"，"},"done":false}
{"model":"qwen:0.5b","created_at":"2025-12-02T08:14:34.4385939Z","message":{"role":"assistant","content":"建议"},"done":false}
{"model":"qwen:0.5b","created_at":"2025-12-02T08:14:34.4479457Z","message":{"role":"assistant","content":"带上"},"done":false}
{"model":"qwen:0.5b","created_at":"2025-12-02T08:14:34.4573942Z","message":{"role":"assistant","content":"伞"},"done":false}
{"model":"qwen:0.5b","created_at":"2025-12-02T08:14:34.4690799Z","message":{"role":"assistant","content":"或者"},"done":false}
{"model":"qwen:0.5b","created_at":"2025-12-02T08:14:34.4812969Z","message":{"role":"assistant","content":"手套"},"done":false}
{"model":"qwen:0.5b","created_at":"2025-12-02T08:14:34.4898927Z","message":{"role":"assistant","content":"。"},"done":false}
{"model":"qwen:0.5b","created_at":"2025-12-02T08:14:34.4992387Z","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop","total_duration":12329858900,"load_duration":12122030000,"prompt_eval_count":13,"prompt_eval_duration":55622600,"eval_count":16,"eval_duration":120145700}

```
如上是ollama，无思考，无函数调用的流式输出

## ollama qwen 单独函数调用输出
```plaintext
{"model":"qwen2.5:7b","created_at":"2025-12-02T09:51:18.4437234Z","message":{"role":"assistant","content":"","tool_calls":[{"id":"call_dwjfhc0f","function":{"index":0,"name":"get_current_weather","arguments":{"format":"celsius","location":"北京"}}}]},"done":false}
{"model":"qwen2.5:7b","created_at":"2025-12-02T09:51:23.9746303Z","message":{"role":"assistant","content":"","tool_calls":[{"id":"call_e8ymdkla","function":{"index":1,"name":"get_history_weather","arguments":{"format":"celsius","location":"洛阳"}}}]},"done":false}
{"model":"qwen2.5:7b","created_at":"2025-12-02T09:51:24.3847699Z","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop","total_duration":49235573300,"load_duration":35305229300,"prompt_eval_count":316,"prompt_eval_duration":4145222400,"eval_count":56,"eval_duration":9719530300}

```

# openai
## openai 普通流式调用
```plaintext
data: {"id":"chatcmpl-9709rQdvMSIASrvcWGVsJMQouP2UV","object":"chat.completion.chunk","created":1764678937,"model":"gpt-4o","system_fingerprint":"fp_3bc1b5746c","choices":[{"index":0,"delta":{"content":""},"logprobs":null,"finish_reason":null}]} 

data: {"id":"chatcmpl-9709rQdvMSIASrvcWGVsJMQouP2UV","object":"chat.completion.chunk","created":1764678937,"model":"gpt-4o","system_fingerprint":"fp_3bc1b5746c","choices":[{"index":0,"delta":{"content":"The"},"logprobs":null,"finish_reason":null}]} 

data: {"id":"chatcmpl-9709rQdvMSIASrvcWGVsJMQouP2UV","object":"chat.completion.chunk","created":1764678937,"model":"gpt-4o","system_fingerprint":"fp_3bc1b5746c","choices":[{"index":0,"delta":{"content":" image"},"logprobs":null,"finish_reason":null}]} 

data: {"id":"chatcmpl-9709rQdvMSIASrvcWGVsJMQouP2UV","object":"chat.completion.chunk","created":1764678937,"model":"gpt-4o","system_fingerprint":"fp_3bc1b5746c","choices":[{"index":0,"delta":{"content":" shows"},"logprobs":null,"finish_reason":null}]} 

data: {"id":"chatcmpl-9709rQdvMSIASrvcWGVsJMQouP2UV","object":"chat.completion.chunk","created":1764678937,"model":"gpt-4o","system_fingerprint":"fp_3bc1b5746c","choices":[{"index":0,"delta":{"content":" a"},"logprobs":null,"finish_reason":null}]} 

data: {"id":"chatcmpl-9709rQdvMSIASrvcWGVsJMQouP2UV","object":"chat.completion.chunk","created":1764678937,"model":"gpt-4o","system_fingerprint":"fp_3bc1b5746c","choices":[{"index":0,"delta":{"content":" computer"},"logprobs":null,"finish_reason":null}]} 
......
......

data: {"id":"chatcmpl-9709rQdvMSIASrvcWGVsJMQouP2UV","object":"chat.completion.chunk","created":1764678938,"model":"gpt-4o","system_fingerprint":"fp_3bc1b5746c","choices":[{"index":0,"delta":{"content":" contains"},"logprobs":null,"finish_reason":null}]} 

data: {"id":"chatcmpl-9709rQdvMSIASrvcWGVsJMQouP2UV","object":"chat.completion.chunk","created":1764678938,"model":"gpt-4o","system_fingerprint":"fp_3bc1b5746c","choices":[{"index":0,"delta":{"content":" no"},"logprobs":null,"finish_reason":null}]} 

data: {"id":"chatcmpl-9709rQdvMSIASrvcWGVsJMQouP2UV","object":"chat.completion.chunk","created":1764678938,"model":"gpt-4o","system_fingerprint":"fp_3bc1b5746c","choices":[{"index":0,"delta":{"content":" visible"},"logprobs":null,"finish_reason":null}]} 

data: {"id":"chatcmpl-9709rQdvMSIASrvcWGVsJMQouP2UV","object":"chat.completion.chunk","created":1764678938,"model":"gpt-4o","system_fingerprint":"fp_3bc1b5746c","choices":[{"index":0,"delta":{"content":" actual"},"logprobs":null,"finish_reason":null}]} 

data: {"id":"chatcmpl-9709rQdvMSIASrvcWGVsJMQouP2UV","object":"chat.completion.chunk","created":1764678938,"model":"gpt-4o","system_fingerprint":"fp_3bc1b5746c","choices":[{"index":0,"delta":{"content":" images"},"logprobs":null,"finish_reason":null}]} 

data: {"id":"chatcmpl-9709rQdvMSIASrvcWGVsJMQouP2UV","object":"chat.completion.chunk","created":1764678938,"model":"gpt-4o","system_fingerprint":"fp_3bc1b5746c","choices":[{"index":0,"delta":{"content":"."},"logprobs":null,"finish_reason":null}]} 

data: {"id":"chatcmpl-9709rQdvMSIASrvcWGVsJMQouP2UV","object":"chat.completion.chunk","created":1764678938,"model":"gpt-4o","system_fingerprint":"fp_3bc1b5746c","choices":[{"index":0,"delta":{"content":""},"logprobs":null,"finish_reason":null}]} 

data: {"id":"chatcmpl-9709rQdvMSIASrvcWGVsJMQouP2UV","object":"chat.completion.chunk","created":1764678938,"model":"gpt-4o","system_fingerprint":"fp_3bc1b5746c","choices":[{"index":0,"delta":{"content":""},"logprobs":null,"finish_reason":null}]} 

data: {"id":"chatcmpl-9709rQdvMSIASrvcWGVsJMQouP2UV","object":"chat.completion.chunk","created":1764678938,"model":"gpt-4o","system_fingerprint":"fp_3bc1b5746c","choices":[{"index":0,"delta":{"content":""},"logprobs":null,"finish_reason":null}]} 

data: {"id":"chatcmpl-89CvUKf0C36wUexKrTrmhf5tTEnEw","object":"chat.completion.chunk","model":"gpt-4o","created":1764678938,"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

data: {"id":"chatcmpl-89CvUKf0C36wUexKrTrmhf5tTEnEw","object":"chat.completion.chunk","created":1764678938,"model":"gpt-4o","system_fingerprint":"","choices":[],"usage":{"prompt_tokens":1459,"completion_tokens":91,"total_tokens":1550,"prompt_tokens_details":{"cached_tokens":0,"text_tokens":0,"audio_tokens":0,"image_tokens":0},"completion_tokens_details":{"text_tokens":0,"audio_tokens":0,"reasoning_tokens":0},"input_tokens":0,"output_tokens":0,"input_tokens_details":null,"claude_cache_creation_5_m_tokens":0,"claude_cache_creation_1_h_tokens":0}}

data: [DONE]


```

## openai 函数调用流式输出
```plaintext
data: {"id":"chatcmpl-CiKIfaJe92tvtH3QZModD64Qz5uvn","object":"chat.completion.chunk","created":1764680501,"model":"gpt-4.1-nano-2025-04-14","service_tier":"default","system_fingerprint":"fp_1a97b5aa6c","usage":null,"choices":[{"index":0,"delta":{"role":"assistant","content":null},"logprobs":null,"finish_reason":null}],"obfuscation":"58LAb"}

data: {"id":"chatcmpl-CiKIfaJe92tvtH3QZModD64Qz5uvn","object":"chat.completion.chunk","created":1764680501,"model":"gpt-4.1-nano-2025-04-14","service_tier":"default","system_fingerprint":"fp_1a97b5aa6c","usage":null,"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_8wSWhuzOhClEzH8c2pYjRIiz","type":"function","function":{"name":"get_current_weather","arguments":""}}]},"logprobs":null,"finish_reason":null}],"obfuscation":"zz7xlYFGDdsJ"}

data: {"id":"chatcmpl-CiKIfaJe92tvtH3QZModD64Qz5uvn","object":"chat.completion.chunk","created":1764680501,"model":"gpt-4.1-nano-2025-04-14","service_tier":"default","system_fingerprint":"fp_1a97b5aa6c","usage":null,"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"fo"}}]},"logprobs":null,"finish_reason":null}],"obfuscation":"5hOyVc12Sg4"}

data: {"id":"chatcmpl-CiKIfaJe92tvtH3QZModD64Qz5uvn","object":"chat.completion.chunk","created":1764680501,"model":"gpt-4.1-nano-2025-04-14","service_tier":"default","system_fingerprint":"fp_1a97b5aa6c","usage":null,"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"rmat\""}}]},"logprobs":null,"finish_reason":null}],"obfuscation":"uDVX3RZjQX"}

data: {"id":"chatcmpl-CiKIfaJe92tvtH3QZModD64Qz5uvn","object":"chat.completion.chunk","created":1764680501,"model":"gpt-4.1-nano-2025-04-14","service_tier":"default","system_fingerprint":"fp_1a97b5aa6c","usage":null,"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":": \"cel"}}]},"logprobs":null,"finish_reason":null}],"obfuscation":"bAyNo0Qi2"}

data: {"id":"chatcmpl-CiKIfaJe92tvtH3QZModD64Qz5uvn","object":"chat.completion.chunk","created":1764680501,"model":"gpt-4.1-nano-2025-04-14","service_tier":"default","system_fingerprint":"fp_1a97b5aa6c","usage":null,"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"sius"}}]},"logprobs":null,"finish_reason":null}],"obfuscation":"cb7g4JkAKtyK"}

data: {"id":"chatcmpl-CiKIfaJe92tvtH3QZModD64Qz5uvn","object":"chat.completion.chunk","created":1764680501,"model":"gpt-4.1-nano-2025-04-14","service_tier":"default","system_fingerprint":"fp_1a97b5aa6c","usage":null,"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\", \"l"}}]},"logprobs":null,"finish_reason":null}],"obfuscation":"wHzVJqRg1"}

data: {"id":"chatcmpl-CiKIfaJe92tvtH3QZModD64Qz5uvn","object":"chat.completion.chunk","created":1764680501,"model":"gpt-4.1-nano-2025-04-14","service_tier":"default","system_fingerprint":"fp_1a97b5aa6c","usage":null,"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"ocatio"}}]},"logprobs":null,"finish_reason":null}],"obfuscation":"9lmVWssxKZ"}

data: {"id":"chatcmpl-CiKIfaJe92tvtH3QZModD64Qz5uvn","object":"chat.completion.chunk","created":1764680501,"model":"gpt-4.1-nano-2025-04-14","service_tier":"default","system_fingerprint":"fp_1a97b5aa6c","usage":null,"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"n\": "}}]},"logprobs":null,"finish_reason":null}],"obfuscation":"4vf7qsxUt6B"}

data: {"id":"chatcmpl-CiKIfaJe92tvtH3QZModD64Qz5uvn","object":"chat.completion.chunk","created":1764680501,"model":"gpt-4.1-nano-2025-04-14","service_tier":"default","system_fingerprint":"fp_1a97b5aa6c","usage":null,"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"北京\"}"}}]},"logprobs":null,"finish_reason":null}],"obfuscation":"vVRyaWfpd"}

data: {"id":"chatcmpl-CiKIfaJe92tvtH3QZModD64Qz5uvn","object":"chat.completion.chunk","created":1764680501,"model":"gpt-4.1-nano-2025-04-14","service_tier":"default","system_fingerprint":"fp_1a97b5aa6c","usage":null,"choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"id":"call_RbImHA6tBOwuzwIAvsQKFOox","type":"function","function":{"name":"get_history_weather","arguments":""}}]},"logprobs":null,"finish_reason":null}],"obfuscation":"NnnjipxeyCb0"}

data: {"id":"chatcmpl-CiKIfaJe92tvtH3QZModD64Qz5uvn","object":"chat.completion.chunk","created":1764680501,"model":"gpt-4.1-nano-2025-04-14","service_tier":"default","system_fingerprint":"fp_1a97b5aa6c","usage":null,"choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"{\"fo"}}]},"logprobs":null,"finish_reason":null}],"obfuscation":"fXsyoDJA0to"}

data: {"id":"chatcmpl-CiKIfaJe92tvtH3QZModD64Qz5uvn","object":"chat.completion.chunk","created":1764680501,"model":"gpt-4.1-nano-2025-04-14","service_tier":"default","system_fingerprint":"fp_1a97b5aa6c","usage":null,"choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"rmat\""}}]},"logprobs":null,"finish_reason":null}],"obfuscation":"WLN9UjESD1"}

data: {"id":"chatcmpl-CiKIfaJe92tvtH3QZModD64Qz5uvn","object":"chat.completion.chunk","created":1764680501,"model":"gpt-4.1-nano-2025-04-14","service_tier":"default","system_fingerprint":"fp_1a97b5aa6c","usage":null,"choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":": \"cel"}}]},"logprobs":null,"finish_reason":null}],"obfuscation":"SQJWWIVud"}

data: {"id":"chatcmpl-CiKIfaJe92tvtH3QZModD64Qz5uvn","object":"chat.completion.chunk","created":1764680501,"model":"gpt-4.1-nano-2025-04-14","service_tier":"default","system_fingerprint":"fp_1a97b5aa6c","usage":null,"choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"sius"}}]},"logprobs":null,"finish_reason":null}],"obfuscation":"Z9eEX8Cu2zbU"}

data: {"id":"chatcmpl-CiKIfaJe92tvtH3QZModD64Qz5uvn","object":"chat.completion.chunk","created":1764680501,"model":"gpt-4.1-nano-2025-04-14","service_tier":"default","system_fingerprint":"fp_1a97b5aa6c","usage":null,"choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"\", \"l"}}]},"logprobs":null,"finish_reason":null}],"obfuscation":"H6p1PqKXQ"}

data: {"id":"chatcmpl-CiKIfaJe92tvtH3QZModD64Qz5uvn","object":"chat.completion.chunk","created":1764680501,"model":"gpt-4.1-nano-2025-04-14","service_tier":"default","system_fingerprint":"fp_1a97b5aa6c","usage":null,"choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"ocatio"}}]},"logprobs":null,"finish_reason":null}],"obfuscation":"tCzhDKlaHz"}

data: {"id":"chatcmpl-CiKIfaJe92tvtH3QZModD64Qz5uvn","object":"chat.completion.chunk","created":1764680501,"model":"gpt-4.1-nano-2025-04-14","service_tier":"default","system_fingerprint":"fp_1a97b5aa6c","usage":null,"choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"n\": "}}]},"logprobs":null,"finish_reason":null}],"obfuscation":"d7wEQ2vBGqg"}

data: {"id":"chatcmpl-CiKIfaJe92tvtH3QZModD64Qz5uvn","object":"chat.completion.chunk","created":1764680501,"model":"gpt-4.1-nano-2025-04-14","service_tier":"default","system_fingerprint":"fp_1a97b5aa6c","usage":null,"choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"\"洛阳\"}"}}]},"logprobs":null,"finish_reason":null}],"obfuscation":"yt6d6eJNh"}

data: {"id":"chatcmpl-CiKIfaJe92tvtH3QZModD64Qz5uvn","object":"chat.completion.chunk","created":1764680501,"model":"gpt-4.1-nano-2025-04-14","service_tier":"default","system_fingerprint":"fp_1a97b5aa6c","choices":[{"index":0,"delta":{},"logprobs":null,"finish_reason":"tool_calls"}],"usage":null,"obfuscation":"azv06a8lAt7uZY"}

data: {"id":"chatcmpl-CiKIfaJe92tvtH3QZModD64Qz5uvn","object":"chat.completion.chunk","created":1764680502,"model":"gpt-4.1-nano","choices":[{"index":0,"delta":{}}],"usage":{"prompt_tokens":172,"completion_tokens":57,"total_tokens":229,"prompt_tokens_details":{"cached_tokens":0,"audio_tokens":0},"completion_tokens_details":{"audio_tokens":0,"reasoning_tokens":0,"accepted_prediction_tokens":0,"rejected_prediction_tokens":0},"input_tokens":0,"output_tokens":0,"input_tokens_details":null}}

data: [DONE]

```

# deepseek
## deepseek 普通流式
```plaintext
data: {"id":"8d101105-3bab-41db-8ad6-a7d6baa79b36","object":"chat.completion.chunk","created":1764680764,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"role":"assistant","content":""},"logprobs":null,"finish_reason":null}]}

data: {"id":"8d101105-3bab-41db-8ad6-a7d6baa79b36","object":"chat.completion.chunk","created":1764680764,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":"Hello"},"logprobs":null,"finish_reason":null}]}

data: {"id":"8d101105-3bab-41db-8ad6-a7d6baa79b36","object":"chat.completion.chunk","created":1764680764,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":"!"},"logprobs":null,"finish_reason":null}]}

data: {"id":"8d101105-3bab-41db-8ad6-a7d6baa79b36","object":"chat.completion.chunk","created":1764680764,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":" How"},"logprobs":null,"finish_reason":null}]}

data: {"id":"8d101105-3bab-41db-8ad6-a7d6baa79b36","object":"chat.completion.chunk","created":1764680764,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":" can"},"logprobs":null,"finish_reason":null}]}

data: {"id":"8d101105-3bab-41db-8ad6-a7d6baa79b36","object":"chat.completion.chunk","created":1764680764,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":" I"},"logprobs":null,"finish_reason":null}]}

data: {"id":"8d101105-3bab-41db-8ad6-a7d6baa79b36","object":"chat.completion.chunk","created":1764680764,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":" assist"},"logprobs":null,"finish_reason":null}]}

data: {"id":"8d101105-3bab-41db-8ad6-a7d6baa79b36","object":"chat.completion.chunk","created":1764680764,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":" you"},"logprobs":null,"finish_reason":null}]}

data: {"id":"8d101105-3bab-41db-8ad6-a7d6baa79b36","object":"chat.completion.chunk","created":1764680764,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":" today"},"logprobs":null,"finish_reason":null}]}

data: {"id":"8d101105-3bab-41db-8ad6-a7d6baa79b36","object":"chat.completion.chunk","created":1764680764,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":"?"},"logprobs":null,"finish_reason":null}]}

data: {"id":"8d101105-3bab-41db-8ad6-a7d6baa79b36","object":"chat.completion.chunk","created":1764680764,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":" 😊"},"logprobs":null,"finish_reason":null}]}

data: {"id":"8d101105-3bab-41db-8ad6-a7d6baa79b36","object":"chat.completion.chunk","created":1764680764,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":""},"logprobs":null,"finish_reason":"stop"}],"usage":{"prompt_tokens":12,"completion_tokens":11,"total_tokens":23,"prompt_tokens_details":{"cached_tokens":0},"prompt_cache_hit_tokens":0,"prompt_cache_miss_tokens":12}}

data: [DONE]


```

## deepseek 

## deepseek 普通流式
```plaintext
data: {"id":"b6f14fd5-b8d2-4fad-8afd-cfd177866359","object":"chat.completion.chunk","created":1764681616,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"role":"assistant","content":""},"logprobs":null,"finish_reason":null}]}

data: {"id":"b6f14fd5-b8d2-4fad-8afd-cfd177866359","object":"chat.completion.chunk","created":1764681616,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":"Hello"},"logprobs":null,"finish_reason":null}]}

data: {"id":"b6f14fd5-b8d2-4fad-8afd-cfd177866359","object":"chat.completion.chunk","created":1764681616,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":"!"},"logprobs":null,"finish_reason":null}]}

data: {"id":"b6f14fd5-b8d2-4fad-8afd-cfd177866359","object":"chat.completion.chunk","created":1764681616,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":" How"},"logprobs":null,"finish_reason":null}]}

data: {"id":"b6f14fd5-b8d2-4fad-8afd-cfd177866359","object":"chat.completion.chunk","created":1764681616,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":" can"},"logprobs":null,"finish_reason":null}]}

data: {"id":"b6f14fd5-b8d2-4fad-8afd-cfd177866359","object":"chat.completion.chunk","created":1764681616,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":" I"},"logprobs":null,"finish_reason":null}]}

data: {"id":"b6f14fd5-b8d2-4fad-8afd-cfd177866359","object":"chat.completion.chunk","created":1764681616,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":" assist"},"logprobs":null,"finish_reason":null}]}

data: {"id":"b6f14fd5-b8d2-4fad-8afd-cfd177866359","object":"chat.completion.chunk","created":1764681616,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":" you"},"logprobs":null,"finish_reason":null}]}

data: {"id":"b6f14fd5-b8d2-4fad-8afd-cfd177866359","object":"chat.completion.chunk","created":1764681616,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":" today"},"logprobs":null,"finish_reason":null}]}

data: {"id":"b6f14fd5-b8d2-4fad-8afd-cfd177866359","object":"chat.completion.chunk","created":1764681616,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":"?"},"logprobs":null,"finish_reason":null}]}

data: {"id":"b6f14fd5-b8d2-4fad-8afd-cfd177866359","object":"chat.completion.chunk","created":1764681616,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":" 😊"},"logprobs":null,"finish_reason":null}]}

data: {"id":"b6f14fd5-b8d2-4fad-8afd-cfd177866359","object":"chat.completion.chunk","created":1764681616,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":""},"logprobs":null,"finish_reason":"stop"}],"usage":{"prompt_tokens":12,"completion_tokens":11,"total_tokens":23,"prompt_tokens_details":{"cached_tokens":0},"prompt_cache_hit_tokens":0,"prompt_cache_miss_tokens":12}}

data: [DONE]




```

## deepseek 思考流式
```plaintext
data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":null,"reasoning_content":"","role":"assistant"},"finish_reason":null}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":0,"total_tokens":294}}

data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":null,"reasoning_content":"We","role":"assistant"},"finish_reason":null}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":1,"total_tokens":295,"completion_tokens_details":{"reasoning_tokens":1}}}

......
......
data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":null,"reasoning_content":".","role":"assistant"},"finish_reason":null}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":30,"total_tokens":324,"completion_tokens_details":{"reasoning_tokens":30}}}

data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":null,"reasoning_content":" We","role":"assistant"},"finish_reason":null}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":31,"total_tokens":325,"completion_tokens_details":{"reasoning_tokens":31}}}

data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":null,"reasoning_content":" can","role":"assistant"},"finish_reason":null}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":32,"total_tokens":326,"completion_tokens_details":{"reasoning_tokens":32}}}

data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":null,"reasoning_content":" simply","role":"assistant"},"finish_reason":null}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":33,"total_tokens":327,"completion_tokens_details":{"reasoning_tokens":33}}}

data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":null,"reasoning_content":" respond","role":"assistant"},"finish_reason":null}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":34,"total_tokens":328,"completion_tokens_details":{"reasoning_tokens":34}}}

data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":null,"reasoning_content":" with","role":"assistant"},"finish_reason":null}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":35,"total_tokens":329,"completion_tokens_details":{"reasoning_tokens":35}}}

data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":null,"reasoning_content":" a","role":"assistant"},"finish_reason":null}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":36,"total_tokens":330,"completion_tokens_details":{"reasoning_tokens":36}}}

data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":null,"reasoning_content":" greeting","role":"assistant"},"finish_reason":null}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":37,"total_tokens":331,"completion_tokens_details":{"reasoning_tokens":37}}}

data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":null,"reasoning_content":".\n","role":"assistant"},"finish_reason":null}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":38,"total_tokens":332,"completion_tokens_details":{"reasoning_tokens":38}}}

data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":"\n","reasoning_content":null,"role":"assistant"},"finish_reason":null}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":40,"total_tokens":334,"completion_tokens_details":{"reasoning_tokens":38}}}

data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":"Hello","reasoning_content":null,"role":"assistant"},"finish_reason":null}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":41,"total_tokens":335,"completion_tokens_details":{"reasoning_tokens":38}}}

data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":"!","reasoning_content":null,"role":"assistant"},"finish_reason":null}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":42,"total_tokens":336,"completion_tokens_details":{"reasoning_tokens":38}}}

data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":" How","reasoning_content":null,"role":"assistant"},"finish_reason":null}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":43,"total_tokens":337,"completion_tokens_details":{"reasoning_tokens":38}}}

data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":" can","reasoning_content":null,"role":"assistant"},"finish_reason":null}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":44,"total_tokens":338,"completion_tokens_details":{"reasoning_tokens":38}}}

data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":" I","reasoning_content":null,"role":"assistant"},"finish_reason":null}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":45,"total_tokens":339,"completion_tokens_details":{"reasoning_tokens":38}}}

data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":" assist","reasoning_content":null,"role":"assistant"},"finish_reason":null}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":46,"total_tokens":340,"completion_tokens_details":{"reasoning_tokens":38}}}

data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":" you","reasoning_content":null,"role":"assistant"},"finish_reason":null}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":47,"total_tokens":341,"completion_tokens_details":{"reasoning_tokens":38}}}

data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":" today","reasoning_content":null,"role":"assistant"},"finish_reason":null}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":48,"total_tokens":342,"completion_tokens_details":{"reasoning_tokens":38}}}

data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":"?","reasoning_content":null,"role":"assistant"},"finish_reason":null}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":49,"total_tokens":343,"completion_tokens_details":{"reasoning_tokens":38}}}

data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":" 😊","reasoning_content":null,"role":"assistant"},"finish_reason":null}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":51,"total_tokens":345,"completion_tokens_details":{"reasoning_tokens":38}}}

data: {"id":"019adf32d7eba732fab10a923ab85c0b","object":"chat.completion.chunk","created":1764681242,"model":"deepseek-ai/DeepSeek-R1","choices":[{"index":0,"delta":{"content":"","reasoning_content":null},"finish_reason":"stop"}],"system_fingerprint":"","usage":{"prompt_tokens":294,"completion_tokens":51,"total_tokens":345,"completion_tokens_details":{"reasoning_tokens":38}}}

data: [DONE]


```

## deepseek 函数调用流式
```plaintext
data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"role":"assistant","content":""},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":"我可以"},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":"帮"},"logprobs":null,"finish_reason":null}]}
......
......
data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":"。"},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_00_JQSxFX1xOHGH6ajdpN88RMXO","type":"function","function":{"name":"get_current_weather","arguments":""}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{"}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\""}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"location"}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\""}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":": "}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\""}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"北京"}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\""}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":", "}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\""}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"format"}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\""}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":": "}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\""}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"c"}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"elsius"}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\""}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"}"}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"id":"call_01_L8UtYbkfaMFr7hzsToGPWGfI","type":"function","function":{"name":"get_current_weather","arguments":""}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"{"}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"\""}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"location"}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"\""}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":": "}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"\""}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"洛阳"}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"\""}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":", "}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"\""}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"format"}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"\""}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":": "}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"\""}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"c"}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"elsius"}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"\""}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"}"}}]},"logprobs":null,"finish_reason":null}]}

data: {"id":"750800d8-716c-40da-ba87-c46db32cf8a3","object":"chat.completion.chunk","created":1764681305,"model":"deepseek-chat","system_fingerprint":"fp_eaab8d114b_prod0820_fp8_kvcache","choices":[{"index":0,"delta":{"content":""},"logprobs":null,"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":491,"completion_tokens":129,"total_tokens":620,"prompt_tokens_details":{"cached_tokens":448},"prompt_cache_hit_tokens":448,"prompt_cache_miss_tokens":43}}

data: [DONE]


```


# 腾讯混元

## 普通流式
```plaintext
data: {"id":"6668ee7ecb4b55bd5bce532287c97298","object":"chat.completion.chunk","created":1764682242,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":"Got","reasoning_content":""},"finish_reason":null}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: {"id":"6668ee7ecb4b55bd5bce532287c97298","object":"chat.completion.chunk","created":1764682242,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":" it","reasoning_content":""},"finish_reason":null}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: {"id":"6668ee7ecb4b55bd5bce532287c97298","object":"chat.completion.chunk","created":1764682242,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":",","reasoning_content":""},"finish_reason":null}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: {"id":"6668ee7ecb4b55bd5bce532287c97298","object":"chat.completion.chunk","created":1764682242,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":" I","reasoning_content":""},"finish_reason":null}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: {"id":"6668ee7ecb4b55bd5bce532287c97298","object":"chat.completion.chunk","created":1764682242,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":"'m","reasoning_content":""},"finish_reason":null}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: {"id":"6668ee7ecb4b55bd5bce532287c97298","object":"chat.completion.chunk","created":1764682242,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":" ready","reasoning_content":""},"finish_reason":null}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: {"id":"6668ee7ecb4b55bd5bce532287c97298","object":"chat.completion.chunk","created":1764682242,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":" to","reasoning_content":""},"finish_reason":null}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: {"id":"6668ee7ecb4b55bd5bce532287c97298","object":"chat.completion.chunk","created":1764682242,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":" answer","reasoning_content":""},"finish_reason":null}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: {"id":"6668ee7ecb4b55bd5bce532287c97298","object":"chat.completion.chunk","created":1764682242,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":" your","reasoning_content":""},"finish_reason":null}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: {"id":"6668ee7ecb4b55bd5bce532287c97298","object":"chat.completion.chunk","created":1764682242,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":" questions","reasoning_content":""},"finish_reason":null}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: {"id":"6668ee7ecb4b55bd5bce532287c97298","object":"chat.completion.chunk","created":1764682242,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":"!","reasoning_content":""},"finish_reason":null}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: {"id":"6668ee7ecb4b55bd5bce532287c97298","object":"chat.completion.chunk","created":1764682242,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":" How","reasoning_content":""},"finish_reason":null}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: {"id":"6668ee7ecb4b55bd5bce532287c97298","object":"chat.completion.chunk","created":1764682242,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":" can","reasoning_content":""},"finish_reason":null}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: {"id":"6668ee7ecb4b55bd5bce532287c97298","object":"chat.completion.chunk","created":1764682242,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":" I","reasoning_content":""},"finish_reason":null}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: {"id":"6668ee7ecb4b55bd5bce532287c97298","object":"chat.completion.chunk","created":1764682242,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":" assist","reasoning_content":""},"finish_reason":null}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: {"id":"6668ee7ecb4b55bd5bce532287c97298","object":"chat.completion.chunk","created":1764682242,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":" you","reasoning_content":""},"finish_reason":null}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: {"id":"6668ee7ecb4b55bd5bce532287c97298","object":"chat.completion.chunk","created":1764682242,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":" today","reasoning_content":""},"finish_reason":null}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: {"id":"6668ee7ecb4b55bd5bce532287c97298","object":"chat.completion.chunk","created":1764682242,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":"?","reasoning_content":""},"finish_reason":null}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: {"id":"6668ee7ecb4b55bd5bce532287c97298","object":"chat.completion.chunk","created":1764682242,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":"","reasoning_content":""},"finish_reason":"stop"}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: [DONE]


```

## 函数调用流式
```plaintext
data: {"id":"d34f9bb375ea37b0c67b8a9a0303ff09","object":"chat.completion.chunk","created":1764682362,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":"","reasoning_content":"","tool_calls":[{"id":"call_d4nekv42c3mas4ig9k30","index":0,"type":"function","function":{"name":"get_current_weather","arguments":""}}]},"finish_reason":null}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: {"id":"d34f9bb375ea37b0c67b8a9a0303ff09","object":"chat.completion.chunk","created":1764682362,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":"","reasoning_content":"","tool_calls":[{"id":"call_d4nekv42c3mas4ig9k30","index":0,"type":"function","function":{"name":"","arguments":"{\"format\":\"celsius\",\"location\":\"北京\"}"}}]},"finish_reason":null}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: {"id":"d34f9bb375ea37b0c67b8a9a0303ff09","object":"chat.completion.chunk","created":1764682362,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":"我们将使用get_current_weather工具来获取北京的天气，然后使用get_history_weather工具来获取洛阳的天气。\n\t\n\t用户想要获取北京和洛阳的当前天气情况，并且指定了温度单位为摄氏度。根据用户的请求，我们需要调用两个工具来分别获取北京和洛阳的当前天气情况。由于用户没有指定时间点，我们可以默认获取当前的天气情况。","reasoning_content":""},"finish_reason":null}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: {"id":"d34f9bb375ea37b0c67b8a9a0303ff09","object":"chat.completion.chunk","created":1764682362,"model":"hunyuan-lite","system_fingerprint":"","choices":[{"index":0,"delta":{"role":"assistant","content":"","reasoning_content":""},"finish_reason":"tool_calls"}],"note":"以上内容为AI生成，不代表开发者立场，请勿删除或修改本标记"}

data: [DONE]


```

# 豆包

## 普通流式
```plaintext
data: {"choices":[{"delta":{"content":"我","role":"assistant"},"index":0}],"created":1764683408,"id":"021764683408123345ac2cd750e626cbfa451e1fd38946f410eef","model":"doubao-seed-1-6-251015","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"是","role":"assistant"},"index":0}],"created":1764683408,"id":"021764683408123345ac2cd750e626cbfa451e1fd38946f410eef","model":"doubao-seed-1-6-251015","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"豆","role":"assistant"},"index":0}],"created":1764683408,"id":"021764683408123345ac2cd750e626cbfa451e1fd38946f410eef","model":"doubao-seed-1-6-251015","service_tier":"default","object":"chat.completion.chunk","usage":null}
......
......
data: {"choices":[{"delta":{"content":"～","role":"assistant"},"index":0}],"created":1764683408,"id":"021764683408123345ac2cd750e626cbfa451e1fd38946f410eef","model":"doubao-seed-1-6-251015","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","role":"assistant"},"finish_reason":"stop","index":0}],"created":1764683408,"id":"021764683408123345ac2cd750e626cbfa451e1fd38946f410eef","model":"doubao-seed-1-6-251015","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: [DONE]


```

## 思考流式
```plaintext
data: {"choices":[{"delta":{"content":"","reasoning_content":"","role":"assistant"},"index":0}],"created":1764682769,"id":"021764682769202345ac2cd750e626cbfa451e1fd38946fb929c1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"用户","role":"assistant"},"index":0}],"created":1764682769,"id":"021764682769202345ac2cd750e626cbfa451e1fd38946fb929c1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}
......
......
data: {"choices":[{"delta":{"content":"","reasoning_content":"清晰","role":"assistant"},"index":0}],"created":1764682769,"id":"021764682769202345ac2cd750e626cbfa451e1fd38946fb929c1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"了","role":"assistant"},"index":0}],"created":1764682769,"id":"021764682769202345ac2cd750e626cbfa451e1fd38946fb929c1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"。","role":"assistant"},"index":0}],"created":1764682769,"id":"021764682769202345ac2cd750e626cbfa451e1fd38946fb929c1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"这","role":"assistant"},"index":0}],"created":1764682769,"id":"021764682769202345ac2cd750e626cbfa451e1fd38946fb929c1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}
......
......
data: {"choices":[{"delta":{"content":"治愈","role":"assistant"},"index":0}],"created":1764682769,"id":"021764682769202345ac2cd750e626cbfa451e1fd38946fb929c1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"。","role":"assistant"},"index":0}],"created":1764682769,"id":"021764682769202345ac2cd750e626cbfa451e1fd38946fb929c1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"）","role":"assistant"},"index":0}],"created":1764682769,"id":"021764682769202345ac2cd750e626cbfa451e1fd38946fb929c1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","role":"assistant"},"finish_reason":"stop","index":0}],"created":1764682769,"id":"021764682769202345ac2cd750e626cbfa451e1fd38946fb929c1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: [DONE]



```

## 函数调用流式
```plaintext
data: {"choices":[{"delta":{"content":"","reasoning_content":"","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"用户","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"需要","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"获取","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"北京","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"和","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"洛阳","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"的","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"当前","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"天气","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"，","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"用","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"摄氏度","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"。","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"首先","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"看","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"提供","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"的","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"函数","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"，","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"get","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"_current","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"_","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"weather","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"可以","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"获取","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"当前","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"天气","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"，","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"参数","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"是","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"location","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"和","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"format","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"。","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"那","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"需要","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"调用","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"两次","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"这个","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"函数","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"，","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"分别","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"传入","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"北京","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"和","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"洛阳","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"，","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"format","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"都是","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"c","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"elsius","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"。","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"所以","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"构造","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"两个","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"函数","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"调用","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"，","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"放在","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"一个","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"seed","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":":","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"tool","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"_call","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"里","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"。","role":"assistant"},"index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: {"choices":[{"delta":{"content":"","reasoning_content":"","role":"assistant","tool_calls":[{"function":{"arguments":"{\"location\": \"北京\", \"format\": \"celsius\"}","name":"get_current_weather"},"id":"call_pwdm5g0i3dwe825aghkb1t8j","index":0,"type":"function"},{"function":{"arguments":"{\"location\": \"洛阳\", \"format\": \"celsius\"}","name":"get_current_weather"},"id":"call_k6mgf4zrsf3hh51lxl67gjgg","index":1,"type":"function"}]},"finish_reason":"tool_calls","index":0}],"created":1764682994,"id":"021764682994508345ac2cd750e626cbfa451e1fd38946f9675a1","model":"doubao-seed-code-preview-251028","service_tier":"default","object":"chat.completion.chunk","usage":null}

data: [DONE]


```

这个函数调用直接tool_calls包含全部了。


