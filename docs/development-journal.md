# InsightFlow 开发日记

## 2026-07-07

### 今日目标
- 把原本偏演示性质的骨架，推进到“可接真实模型、可做真实外部检索、可产出正式中文调研报告”的版本。
- 让项目支持用户提供的环境变量，不把数据库账号和 API Key 写死在仓库里。

### 今天完成的事情

#### 1. 运行配置改造
- 新增 `DataSourceConfig`，支持以下运行方式：
  - 优先使用 `SPRING_DATASOURCE_URL`
  - 其次使用 `DB_HOST / DB_PORT / DB_NAME / DB_USERNAME / DB_PASSWORD`
  - 都没有时回退到本地 H2
- `application.yml` 改为支持：
  - `OPENAI_BASE_URL`
  - `OPENAI_API_KEY`
  - `OPENAI_MODEL_NAME`
  - `OPENAI_TIMEOUT`
  - `OPENAI_MAX_RETRIES`
  - `OPENAI_MAX_COMPLETION_TOKENS`
- `pom.xml` 明确设置 UTF-8 编码，避免中文资源和源代码在不同机器上出现乱码。

#### 2. 真实外部检索落地
- 新增 `jsoup` 依赖。
- 重写 `ExternalRetrievalService`：
  - 使用 DuckDuckGo HTML 搜索页做真实外部召回
  - 解析标题、链接、摘要
  - 对 URL 去重
  - 控制每轮抓取数量
- 重写 `WebFetchTool`：
  - 用 Jsoup 抓取网页正文
  - 清洗 `script/style/nav/footer/aside` 等噪音
  - 优先提取 `article/main/content`，不足时回退到 `body`
  - 结果继续进入证据池和工具调用日志

#### 3. RAG 与证据处理增强
- 升级 `InternalRetrievalService`：
  - 不再只是简单 `contains`
  - 引入词项覆盖、词频和标题加权
- 升级 `RerankTool`：
  - 合并去重
  - 统一按分数排序
  - 控制最终证据规模
- 升级 `CitationTool`：
  - 当模型没给出引用时，基于 claim 与 evidence 的词项重合做回填
- 升级 `TrustScoreTool`：
  - 对 `.gov/.edu`、新闻/投资者关系站点、社区类站点区分可信度

#### 4. 中文报告生成强化
- 完整重写五类 Agent 提示词：
  - `planner`
  - `extractor`
  - `verifier`
  - `writer`
  - `reviewer`
- 目标从“演示简报”改为“正式中文调研报告”：
  - 执行摘要
  - 分维度章节
  - 结论与建议
  - 低置信度标记
  - 引用 evidenceIds
- `ReportService` 生成的 Markdown 已改为中文结构。

#### 5. 中文资料兼容性
- `KnowledgeDocumentService` 新增编码探测回退：
  - UTF-8
  - GB18030
  - GBK
- 这样中文行业资料上传后，不会因为编码问题直接把知识库切坏。

#### 6. 前端健壮性修复
- 当浏览器本地缓存了失效任务 id，而数据库已切换或清空时：
  - 自动清理失效选中项
  - 回到当前任务列表
  - 避免页面一直报错和重连

### 今日验证结果

#### 已验证通过
- `mvn test` 通过
- `mvn -DskipTests package` 通过
- 使用 `DB_*` 环境变量启动成功
- 服务已成功连接 PostgreSQL `agentdemo`
- `/actuator/health` 返回 `UP`

#### 真实模型联调情况
- 直接请求 `https://api.openai.com/v1/models` 时，返回：
  - `403 unsupported_country_region_territory`
- 应用内实际跑任务时，Planner 节点最终失败：
  - `GraphRunnerException -> RuntimeException -> java.net.ConnectException`

### 当前判断
- 代码层面已经具备“真实 LLM + 多 Agent + 内部 RAG + 外部搜索补证”的运行路径。
- 当前不能完成真实在线报告生成，不是因为业务代码没接通，而是因为当前机器直连 OpenAI 官方地址存在网络/区域限制。

### 下一步建议
1. 提供一个当前机器可访问的 OpenAI 兼容 `OPENAI_BASE_URL`
2. 或切换到可访问的兼容模型服务
3. 再执行一次完整调研任务，验证最终报告质量、引用覆盖率和回退链路

