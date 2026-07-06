# InsightFlow

InsightFlow 是一个面向行业研究与竞品分析的多 Agent 智能分析平台骨架项目。当前已实现 Phase 2 骨架，重点跑通：

`任务创建 -> planner -> retrievalSubgraph -> extract -> verify -> write -> review -> report 输出`

## 已包含能力

- Spring Boot REST API + SSE 进度流
- LangChain4j Planner / Extractor / Verifier / Writer / Reviewer Agent（无 API Key 时自动降级为本地 stub）
- LangGraph4j 主图骨架、条件路由与回退重试
- 文档上传、切片、索引、内部检索
- 结构化任务计划、事实抽取、claim 验证、报告审查
- agent_run_log / tool_call_log / timeline / citationCoverage 等基础观测

## 运行方式

### 默认启动（零依赖演示）

```powershell
mvn spring-boot:run
```

默认使用本地 H2 文件数据库，文件上传存到 `knowledge/raw/`。

### PostgreSQL 启动

```powershell
$env:DB_HOST='localhost'
$env:DB_PORT='5432'
$env:DB_NAME='agentdemo'
$env:DB_USERNAME='postgres'
$env:DB_PASSWORD='your-password'
$env:OPENAI_API_KEY='your-key'
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

## 关键 API

- `POST /api/tasks`
- `POST /api/tasks/{id}/run`
- `GET /api/tasks/{id}`
- `GET /api/tasks/{id}/report`
- `GET /api/tasks/{id}/timeline`
- `GET /api/tasks/{id}/stream`
- `POST /api/documents/upload`
- `POST /api/documents/{id}/index`

## 快速体验

1. 上传一个 UTF-8 编码的文本文件到 `/api/documents/upload`
2. 调用 `/api/documents/{id}/index` 建索引
3. 调用 `/api/tasks` 创建任务
4. 调用 `/api/tasks/{id}/run` 启动图执行
5. 用 `/api/tasks/{id}/stream` 观察节点进度
6. 用 `/api/tasks/{id}/timeline` 查看节点、工具与指标轨迹
7. 用 `/api/tasks/{id}/report` 查看生成的报告
