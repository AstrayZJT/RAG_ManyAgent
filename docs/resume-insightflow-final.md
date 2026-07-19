# InsightFlow Resume Version

## InsightFlow：面向行业研究的可追溯证据 RAG 多 Agent 报告系统

**技术栈：** Spring Boot、LangGraph4j、LangChain4j、Qwen3.7-Plus、text-embedding-v4、gte-rerank-v2、PostgreSQL、pgvector、Spring Data JPA、Flyway、Jsoup

**项目描述：**

面向行业研究与竞品分析场景，构建从问题规划、内外部检索、事实抽取、主张验证到报告生成的多 Agent 工作流。系统不直接信任模型生成的引用，而是以持久化 Evidence 为证据白名单，对文档版本、原文位置、检索分数和报告引用进行全链路追踪，使生成结论能够回溯到具体文档切片。

**核心工作：**

- 基于 LangGraph4j 编排 Planner、Retrieval、Extractor、Verifier、Writer 和 Reviewer 节点，支持并行内外部检索、条件路由、checkpoint 持久化、指定节点 rerun、任务 resume 与 SSE 进度推送，避免长流程失败后整条链路重新执行。
- 重构知识库摄取链路，将固定 800 字符硬切改为句子/段落边界切块与 overlap；为文档和 chunk 保存 SHA-256、chunkIndex、字符起止范围及 collection metadata，重新索引后历史 Evidence 仍保留检索时的内容快照和来源位置。
- 接入 LangChain4j、text-embedding-v4 与 pgvector，保留加权中文词项检索，并通过加权 RRF 融合关键词与向量排名；对 Top 20 候选调用 gte-rerank-v2 二次排序，模型异常时自动降级为 Hybrid，Evidence 中持久化 lexical、vector、fusion 和 rerank 分数组成。
- 建立 10 篇固定评测文档和 20 条人工标注检索问题，分别覆盖字面匹配与语义改写；在同一 PostgreSQL/pgvector 数据集上，Hybrid+Rerank 将整体 Hit@1 从 0.65 提升至 0.95、MRR 从 0.685 提升至 0.967，语义问题 Hit@1 从 0.30 提升至 0.90，Hit@3/Hit@5 达到 1.00。
- 将 EvidenceRecord 作为 task 级引用白名单，Claim 和 Report 引用必须同时存在于当前运行时证据与数据库记录；自动移除伪造、缺失和跨任务 evidence id，对无有效证据的主张或章节标记低置信度，并统计 Claim Coverage、Section Coverage 与 Citation Validity。
- 将 Sogou、Bing、DuckDuckGo HTML 检索抽象为有序 SearchProvider 链，统一处理重定向 URL、结果去重、页面抓取、主题相关性过滤和失败降级；通过 Flyway 管理 pgvector 扩展和枚举约束迁移，并增加可选 Testcontainers pgvector 集成测试与持久化评测历史。

## Interview Boundary

- 指标来自固定的 10 文档、20 问题开发评测集，不宣称等同于公开基准或生产全量效果。
- Testcontainers 测试在 Docker 可用时执行；当前机器 Docker Desktop 未启动时会跳过，但真实 PostgreSQL 18 + pgvector 链路已完成文档重索引、向量写入、metadata filter、召回和 rerank 端到端验证。
- 外部 HTML SearchProvider 适合演示与接口抽象，生产环境应替换为有 SLA 和授权的数据搜索 API。
