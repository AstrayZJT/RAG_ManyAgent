# InsightFlow 代码配合讲解

这份文档不是路线图，而是按源码顺序，把 InsightFlow 的主链路、状态、节点、工具、checkpoint、表和前端串起来讲。
你可以一边开着代码，一边对照这份文档看。

先记住一句话：

**这个项目不是“一个 LLM 聊天应用”，而是“图驱动的调研流水线”。**

## 1. 入口从哪儿进

### 1.1 Spring Boot 启动

看 [InsightFlowApplication.java](../src/main/java/com/astray/insightflow/InsightFlowApplication.java)：

```java
@SpringBootApplication
@EnableAsync
@ConfigurationPropertiesScan
public class InsightFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(InsightFlowApplication.class, args);
    }
}
```

这里很简单，但有两个关键点：

1. `@EnableAsync` 让任务能异步执行，API 返回后图继续跑。
2. `@ConfigurationPropertiesScan` 让 `application.yml` 里的配置类自动生效。

### 1.2 配置从哪儿来

看 [application.yml](../src/main/resources/application.yml) 和 [application-postgres.yml](../src/main/resources/application-postgres.yml)。

你要重点记住这些配置：

- `langchain4j.open-ai.chat-model.*`：模型地址、key、模型名、温度、超时。
- `insightflow.workflow.*`：回退阈值、最大循环数、claim 数量要求。
- `agent.search.*`：搜索结果上限、网页抓取字符上限。
- `rag.*`：知识库路径、pgvector 参数。
- `infrastructure.*`：Redis / MinIO / RabbitMQ 的开关。

当前代码默认模型名是 `glm-5`，如果没配 API Key，就会走 stub agent。

看 [LangChainConfig.java](../src/main/java/com/astray/insightflow/config/LangChainConfig.java)：

```java
if (!StringUtils.hasText(properties.apiKey())) {
    return new StubPlannerAgent();
}
return AiServices.builder(PlannerAgent.class)
        .chatModel(buildChatModel(properties))
        .build();
```

这段就是“真模型”和“假实现”的切换点。

## 2. 请求是怎么进来的

### 2.1 创建任务

看 [TaskController.java](../src/main/java/com/astray/insightflow/task/api/TaskController.java) 的 `createTask`：

```java
@PostMapping
public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
    String language = StringUtils.hasText(request.language()) ? request.language() : "zh-CN";
    ResearchTask task = taskService.createTask(request.query().trim(), language.trim());
    return ResponseEntity.ok(TaskResponse.from(task));
}
```

你可以把它理解成：

1. 前端提交研究问题。
2. 后端创建一条 `research_task`。
3. 返回任务 id。

任务表本身不存大段内容，只存“这是什么任务、状态如何、什么时候开始、什么时候结束、错没错”。

### 2.2 启动执行

`runTask` 会调用：

```java
taskGraphExecutor.executeAsync(taskId);
```

然后马上返回 `ACCEPTED`。

这里的意思是：

- API 不会卡着等整个调研结束。
- 真正执行在后台异步线程里。
- 前端再通过 SSE / timeline / report 去看进度。

### 2.3 恢复和重跑

`resumeTask` 和 `rerunTask` 很关键：

- `resume`：从 checkpoint 继续。
- `rerun`：从某个节点前面的 checkpoint 重跑。

这里有个重要细节：

```java
Map<String, Object> statePatch = request == null || request.statePatch() == null ? Map.of() : request.statePatch();
```

也就是说，恢复前你可以手动补一个 `statePatch`，把修正后的状态合进去再继续跑。

## 3. 任务数据怎么落库

### 3.1 任务主表

看 [ResearchTask.java](../src/main/java/com/astray/insightflow/task/domain/ResearchTask.java)：

- `id`：任务 id。
- `queryText`：研究问题。
- `language`：输出语言。
- `status`：CREATED / RUNNING / COMPLETED / FAILED。
- `errorMessage`：失败原因。
- `createdAt / updatedAt / startedAt / completedAt`：时间戳。

看 [TaskService.java](../src/main/java/com/astray/insightflow/task/service/TaskService.java)：

- `createTask`：创建任务。
- `markRunning`：开始执行。
- `markCompleted`：结束成功。
- `markFailed`：结束失败。

也就是说，`research_task` 只管任务生命周期，不管细节结果。

### 3.2 计划表

看 [TaskPlanEntity.java](../src/main/java/com/astray/insightflow/task/domain/TaskPlanEntity.java)：

- `taskId`：一条任务对应一条计划。
- `planJson`：Planner 的结构化结果。

Planner 节点跑完后，会把计划写进这张表。

## 4. 图是怎么搭起来的

看 [ResearchGraphBuilder.java](../src/main/java/com/astray/insightflow/graph/ResearchGraphBuilder.java)：

```java
StateGraph<ResearchState> stateGraph = new StateGraph<>(ResearchState.SCHEMA, researchStateFactory)
        .addNode(PLANNER, node_async((state, config) -> plannerNode.execute(state)))
        .addNode(EXTRACT, node_async((state, config) -> extractNode.execute(state)))
        .addNode(VERIFY, node_async((state, config) -> verifyNode.execute(state)))
        .addNode(WRITE, node_async((state, config) -> writeNode.execute(state)))
        .addNode(REVIEW, node_async((state, config) -> reviewNode.execute(state)))
        .addEdge(START, PLANNER);
```

这段代码说明三件事：

1. 图的每个节点其实是一个 Java 类。
2. 节点执行时拿到的是 `ResearchState`。
3. 图是从 `START -> planner` 开始的。

然后它把检索子图接进去：

```java
retrievalSubgraphBuilder.attach(stateGraph, PLANNER, EXTRACT)
```

意思是：

- `planner` 后面不是直接 `extract`。
- 中间先跑检索子图。
- 检索子图结束后再进入 `extract`。

最后编译时挂上 checkpoint：

```java
return stateGraph.compile(CompileConfig.builder()
        .checkpointSaver(checkpointSaver)
        .releaseThread(false)
        .build());
```

这里的意图很明显：

- 要能自动保存中间状态。
- 要能恢复、重跑。
- 要保留 thread 语义，不让长任务丢上下文。

## 5. 状态为什么要单独设计

看 [ResearchState.java](../src/main/java/com/astray/insightflow/graph/state/ResearchState.java)。

它不是普通 DTO，而是整条图的共享上下文。

最核心的字段是这些：

- `taskId`
- `userQuery`
- `language`
- `plan`
- `subQueries`
- `needExternalSearch`
- `internalEvidences`
- `externalEvidences`
- `mergedEvidences`
- `facts`
- `claims`
- `verifyDecision`
- `reportDraft`
- `reviewResult`
- `loopCount`
- `status`
- `metrics`
- `timeline`

### 5.1 state 怎么被写进去

每个节点跑完，不是直接改数据库，而是返回一个 `Map<String, Object>`。

比如 `PlannerNode` 最后会返回：

```java
output.put(ResearchState.PLAN, plan);
output.put(ResearchState.SUB_QUERIES, plan.getSubQueries());
output.put(ResearchState.NEED_EXTERNAL_SEARCH, plan.isNeedExternalSearch());
output.put(ResearchState.LOOP_COUNT, 0);
output.put(ResearchState.STATUS, "PLANNED");
```

这就是“写入 state”。

图框架会把它合并进共享状态，然后 checkpoint 会把最新状态持久化。

### 5.2 为什么 metrics 和 timeline 特别处理

在 `ResearchState.SCHEMA` 里：

- 大部分字段是覆盖式更新。
- `metrics` 和 `timeline` 用 merge。

意思是：

- 你要看最新值。
- 也要保留多轮执行痕迹。

这很适合调研任务，因为你既要结果，也要过程。

## 6. 每个节点到底做什么

### 6.1 PlannerNode

看 [PlannerNode.java](../src/main/java/com/astray/insightflow/graph/node/PlannerNode.java)。

它做的事：

1. 调 `PlannerAgent` 生成 `PlanResult`。
2. 用 `PlannerPlanTemplates.normalize(...)` 做归一化。
3. 把 plan 存进 `task_plan`。
4. 初始化 metrics、timeline、loopCount、status。
5. 发进度事件。

你读它时要抓住一句话：

**Planner 不是泛泛地总结，而是把问题拆成后续节点可执行的结构化计划。**

`PlanResult` 里最重要的是：

- `objectiveSummary`
- `dimensions`
- `subQueries`
- `retrievalStrategy`
- `factSchema`
- `needExternalSearch`

`PlannerPlanTemplates` 还会做 fallback。

也就是说，LLM 输出太泛、太空、没贴题时，这里会自动补救。

### 6.2 RetrievalDispatchNode

看 [RetrievalDispatchNode.java](../src/main/java/com/astray/insightflow/graph/node/RetrievalDispatchNode.java)。

它不检索，只做分发：

- 判断是否要外部搜索。
- 记录并行分支数。
- 把状态推进到 `RETRIEVAL_DISPATCHED`。

这个节点的作用是让“要不要并行检索”从检索逻辑里拆出来。

### 6.3 RetrieveInternalNode

看 [RetrieveInternalNode.java](../src/main/java/com/astray/insightflow/graph/node/RetrieveInternalNode.java)。

它调用的是 [KbSearchTool.java](../src/main/java/com/astray/insightflow/tool/KbSearchTool.java)：

```java
List<Evidence> evidences = kbSearchTool.search(taskId, "retrieveInternal", state.subQueries());
```

内部检索现在做的不是向量检索，而是：

1. 遍历所有 chunk。
2. 按 query 词项匹配打分。
3. 加标题加权。
4. 排序取 topK。
5. 存入 `evidence_record`。

所以你读这块时别误会成“已经是完整向量库 RAG 了”。
配置里有 `pgvector`，但当前主逻辑还是工程化词项检索。

### 6.4 RetrieveExternalNode

看 [RetrieveExternalNode.java](../src/main/java/com/astray/insightflow/graph/node/RetrieveExternalNode.java)。

它调用 [WebSearchTool.java](../src/main/java/com/astray/insightflow/tool/WebSearchTool.java)。

外部检索链路大致是：

1. 搜索引擎召回。
2. 去重。
3. 抓网页正文。
4. 清洗 HTML。
5. 过滤低相关内容。
6. 存入 `evidence_record`。

`WebFetchTool` 会把 `script/style/nav/footer/aside` 这些噪音去掉，再抽主内容。

### 6.5 MergeRerankNode

看 [MergeRerankNode.java](../src/main/java/com/astray/insightflow/graph/node/MergeRerankNode.java)。

它做两件事：

1. 把内部和外部证据合并。
2. 用 [RerankTool.java](../src/main/java/com/astray/insightflow/tool/RerankTool.java) 去重和重排。

这个节点很重要，因为后面抽取、验证、写作都依赖它输出的“干净证据池”。

### 6.6 ExtractNode

看 [ExtractNode.java](../src/main/java/com/astray/insightflow/graph/node/ExtractNode.java)。

它的任务是把证据变成结构化事实。

主流程：

```java
result = extractorAgent.extract(state.userQuery(), state.language(), planJson, evidenceJson);
```

如果 LLM 出错或者返回空，就会 fallback：

```java
new StubExtractorAgent(jsonUtils).extract(...)
```

然后再走 [FactNormalizeTool.java](../src/main/java/com/astray/insightflow/tool/FactNormalizeTool.java) 做清洗。

最终 facts 会写进：

- `facts`（state）
- `extracted_fact`（数据库）

你要把这一层理解成“从证据里抽结构化字段”，不是“写解释性文字”。

### 6.7 VerifyNode

看 [VerifyNode.java](../src/main/java/com/astray/insightflow/graph/node/VerifyNode.java)。

它做的是 claim 级交叉验证。

流程是：

1. 把 facts 和 evidence 转成 JSON。
2. 调 `VerifierAgent.verify(...)`。
3. 调 `CitationTool.attach(...)` 给 claim 绑定证据。
4. 调 `TrustScoreTool.scoreBatch(...)` 给证据算可信度。
5. 根据 claim 数量、置信度、引用覆盖率、冲突数决定能不能写。

核心判断逻辑大概是：

- claim 数量够不够。
- 平均置信度够不够。
- 引用覆盖率够不够。
- 冲突是不是为 0。

然后它会把结果塞进 `verifyDecision`，交给 `VerifyRouteDecider` 路由。

### 6.8 WriteNode

看 [WriteNode.java](../src/main/java/com/astray/insightflow/graph/node/WriteNode.java)。

它基于已验证的 claims 生成报告草稿。

输入是：

- `plan`
- `claims`
- `mergedEvidences`

输出是：

- `reportDraft`

并保存到 `final_report`。

`ReportDraft` 里包含：

- 标题
- 执行摘要
- 各章节 sections
- 结论
- 置信度说明
- 审查摘要

这一步不是最终定稿，因为后面还有 `ReviewNode`。

### 6.9 ReviewNode

看 [ReviewNode.java](../src/main/java/com/astray/insightflow/graph/node/ReviewNode.java)。

它的职责是审查报告能不能交付。

它会检查：

- 有没有缺引用。
- 有没有未支撑的 claim。
- 有没有低置信度章节。

然后给出三个方向：

- `approved = true`，结束。
- `rerunFrom = RETRIEVAL`，回检索。
- `rerunFrom = VERIFY`，回验证。

这里还有一个很实用的回退保护：

如果 `loopCount` 太高，它会允许以低置信度模式结束，避免死循环。

## 7. 条件路由怎么理解

### 7.1 VerifyRouteDecider

看 [VerifyRouteDecider.java](../src/main/java/com/astray/insightflow/graph/router/VerifyRouteDecider.java)。

逻辑很直接：

- `readyForWrite = true` -> 去 `write`
- `loopCount > maxLoops` -> 也去 `write`
- `rerunRetrieval = true` -> 回 `retrievalStart`

### 7.2 ReviewRouteDecider

看 [ReviewRouteDecider.java](../src/main/java/com/astray/insightflow/graph/router/ReviewRouteDecider.java)。

逻辑是：

- `approved = true` -> `END`
- `rerunFrom = RETRIEVAL` -> 回 `retrievalStart`
- `rerunFrom = VERIFY` -> 回 `verify`

### 7.3 一个小提醒

`InternalRouteDecider` 这个类现在存在，但主图里还没真正挂上。
你看源码时别被它带偏，它更像预留的路由策略。

## 8. 工具层是怎么工作的

工具层都在 [tool](../src/main/java/com/astray/insightflow/tool) 包下。

### 8.1 KbSearchTool

内部知识库检索入口。

### 8.2 WebSearchTool

外部网页召回入口。

### 8.3 WebFetchTool

负责抓网页正文、清洗噪音内容、截断长度。

### 8.4 RerankTool

负责去重、按 score 排序、控制输出数量。

### 8.5 CitationTool

负责把 claim 绑定到 evidence id。

### 8.6 TrustScoreTool

负责给证据来源算可信度。

### 8.7 FactNormalizeTool

负责清洗 fact 字段里的空格和格式。

这几个工具都标了 `@Tool`，所以既能给 LangChain4j 的 agent 用，也能在节点里直接调用。

## 9. checkpoint 为什么能恢复

看 [DatabaseCheckpointSaver.java](../src/main/java/com/astray/insightflow/graph/checkpoint/DatabaseCheckpointSaver.java)。

它保存的不是节点，而是：

- 当前 `ResearchState`
- 当前节点 id
- 下一节点 id
- checkpoint id

关键代码是：

```java
meta.setStateJson(researchStateFactory.toStateJson(checkpoint.getState()));
meta.setStateSummaryJson(researchStateFactory.toStateJson(researchStateFactory.summarize(checkpoint.getState())));
```

也就是说：

1. 完整 state 存一份。
2. 摘要 state 再存一份，方便列表展示。

`CheckpointService` 负责三件事：

- `latest(taskId)`：找最新 checkpoint。
- `get(taskId, checkpointId)`：按 id 精确找。
- `snapshotBeforeNode(taskId, nodeName)`：找某节点之前的快照。

`TaskGraphExecutor` 里的恢复逻辑最关键：

```java
CheckpointService.PreparedExecution preparedExecution = checkpointService.prepareResume(taskId, checkpointId, statePatch);
compiledGraph.invoke((Map<String, Object>) null, preparedExecution.runnableConfig())
```

这说明：

- 恢复不是从头跑。
- 是从 checkpoint 里的下一节点继续跑。

### 9.1 statePatch 是干什么的

它允许你在恢复前手工补状态。
比如你修正了某些字段，就先补进 state，再继续执行。

### 9.2 resume 和 rerun 的区别

- `resume`：接着上次中断的地方跑。
- `rerun`：从某节点之前的状态重新跑。

## 10. 观测层怎么看

### 10.1 进度流

看 [TaskProgressPublisher.java](../src/main/java/com/astray/insightflow/task/service/TaskProgressPublisher.java)。

它做的是内存里的 SSE 事件发布：

- `publish(...)`：发一个进度事件。
- `subscribe(taskId)`：前端连 SSE。
- `history(taskId)`：读历史事件。

这就是为什么你在前端能实时看到节点推进。

### 10.2 节点日志

看 [AgentRunLogService.java](../src/main/java/com/astray/insightflow/observe/service/AgentRunLogService.java) 和 [ToolCallLogService.java](../src/main/java/com/astray/insightflow/observe/service/ToolCallLogService.java)。

它们分别记录：

- 节点运行耗时、输出、metrics。
- 工具调用输入、输出、metrics。

### 10.3 时间线

看 [TaskTimelineService.java](../src/main/java/com/astray/insightflow/observe/service/TaskTimelineService.java)。

它会把这些东西拼起来：

- SSE 历史事件
- agent run logs
- tool call logs
- checkpoints
- evaluation

所以 `/timeline` 不是单纯的日志，而是任务的完整侧写。

### 10.4 评测

看 [EvaluationService.java](../src/main/java/com/astray/insightflow/eval/service/EvaluationService.java)。

它会综合算：

- 检索命中率
- citation coverage
- claim support rate
- report completeness
- overall score

最终存进 `evaluation_record`。

## 11. 表设计怎么对应代码

你可以按下面理解：

- `research_task`：任务壳。
- `task_plan`：Planner 输出。
- `knowledge_document`：上传文档元数据。
- `document_chunk`：切片后的文档内容。
- `evidence_record`：内外检索证据。
- `extracted_fact`：抽取事实。
- `verified_claim`：验证后的 claims。
- `final_report`：最终报告。
- `agent_run_log`：节点日志。
- `tool_call_log`：工具调用日志。
- `graph_checkpoint_meta`：图恢复快照。
- `evaluation_record`：评测结果。

这套表的核心思路是：

**任务是任务，计划是计划，证据是证据，事实是事实，claim 是 claim，报告是报告，日志是日志，checkpoint 是 checkpoint。**

## 12. 前端怎么和后端对应

看 [src/main/resources/static/index.html](../src/main/resources/static/index.html)、[app.js](../src/main/resources/static/app.js)、[styles.css](../src/main/resources/static/styles.css)。

它就是一个控制台，核心动作是：

- 创建任务。
- 运行任务。
- 上传文档。
- 索引文档。
- 看 report。
- 看 timeline。
- 看 checkpoint。
- 做 resume / rerun。
- 做 evaluate。

前端本地还会记：

- 当前任务 id
- 当前文档 id
- 当前 tab
- 恢复点
- 回退节点
- state patch

所以你在页面上看到的不是“展示页”，而是一个任务调度面板。

## 13. 你读代码时最容易误会的地方

1. 以为 agent 决定全局流程。其实不是，图路由才决定。
2. 以为 checkpoint 记录节点对象。其实记录的是 state。
3. 以为内部检索已经是完整向量库。其实现在主要是词项打分 + 排序。
4. 以为所有中间结果都在数据库里。其实很多是先在 state 里流转。
5. 以为 SSE 是唯一状态源。其实 SSE 只是展示，真实记录还在日志表和 checkpoint 表。

## 14. 一条完整链路你应该怎么在代码里跟

你可以按这个顺序逐行读：

1. `TaskController.createTask`
2. `TaskController.runTask`
3. `TaskGraphExecutor.execute`
4. `ResearchGraphBuilder.build`
5. `PlannerNode.execute`
6. `RetrievalDispatchNode.execute`
7. `RetrieveInternalNode.execute`
8. `RetrieveExternalNode.execute`
9. `MergeRerankNode.execute`
10. `ExtractNode.execute`
11. `VerifyNode.execute`
12. `WriteNode.execute`
13. `ReviewNode.execute`
14. `DatabaseCheckpointSaver.saveCheckpoint`
15. `TaskTimelineService.getTimeline`
16. `EvaluationService.evaluate`

如果你把这 16 步吃透，这个项目基本就通了。

