# InsightFlow 按用户输入的完整代码阅读

这份文档按照一次研究任务的实际执行顺序阅读代码。先记住三个核心对象：

- `ResearchTask`：数据库中的任务主记录。
- `ResearchState`：LangGraph4j 图中流转的运行时状态。
- `EvidenceRecord`：当前任务的证据白名单和来源快照。

整体链路：

```text
POST /api/tasks
  -> 创建 ResearchTask
POST /api/tasks/{id}/run
  -> 异步启动 TaskGraphExecutor
  -> LangGraph4j CompiledGraph
  -> Planner
  -> RetrievalSubgraph
       -> Internal Retrieval
       -> External Retrieval
       -> Merge / Deduplicate / Sort
  -> Extract
  -> Verify
       -> 通过：Write
       -> 证据不足：回到 RetrievalSubgraph
  -> Write
  -> Review
       -> 通过：END
       -> 需要补检索：RetrievalSubgraph
       -> 只需重新验证：Verify
  -> ResearchTask.COMPLETED
```

## 1. 用户提交问题

入口文件：

- `src/main/java/com/astray/insightflow/task/api/TaskController.java`
- `src/main/java/com/astray/insightflow/task/service/TaskService.java`
- `src/main/java/com/astray/insightflow/task/domain/ResearchTask.java`

请求：

```http
POST /api/tasks
Content-Type: application/json

{
  "query": "分析新能源汽车行业的竞争格局",
  "language": "zh-CN"
}
```

`TaskController.createTask(...)` 只负责校验请求、补默认语言并调用 `TaskService.createTask(...)`。

`TaskService.createTask(...) `不会调用模型，只创建任务：

```java
task.setId(UUID.randomUUID().toString());
task.setQueryText(query);
task.setLanguage(language);
task.setStatus(ResearchTaskStatus.CREATED);
researchTaskRepository.save(task);
```

这时数据库只有任务主记录，没有 Plan、Evidence 或 Report。创建和执行拆成两个接口：

- `POST /api/tasks`：创建任务。
- `POST /api/tasks/{id}/run`：启动任务。

稳定的 `taskId` 后面会同时用于 SSE、Checkpoint、EvidenceRecord、报告和评测。

## 2. 启动异步执行

入口：

- `src/main/java/com/astray/insightflow/task/api/TaskController.java`
- `src/main/java/com/astray/insightflow/graph/TaskGraphExecutor.java`

`runTask(...) `先确认任务存在，再调用：

```java
taskGraphExecutor.executeAsync(taskId);
return ResponseEntity.accepted().body(...);
```

`TaskGraphExecutor.executeAsync(...) `使用 `@Async("taskExecutionExecutor")`，所以 HTTP 请求不会等待整份报告完成。

`execute(...) `的核心逻辑：

```java
ResearchTask task = taskService.markRunning(taskId);

RunnableConfig config = RunnableConfig.builder()
        .threadId(taskId)
        .build();

compiledGraph.invoke(
        researchStateFactory.initialState(task),
        config
);
```

这里有两个关键点：

1. `ResearchTask` 从 `CREATED` 变成 `RUNNING`。
2. LangGraph4j 的 `threadId` 使用 `taskId`，因此任务可以按 ID 恢复 Checkpoint。

图正常返回后，执行器调用 `markCompleted(...) `；任意节点异常则调用 `markFailed(...)`。

## 3. 图的构建与拓扑

相关文件：

- `src/main/java/com/astray/insightflow/config/GraphRuntimeConfig.java`
- `src/main/java/com/astray/insightflow/graph/ResearchGraphBuilder.java`
- `src/main/java/com/astray/insightflow/graph/subgraph/RetrievalSubgraphBuilder.java`

Spring 启动时，`GraphRuntimeConfig` 调用 `ResearchGraphBuilder.build()` 创建 `CompiledGraph<ResearchState>` Bean。

主图节点包括：

```text
START -> planner
       -> retrievalStart
       -> retrieveInternal ----+
                               +-> mergeRerank
       -> retrieveExternal ----+
       -> extract
       -> verify
          +-> write
          +-> retrievalStart
       -> write
       -> review
          +-> END
          +-> retrievalStart
          +-> verify
```

图的拓扑在启动时固定，但实际路径由 `ResearchState` 决定：

- Verify 通过，进入 Write。
- Verify 证据不足，回到 Retrieval。
- Review 通过，进入 END。
- Review 要求补证据，回到 Retrieval。
- Review 只要求重新验证，回到 Verify。

## 4. ResearchState：所有节点共享的数据

文件：

- `src/main/java/com/astray/insightflow/graph/state/ResearchState.java`
- `src/main/java/com/astray/insightflow/graph/state/ResearchStateFactory.java`

主要字段：

```text
taskId                 任务 ID
userQuery              用户问题
language               输出语言
plan                   Planner 计划
subQueries             检索子问题
needExternalSearch     是否需要外部搜索
internalEvidences      内部证据
externalEvidences      外部证据
mergedEvidences        合并证据
facts                  抽取事实
claims                 验证主张
verifyDecision         验证路由结果
reportDraft            报告草稿
reviewResult           审查结果
loopCount              回路次数
metrics                指标
timeline               时间线
status                 图内状态
```

初始状态由 `ResearchStateFactory.initialState(task)` 创建：

```java
inputs.put(ResearchState.TASK_ID, task.getId());
inputs.put(ResearchState.USER_QUERY, task.getQueryText());
inputs.put(ResearchState.LANGUAGE, task.getLanguage());
inputs.put(ResearchState.LOOP_COUNT, 0);
inputs.put(ResearchState.STATUS, "RUNNING");
```

`ResearchStateFactory` 还负责从 Checkpoint JSON 恢复为强类型对象，例如 `PlanResult`、`List<Evidence>`、`ReportDraft`。其中 `metrics` 和 `timeline` 有合并函数，避免并行检索分支互相覆盖。

## 5. Planner：把自然语言变成研究计划

文件：

- `src/main/java/com/astray/insightflow/graph/node/PlannerNode.java`
- `src/main/java/com/astray/insightflow/agent/planner/PlannerAgent.java`
- `src/main/resources/prompts/planner-system.txt`
- `src/main/resources/prompts/planner-user.txt`

`PlannerNode.execute(...) `调用 LangChain4j AI Service：

```java
plannerAgent.plan(state.userQuery(), state.language());
```

模型输出的是 `PlanResult`，而不是普通文本。计划通常包含：

```text
title
dimensions
subQueries
needExternalSearch
```

之后通过 `PlannerPlanTemplates.normalize(...)` 归一化模型结果，减少字段缺失和结构不一致。

Planner 的结果写入两处：

```text
ResearchState.PLAN
    -> 后续节点继续使用

TaskPlanEntity.planJson
    -> 任务详情、回放和恢复使用
```

Planner 节点还会写 `AgentRunLog`、发布 SSE 事件，并记录 Token 估算等指标。

## 6. RetrievalSubgraph：并行获取内外部证据

文件：

- `src/main/java/com/astray/insightflow/graph/subgraph/RetrievalSubgraphBuilder.java`
- `src/main/java/com/astray/insightflow/graph/node/RetrievalDispatchNode.java`

子图节点：

```text
retrievalStart
  -> retrieveInternal
  -> retrieveExternal
  -> mergeRerank
```

`retrievalStart` 记录 `needExternalSearch` 和分支数。要注意，它主要是调度标记节点；外部是否真的产出证据，由 `RetrieveExternalNode` 把开关传给 `WebSearchTool` 决定。

### 6.1 内部检索调用链

```text
RetrieveInternalNode
  -> KbSearchTool.search(...)
  -> InternalRetrievalService.search(taskId, subQueries)
  -> rank(subQueries)
  -> EvidenceRecordRepository.saveAll(...)
```

`InternalRetrievalService.rank(...) `的流程：

1. 按 collection 过滤知识文档。
2. 遍历 `DocumentChunk`，计算关键词分。
3. 向量服务启用时，查询 pgvector。
4. 分别得到关键词排名和向量排名。
5. 使用加权 RRF 合并排名。
6. 创建带分数分解和原文定位信息的 `Evidence`。

`search(...) `返回前会把 Evidence 转为 `EvidenceRecord`，删除当前任务旧的内部证据后重新保存。

所以后续引用不是引用“知识库里所有 Chunk”，而是引用本次任务实际命中的 EvidenceRecord。

### 6.2 外部检索调用链

文件：

- `src/main/java/com/astray/insightflow/graph/node/RetrieveExternalNode.java`
- `src/main/java/com/astray/insightflow/tool/WebSearchTool.java`
- `src/main/java/com/astray/insightflow/retrieval/service/ExternalRetrievalService.java`
- `src/main/java/com/astray/insightflow/tool/WebFetchTool.java`

```text
RetrieveExternalNode
  -> WebSearchTool.search(...)
  -> ExternalRetrievalService.search(...)
  -> SearchProvider.search(...)
  -> WebFetchTool.fetchPage(...)
  -> 去重、相关性过滤、质量评分
  -> EvidenceRecordRepository.saveAll(...)
```

如果 `needExternalSearch == false`，外部服务清理当前任务旧的外部证据并返回空列表。

如果开启外部检索，服务会：

1. 对每个子查询执行搜索。
2. 按有序 `SearchProvider` 链调用 Sogou、Bing、DuckDuckGo。
3. Provider 失败时尝试下一个 Provider。
4. 规范化 URL 并去重。
5. 根据标题、摘要和 URL 做初次相关性过滤。
6. 用 `WebFetchTool` 请求页面并抓取正文。
7. 用最终标题、URL 和正文再次过滤。
8. 根据查询排名、相关性和站点质量计算分数。
9. 限制页面数量并保存外部 EvidenceRecord。

因此 WebSearch 不是只读搜索结果页，而是：

```text
搜索结果 -> 候选 URL -> 请求网页 -> Jsoup 提取正文 -> 形成 Evidence
```

### 6.3 MergeRerankNode 的真实职责

文件：

- `src/main/java/com/astray/insightflow/graph/node/MergeRerankNode.java`
- `src/main/java/com/astray/insightflow/tool/RerankTool.java`

节点先合并内外部 Evidence，再调用 `RerankTool` 做：

- URL 去重。
- 没有 URL 时按标题和摘要组合去重。
- 同一来源保留分数更高的证据。
- 按分数倒序排序。
- 限制合并后的最大数量。

要区分两种“重排”：

- 内部 Hybrid Retrieval 中的 `RerankService`：可调用 DashScope `gte-rerank-v2`，对候选做模型重排。
- `MergeRerankNode` 使用的 `RerankTool`：跨内外部来源去重和排序，不等同于模型 Rerank。

## 7. Extract：从证据中抽取事实

文件：

- `src/main/java/com/astray/insightflow/graph/node/ExtractNode.java`
- `src/main/java/com/astray/insightflow/agent/extractor/ExtractorAgent.java`
- `src/main/java/com/astray/insightflow/tool/FactNormalizeTool.java`

Extract 把以下内容交给抽取 Agent：

```text
userQuery
language
planJson
mergedEvidenceJson
```

输出是 `List<ExtractedFact>`，每条事实通常带有主体、属性、值、标准化值和 Evidence ID。

抽取有两级降级：

1. LLM 抽取异常，使用 `StubExtractorAgent` 启发式抽取。
2. LLM 返回空事实但 Evidence 不为空，也使用启发式抽取。

之后调用 `FactNormalizeTool`，再将事实写入：

```text
ResearchState.FACTS
ExtractedFactEntity
```

## 8. Verify：验证主张并决定是否补检索

文件：

- `src/main/java/com/astray/insightflow/graph/node/VerifyNode.java`
- `src/main/java/com/astray/insightflow/tool/CitationTool.java`
- `src/main/java/com/astray/insightflow/tool/CitationGuardTool.java`
- `src/main/java/com/astray/insightflow/tool/TrustScoreTool.java`
- `src/main/java/com/astray/insightflow/graph/router/VerifyRouteDecider.java`

Verify Agent 接收：

```text
userQuery
language
factsJson
mergedEvidenceJson
loopCount
```

输出 `VerificationResult`，包括 Claims 和初步决策。

### 8.1 CitationTool

如果模型已经给出 supporting Evidence IDs，就先保留；如果没有，`CitationTool` 根据 Claim 文本、理由、维度与 Evidence 标题/摘要做词项匹配，最多绑定 3 个候选 Evidence ID。

### 8.2 CitationGuardTool

模型输出的 Evidence ID 不能直接信任。允许引用的 ID 是：

```text
当前运行时 Evidence ID
    ∩
当前 task 的 EvidenceRecord ID
    =
允许引用的 Evidence ID
```

因此伪造 ID、其他任务的 ID、当前运行中不存在的 ID都会被删除。

如果 Claim 被删除所有 supporting evidence：

- 标记低置信度。
- 没有冲突证据时标记为 `INSUFFICIENT`。

### 8.3 可信度与路由

`TrustScoreTool` 给 Evidence 计算来源可信度。Verify 再计算：

```text
adjustedConfidence
  = claimConfidence * 0.72
  + evidenceTrust * 0.28
```

进入写作前要求：

- Claim 数量达到最小值。
- 平均置信度达到阈值。
- Citation coverage 至少达到 0.50。
- 没有冲突 Claim。

不满足时设置 `rerunRetrieval=true` 并增加 `loopCount`。

`VerifyRouteDecider` 的逻辑：

```java
if (state.verifyDecision().isReadyForWrite()) {
    return GO_WRITE;
}
if (state.loopCount() > workflowProperties.maxLoops()) {
    return GO_WRITE;
}
return GO_RETRIEVAL;
```

达到最大循环次数后，系统允许低置信度写作，并把风险留在报告中，而不是无限循环。

## 9. Write：生成报告并再次做引用校验

文件：

- `src/main/java/com/astray/insightflow/graph/node/WriteNode.java`
- `src/main/java/com/astray/insightflow/agent/writer/WriterAgent.java`
- `src/main/java/com/astray/insightflow/report/service/ReportService.java`

Writer Agent 接收：

```text
userQuery
language
planJson
claimsJson
mergedEvidenceJson
```

输出 `ReportDraft`，包含标题、摘要、章节、结论和章节 Evidence IDs。

生成后，WriteNode 再调用：

```java
citationGuardTool.validateReportDraft(
        taskId,
        "write",
        draft,
        state.mergedEvidences()
);
```

每个章节如果没有合法引用，会被标记为低置信度；非法 Evidence ID会被移除。

最后 `ReportService.save(...) `保存：

```text
FinalReport.reportJson       结构化报告
FinalReport.reportMarkdown   展示用 Markdown
```

因此 Writer 负责组织内容，CitationGuard 负责约束引用边界。

## 10. Review：审查并决定局部重跑

文件：

- `src/main/java/com/astray/insightflow/graph/node/ReviewNode.java`
- `src/main/java/com/astray/insightflow/agent/reviewer/ReviewerAgent.java`
- `src/main/java/com/astray/insightflow/graph/router/ReviewRouteDecider.java`

Reviewer Agent 接收：

```text
userQuery
language
reportJson
claimsJson
evidenceJson
loopCount
```

它可以发现：

- 缺少引用。
- Claim 没有证据支持。
- 章节存在逻辑跳跃。
- 置信度过低。

Review 的路由：

```text
approved              -> END
rerunFrom=RETRIEVAL   -> retrievalStart
rerunFrom=VERIFY      -> verify
```

Review 节点会保存 review summary；如果超过最大循环次数或被标记低置信度，会将章节标记为低置信度，而不是无限重跑。

## 11. 完成、报告查询和 SSE

### 11.1 图完成

Review 路由到 `END` 后，`compiledGraph.invoke(...) `返回，`TaskGraphExecutor`执行：

```java
taskService.markCompleted(taskId);
publishTaskState(taskId, "COMPLETED", ...);
```

最终任务状态保存在 `ResearchTask`，不只存在 LangGraph4j 的内存状态中。

### 11.2 查询报告

接口：

```http
GET /api/tasks/{id}/report
```

`TaskController.getReport(...) `会：

1. 从 `FinalReport`读取 Markdown 和 JSON。
2. 反序列化为 `ReportDraft`。
3. 从 `EvidenceRecordRepository`读取当前任务的证据。
4. 返回报告和引用列表。

### 11.3 SSE

接口：

```http
GET /api/tasks/{id}/stream
```

文件：

- `src/main/java/com/astray/insightflow/task/service/TaskProgressPublisher.java`

每个节点通过 `publish(...) `发送：

```text
planner RUNNING / COMPLETED
retrieveInternal RUNNING / COMPLETED
checkpoint SAVED
verify COMPLETED
review COMPLETED
task COMPLETED
```

当前 SSE 事件历史保存在内存；Checkpoint、AgentRunLog、ToolCallLog 和业务实体落数据库。SSE 是展示层，不是任务状态的唯一来源。

## 12. Checkpoint、Resume 和局部重跑

文件：

- `src/main/java/com/astray/insightflow/graph/checkpoint/DatabaseCheckpointSaver.java`
- `src/main/java/com/astray/insightflow/graph/checkpoint/CheckpointService.java`
- `src/main/java/com/astray/insightflow/graph/checkpoint/GraphCheckpointMeta.java`

每次图推进时，`DatabaseCheckpointSaver` 保存：

```text
checkpointId
taskId/threadId
nodeName
nextNodeName
stateJson
stateSummaryJson
saveMode
createdAt / updatedAt
```

所以 Checkpoint 不只是保存当前节点，还保存恢复所需的完整 `ResearchState`。

### Resume

`POST /api/tasks/{id}/resume` 调用 `CheckpointService.prepareResume(...)`：

1. 指定 ID 就读取指定快照，否则读取最新快照。
2. 拒绝已经到 `END` 的快照。
3. 用任务 ID、Checkpoint ID、下一个节点构造 `RunnableConfig`。
4. 对 `statePatch` 做强类型转换。
5. `TaskGraphExecutor.resume(...)` 用空输入继续调用图。

### 指定节点重跑

`POST /api/tasks/{id}/rerun/{node}` 会先找到目标节点之前的快照，再从目标节点重新执行：

```text
rerun/retrieval -> retrievalStart
rerun/verify    -> verify
rerun/write     -> write
```

Planner 不支持通过 Checkpoint 局部重跑；重新规划使用完整 `/run`。

## 13. 一次请求中的数据库边界

```text
ResearchTask
  原始问题、任务状态、时间

TaskPlanEntity
  Planner 结构化计划

DocumentChunk / pgvector
  知识库切片和向量

EvidenceRecord
  本次任务实际命中的内部/外部证据

ExtractedFactEntity
  从证据抽取的事实

VerifiedClaimEntity
  验证后的主张、引用、置信度和状态

FinalReport
  结构化报告和 Markdown 报告

GraphCheckpointMeta
  图状态、当前节点和下一个节点

AgentRunLog / ToolCallLog
  节点和工具执行轨迹
```

可以用一句话理解三种状态：

```text
ResearchState = 当前运行时状态
数据库实体     = 可恢复、可查询、可审计的状态
SSE            = 实时展示事件
```

## 14. 复习时容易被追问的边界

### 检索子图是否每次请求动态生成

不是。节点和边在 Spring 启动时构建并编译。每次请求变化的是 `ResearchState`，图通过条件路由决定实际路径。

### 外部检索关闭时是否完全不执行外部节点

当前图仍然连接 `retrieveExternal`，但 `needExternalSearch` 会传给 `WebSearchTool`；关闭时服务清理旧外部证据并返回空列表。 `retrievalStart` 的分支数主要是指标。

### RerankTool 是否一定调用模型

不一定。`MergeRerankNode` 使用的 `RerankTool` 主要负责跨来源去重和排序；内部 Hybrid Retrieval 的模型重排由 `RerankService` 和 `DashScopeRerankService` 负责。

### 模型伪造 Evidence ID 会怎样

`CitationGuardTool` 将运行时 Evidence ID 与当前任务的 EvidenceRecord ID 求交集，伪造 ID会被删除；没有合法证据的 Claim 或章节会被标记低置信度。

### SSE 断线后任务会丢吗

不会因为 SSE 断线丢失核心任务状态。SSE 只是内存事件推送；任务、Checkpoint、Evidence、Claim 和 Report 都有数据库记录。

### Verify 和 Review 为什么都能回到前面

- Verify 发现事实不足、置信度不足或冲突，回到 Retrieval 补证据。
- Review 发现报告组织或引用问题，可以回到 Retrieval，也可以只回到 Verify。

## 15. 最后复述

用户请求先保存为 `ResearchTask`，再异步进入一个以 `ResearchState` 为共享状态的 LangGraph4j 工作流。Planner 生成研究计划，检索子图并行获取内外部 Evidence，Extract 产出事实，Verify 做引用和可信度校验，Write 生成并再次校验报告，Review 决定结束或局部重跑；整个过程通过 Checkpoint、数据库实体、AgentRunLog 和 EvidenceRecord 实现可恢复、可审计和可追溯。
