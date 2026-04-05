# ai4j FlowGram Demo

## Run

```powershell
$env:ZHIPU_API_KEY="your-key"
cmd /c "mvn -pl ai4j-flowgram-demo -am -DskipTests package"
java -jar ai4j-flowgram-demo/target/ai4j-flowgram-demo-2.1.0.jar
```

The demo exposes FlowGram REST APIs under `/flowgram`.

## Quick Check

```powershell
$body = @{
  schema = @{
    nodes = @(
      @{
        id = "start_0"
        type = "Start"
        name = "start_0"
        data = @{
          outputs = @{
            type = "object"
            required = @("message")
            properties = @{
              message = @{ type = "string" }
            }
          }
        }
      },
      @{
        id = "end_0"
        type = "End"
        name = "end_0"
        data = @{
          inputs = @{
            type = "object"
            required = @("result")
            properties = @{
              result = @{ type = "string" }
            }
          }
          inputsValues = @{
            result = @{
              type = "ref"
              content = @("start_0", "message")
            }
          }
        }
      }
    )
    edges = @(
      @{
        sourceNodeID = "start_0"
        targetNodeID = "end_0"
      }
    )
  }
  inputs = @{
    message = "hello-flowgram"
  }
} | ConvertTo-Json -Depth 10

Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:18080/flowgram/tasks/run" -ContentType "application/json" -Body $body
```

## Real LLM Example

```powershell
$body = @{
  schema = @{
    nodes = @(
      @{
        id = "start_0"
        type = "Start"
        name = "start_0"
        data = @{
          outputs = @{
            type = "object"
            required = @("message")
            properties = @{
              message = @{ type = "string" }
            }
          }
        }
      },
      @{
        id = "llm_0"
        type = "LLM"
        name = "llm_0"
        data = @{
          inputs = @{
            type = "object"
            required = @("modelName", "prompt")
            properties = @{
              modelName = @{ type = "string" }
              prompt = @{ type = "string" }
            }
          }
          outputs = @{
            type = "object"
            required = @("result")
            properties = @{
              result = @{ type = "string" }
            }
          }
          inputsValues = @{
            modelName = @{
              type = "constant"
              content = "glm-4.7"
            }
            prompt = @{
              type = "ref"
              content = @("start_0", "message")
            }
          }
        }
      },
      @{
        id = "end_0"
        type = "End"
        name = "end_0"
        data = @{
          inputs = @{
            type = "object"
            required = @("result")
            properties = @{
              result = @{ type = "string" }
            }
          }
          inputsValues = @{
            result = @{
              type = "ref"
              content = @("llm_0", "result")
            }
          }
        }
      }
    )
    edges = @(
      @{
        sourceNodeID = "start_0"
        targetNodeID = "llm_0"
      },
      @{
        sourceNodeID = "llm_0"
        targetNodeID = "end_0"
      }
    )
  }
  inputs = @{
    message = "Please answer with exactly three words: FlowGram spring boot."
  }
} | ConvertTo-Json -Depth 12

$run = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:18080/flowgram/tasks/run" -ContentType "application/json" -Body $body
$result = Invoke-RestMethod -Method Get -Uri ("http://127.0.0.1:18080/flowgram/tasks/" + $run.taskId + "/result")
```

On a verified local run with `glm-4.7`, the workflow completed with:

```json
{
  "status": "success",
  "result": "FlowGram spring boot"
}
```

