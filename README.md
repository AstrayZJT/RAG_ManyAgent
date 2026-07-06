# InsightFlow

InsightFlow 是一个面向行业研究与竞品分析的多 Agent 智能分析平台骨架。当前已推进到 Phase 3，重点展示稳定 workflow、局部 agentic 决策、checkpoint 恢复、并行检索、结构化评测和可观测执行轨迹。

主链路：

`任务创建 -> planner -> retrievalSubgraph -> extract -> verify -> write -> review -> report`

Phase 3 后，`retrievalSubgraph` 已接入并行分支：

`retrievalStart -> retrieveInternal + retrieveExternal -> mergeRerank`

## 已包含能力

- Spring Boot REST API + SSE 进度流
- LangChain4j Planner / Extractor / Verifier / Writer / Reviewer Agent
- 无 API Key 时自动降级为本地 stub，便于零依赖演示
- LangGraph4j StateGraph、条件路由、回退重跑、并行分支
- 每个节点完成后持久化 checkpoint，`threadId = taskId`
- 支持从 checkpoint resume，支持从指定节点 rerun
- 文档上传、切片、索引、内部检索
- 外部网页补证入口、抓取 stub、证据合并重排
- 结构化 fact 抽取、claim 验证、报告写作和审查
- `agent_run_log`、`tool_call_log`、`graph_checkpoint_meta`、`evaluation_record`
- timeline 聚合节点轨迹、工具调用、token / latency、checkpoint 和评测指标

## 运行方式

默认使用本地 H2 文件数据库，上传文件保存到 `knowledge/raw/`。

```powershell
mvn spring-boot:run
```

接入真实模型时配置：

```powershell
$env:OPENAI_API_KEY='your-key'
mvn spring-boot:run
```

## 关键 API

- `POST /api/documents/upload`
- `POST /api/documents/{id}/index`
- `POST /api/tasks`
- `POST /api/tasks/{id}/run`
- `POST /api/tasks/{id}/resume`
- `POST /api/tasks/{id}/rerun/{node}`
- `POST /api/tasks/{id}/evaluate`
- `GET /api/tasks/{id}`
- `GET /api/tasks/{id}/report`
- `GET /api/tasks/{id}/timeline`
- `GET /api/tasks/{id}/timeline?beforeNode=verify`
- `GET /api/tasks/{id}/stream`

## 可演示流程

1. 上传 UTF-8 文本文档到 `/api/documents/upload`
2. 调用 `/api/documents/{id}/index` 建索引
3. 调用 `/api/tasks` 创建研究任务
4. 调用 `/api/tasks/{id}/run` 启动图执行
5. 调用 `/api/tasks/{id}/timeline` 查看节点、工具、checkpoint 和指标轨迹
6. 调用 `/api/tasks/{id}/timeline?beforeNode=verify` 查看 verify 前的状态快照
7. 调用 `/api/tasks/{id}/resume` 从 checkpoint 恢复，可带 `statePatch` 人工修改状态
8. 调用 `/api/tasks/{id}/rerun/retrieval` 从检索子图重新执行
9. 调用 `/api/tasks/{id}/evaluate` 生成基础评测结果
10. 调用 `/api/tasks/{id}/report` 查看带引用和低置信度标记的报告

## Resume 示例

```json
{
  "checkpointId": "checkpoint-id-before-verify",
  "statePatch": {
    "metrics": {
      "manualIntervention": true
    },
    "timeline": [
      "manual patch before verify"
    ]
  }
}
```

## Rerun 示例

```json
{
  "statePatch": {
    "metrics": {
      "rerunDemo": true
    },
    "timeline": [
      "manual rerun from retrieval"
    ]
  }
}
```

支持的常用 rerun 节点：

- `retrieval` / `retrievalStart`
- `mergeRerank`
- `extract`
- `verify`
- `write`
- `review`

`planner` 暂不支持 checkpoint rerun，如需完整重跑可重新调用 `/run`。

## 当前边界

- 图执行、checkpoint、resume、rerun、timeline、评测记录是真实可运行能力。
- 外部检索当前仍是可演示 stub，后续可替换为真实搜索 API。
- Agent 默认实现为本地规则 stub，有 API Key 时可接 LangChain4j `@AiService`。
- 向量库、MinIO、Redis、RabbitMQ 在配置层预留，当前默认演示路径使用 H2 与本地文件。
