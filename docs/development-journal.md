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

### 晚间联调补充

#### 新增修复
- `PlannerNode` 现在会对 LLM 产出的 `PlanResult` 做统一归一化与兜底，避免出现“计划很泛、没锚定主题、直接把外部检索关掉”的情况。
- 新增 `PlannerPlanTemplates`：
  - 支持通用研究模板
  - 支持电网/行业研究模板
  - 自动补齐 `dimensions / subQueries / factSchema`
  - 对“明显太泛”的计划直接替换为可执行 fallback plan
- `StubPlannerAgent` 改为复用同一套模板逻辑，保证有无真实模型时行为一致。
- 修复 `extractor-user.txt` 占位符错误：
  - 原来写成了单花括号，真实 LLM 实际拿不到 `planJson` 和 `evidenceJson`
  - 现已改为 `{{planJson}} / {{evidenceJson}}`
- `ExtractNode` 新增工程兜底：
  - 如果 LLM 抽取失败或返回空 facts
  - 自动切换到启发式抽取
  - 保证主流程不会因为单个 Agent 空返回直接塌掉
- `ExternalRetrievalService` 继续增强：
  - Bing 优先，DuckDuckGo 兜底
  - 增加 query-topic 相关性过滤
  - 避免把“关于”“年份百科”“无关热词”直接塞进证据池
  - 针对电网类主题补充权威站点定向搜索入口（如能源局、国家电网、南方电网、中电联）

#### 新增测试
- 新增 `PlannerPlanTemplatesTests`
  - 验证泛化 Planner 输出会被电网主题 fallback 替换
  - 验证时效性主题会自动打开外部检索
- 新增 `ExtractNodeTests`
  - 验证 LLM 返回空 facts 时会自动进入 heuristic fallback
- 扩展 `ExternalRetrievalServiceTests`
  - 验证相关电网结果会被保留
  - 验证“关于（汉语词语）”“2020 年度新闻”这类噪音结果会被过滤

#### 真实运行验证
- 运行环境：
  - PostgreSQL `agentdemo`
  - `qwen-plus`
  - Spring Profile=`postgres`
- 真实任务已经可以稳定跑完整条链路：
  - `planner -> retrieval -> extract -> verify -> write -> review`
  - checkpoint 会持续落库
  - 任务轨迹、token、citationCoverage 可以通过接口查询
- 联调过程中观察到的阶段性结果：
  - 第一轮：Planner 已贴题，外部检索拿到 8 条证据，但抽取为 0 facts
  - 第二轮：修复 extractor prompt 后，facts/claims/report/citation 全链路跑通
  - 第三轮：外检相关性过滤生效后，噪音证据数量明显下降，但也出现过“过滤过严导致证据为 0”的情况

#### 当前仍需继续优化的点
- 真实外部检索已经不再只是“能搜到”，但“搜得准”还没有完全达到调研报告要求。
- 电网主题下仍可能出现以下问题：
  - 某些 query 被搜索引擎召回到百科型年度页面
  - 相关性阈值过松时会进脏证据
  - 阈值过紧时又可能把候选全部过滤掉
- 下一步需要继续做：
  1. 针对权威站点做更稳定的 query expansion
  2. 引入更细粒度的 domain signal / year signal / entity signal 打分
  3. 优化提取与验证阶段对“弱相关证据”的拒收策略
  4. 再做一轮电网主题回归，确保最终报告引用真正落在电网行业事实而不是宏观背景词条上
