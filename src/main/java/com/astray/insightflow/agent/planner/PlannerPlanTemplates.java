package com.astray.insightflow.agent.planner;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlannerPlanTemplates {

    private static final Pattern YEAR_PATTERN = Pattern.compile("20\\d{2}");
    private static final Pattern ENGLISH_TOKEN_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9+._-]{1,}");

    private static final String[] POWER_GRID_TERMS = {
            "电网", "电力", "特高压", "输电", "配电网", "南方电网", "国家电网", "新型电力系统"
    };

    private static final String[] MARKET_RESEARCH_TERMS = {
            "竞品", "竞争", "市场", "行业", "企业", "公司", "品牌", "财务", "融资", "估值", "客户", "用户", "商业化"
    };

    private static final String[] EXTERNAL_SEARCH_HINTS = {
            "历史", "趋势", "结构", "政策", "法规", "市场", "竞品", "竞争", "财务",
            "技术", "未来", "行业", "调研", "分析", "发展", "最新", "近况", "进展", "更新"
    };

    private static final String[] INTERNAL_ONLY_HINTS = {
            "仅内部", "仅基于内部", "内部文档", "上传文档", "本地文档"
    };

    private static final String[] GENERIC_PLAN_PHRASES = {
            "指定研究目标", "核心要素", "综合调研", "分析框架", "市场概况", "行业研究"
    };

    private static final String[] LEADING_ACTION_PREFIXES = {
            "请研究", "请分析", "请调研", "请梳理", "请比较", "请总结",
            "研究", "分析", "调研", "梳理", "比较", "评估", "总结", "围绕", "关于", "请"
    };

    private PlannerPlanTemplates() {
    }

    public static PlanResult normalize(String query, String language, PlanResult candidate) {
        String normalizedQuery = normalizeQuery(query);
        String normalizedLanguage = normalizeLanguage(language);
        PlanResult plan = candidate == null ? new PlanResult() : candidate;
        sanitize(plan);

        if (shouldFallback(normalizedQuery, plan)) {
            return buildFallbackPlan(normalizedQuery, normalizedLanguage);
        }

        if (!StringUtils.hasText(plan.getObjectiveSummary())) {
            plan.setObjectiveSummary(buildObjectiveSummary(normalizedQuery, normalizedLanguage));
        }
        if (plan.getDimensions().size() < 3) {
            plan.setDimensions(buildDimensions(normalizedQuery));
        }
        if (plan.getSubQueries().size() < 4) {
            plan.setSubQueries(buildSubQueries(normalizedQuery, plan.getDimensions()));
        }
        if (plan.getFactSchema().size() < 4) {
            plan.setFactSchema(buildFactFields(normalizedQuery));
        }
        if (!StringUtils.hasText(plan.getRetrievalStrategy())) {
            plan.setRetrievalStrategy(buildRetrievalStrategy(normalizedQuery));
        }
        if (!plan.isNeedExternalSearch() && shouldPreferExternalSearch(normalizedQuery)) {
            plan.setNeedExternalSearch(true);
        }
        sanitize(plan);
        return plan;
    }

    public static PlanResult buildFallbackPlan(String query, String language) {
        String normalizedQuery = normalizeQuery(query);
        String normalizedLanguage = normalizeLanguage(language);
        PlanResult plan = new PlanResult();
        plan.setObjectiveSummary(buildObjectiveSummary(normalizedQuery, normalizedLanguage));
        plan.setDimensions(buildDimensions(normalizedQuery));
        plan.setSubQueries(buildSubQueries(normalizedQuery, plan.getDimensions()));
        plan.setRetrievalStrategy(buildRetrievalStrategy(normalizedQuery));
        plan.setFactSchema(buildFactFields(normalizedQuery));
        plan.setNeedExternalSearch(shouldPreferExternalSearch(normalizedQuery));
        sanitize(plan);
        return plan;
    }

    private static void sanitize(PlanResult plan) {
        plan.setObjectiveSummary(normalizeText(plan.getObjectiveSummary()));
        plan.setRetrievalStrategy(normalizeText(plan.getRetrievalStrategy()));
        plan.setDimensions(cleanDimensions(plan.getDimensions()));
        plan.setSubQueries(cleanStrings(plan.getSubQueries()));
        plan.setFactSchema(cleanFactFields(plan.getFactSchema()));
    }

    private static boolean shouldFallback(String query, PlanResult plan) {
        if (plan == null) {
            return true;
        }
        if (!StringUtils.hasText(query)) {
            return !StringUtils.hasText(plan.getObjectiveSummary()) || plan.getSubQueries().size() < 4;
        }
        if (!StringUtils.hasText(plan.getObjectiveSummary())) {
            return true;
        }
        if (plan.getDimensions().size() < 3 || plan.getSubQueries().size() < 4 || plan.getFactSchema().size() < 4) {
            return true;
        }

        List<String> signals = extractSignals(query);
        String planText = buildPlanText(plan);
        if (isObviouslyGeneric(plan.getObjectiveSummary())) {
            return true;
        }
        if (signals.isEmpty()) {
            return false;
        }

        for (String signal : signals) {
            if (planText.contains(signal)) {
                return false;
            }
        }
        return !containsDomainHint(query, planText);
    }

    private static boolean isObviouslyGeneric(String text) {
        return containsAny(text, GENERIC_PLAN_PHRASES);
    }

    private static boolean containsDomainHint(String query, String planText) {
        if (isPowerGridQuery(query)) {
            return containsAny(planText, POWER_GRID_TERMS);
        }
        if (isMarketResearchQuery(query)) {
            return containsAny(planText, MARKET_RESEARCH_TERMS);
        }
        return false;
    }

    private static String buildPlanText(PlanResult plan) {
        StringBuilder builder = new StringBuilder();
        builder.append(nullToEmpty(plan.getObjectiveSummary())).append('\n');
        for (PlanDimension dimension : plan.getDimensions()) {
            builder.append(nullToEmpty(dimension.getName())).append(' ')
                    .append(nullToEmpty(dimension.getRationale())).append('\n');
        }
        for (String subQuery : plan.getSubQueries()) {
            builder.append(nullToEmpty(subQuery)).append('\n');
        }
        builder.append(nullToEmpty(plan.getRetrievalStrategy())).append('\n');
        for (FactField field : plan.getFactSchema()) {
            builder.append(nullToEmpty(field.getName())).append(' ')
                    .append(nullToEmpty(field.getDescription())).append('\n');
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private static String buildObjectiveSummary(String query, String language) {
        String target = StringUtils.hasText(query) ? query : "研究主题";
        if ("en-US".equalsIgnoreCase(language)) {
            return "Build a verifiable research framework for " + target;
        }
        return "系统梳理" + target + "，形成可检索、可验证、可写作的研究框架";
    }

    private static List<PlanDimension> buildDimensions(String query) {
        if (isPowerGridQuery(query)) {
            return List.of(
                    new PlanDimension("发展历史分期与关键事件", "厘清中国电网各阶段的时间节点、政策驱动与标志性工程，支撑纵向演进分析。"),
                    new PlanDimension("产业结构与主体关系", "明确国家电网、南方电网等主体的管辖范围、资产规模与权责边界，反映现行体制架构。"),
                    new PlanDimension("技术演进与基础设施特征", "聚焦输电电压等级升级、智能调度系统、新能源并网能力与数字化平台建设等硬性指标。"),
                    new PlanDimension("政策法规与标准体系", "识别影响电网发展的核心法规、规划文件、并网标准与绿电交易规则，揭示制度约束。"),
                    new PlanDimension("未来趋势与风险机会", "研判双碳目标、新型电力系统与电网投资节奏下的中长期趋势与不确定性。")
            );
        }
        if (isMarketResearchQuery(query)) {
            return List.of(
                    new PlanDimension("市场定位与竞争格局", "界定研究对象所在细分赛道、主要竞争对手与差异化位置。"),
                    new PlanDimension("技术能力与创新水平", "提炼专利、研发投入、产品能力与技术壁垒等可验证指标。"),
                    new PlanDimension("财务表现与资本结构", "分析营收、毛利、现金流与融资事件，判断商业健康度。"),
                    new PlanDimension("政策适配与合规状态", "核查资质、监管要求、行政处罚与合规风险。"),
                    new PlanDimension("客户覆盖与商业化路径", "梳理用户规模、渠道结构、转化效率与收入来源。")
            );
        }
        return List.of(
                new PlanDimension("研究背景与范围", "明确研究对象、问题边界与分析口径，确保后续检索一致。"),
                new PlanDimension("核心主体与关键变量", "识别研究中最重要的对象、产品、政策或市场变量。"),
                new PlanDimension("现状指标与对比", "提炼可量化指标并与同类对象或历史阶段进行对照。"),
                new PlanDimension("外部环境与风险", "分析政策、监管、供需变化与其他外部影响因素。"),
                new PlanDimension("趋势与结论", "归纳中长期走势、机会、风险与需要验证的判断。")
        );
    }

    private static List<String> buildSubQueries(String query, List<PlanDimension> dimensions) {
        if (isPowerGridQuery(query)) {
            return List.of(
                    composeSubQuery(query, "发展历史 分期 关键事件 政策 重大工程"),
                    composeSubQuery(query, "国家电网 南方电网 资产规模 覆盖范围 年报"),
                    composeSubQuery(query, "特高压 线路 条数 总长度 最高电压等级"),
                    composeSubQuery(query, "电力法 修订 调度权 分布式电源 接入"),
                    composeSubQuery(query, "新型电力系统 2030 2060 目标"),
                    composeSubQuery(query, "数字化巡检 调控云 源网荷储 协同控制")
            );
        }
        if (isMarketResearchQuery(query)) {
            return List.of(
                    composeSubQuery(query, "市场定位 细分赛道 份额 变化趋势"),
                    composeSubQuery(query, "竞争格局 主要竞对 排名 差异化"),
                    composeSubQuery(query, "技术能力 专利 研发投入 技术成果"),
                    composeSubQuery(query, "财务表现 营收 毛利 现金流 融资事件"),
                    composeSubQuery(query, "政策 合规 资质 监管 风险"),
                    composeSubQuery(query, "客户覆盖 渠道结构 商业化路径 转化率")
            );
        }

        List<String> subQueries = new ArrayList<>();
        for (PlanDimension dimension : dimensions) {
            if (StringUtils.hasText(dimension.getName())) {
                subQueries.add(composeSubQuery(query, dimension.getName()));
            }
            if (subQueries.size() >= 6) {
                break;
            }
        }
        while (subQueries.size() < 4) {
            subQueries.add(composeSubQuery(query, "关键事实"));
        }
        return subQueries;
    }

    private static List<FactField> buildFactFields(String query) {
        if (isPowerGridQuery(query)) {
            return List.of(
                    new FactField("historicalPhaseStartYear", "某发展阶段的起始年份，如厂网分开阶段为2002年"),
                    new FactField("gridCompanyAssetScaleRmb", "某电网企业最新披露的总资产规模，单位为亿元人民币"),
                    new FactField("uhvLineCountOperational", "截至检索时点已正式投运的特高压输电线路总数"),
                    new FactField("regulatoryClauseKeyContent", "最新版电力法中约束调度权或分布式电源并网义务的关键条款"),
                    new FactField("targetYearFlexibilityRatio", "目标年份负荷侧可调节资源占最大负荷的比例，单位为百分比"),
                    new FactField("digitalProjectCumulativeInvestment", "数字化项目在一定期间内披露的累计投资金额，单位为亿元")
            );
        }
        if (isMarketResearchQuery(query)) {
            return List.of(
                    new FactField("marketSegment", "所属一级及二级行业分类"),
                    new FactField("competitorRanking", "细分赛道内前五名竞对及相对位次"),
                    new FactField("corePatentCount", "已授权且维持有效的发明专利总数"),
                    new FactField("revenueCAGR_3Y", "最近三年复合营收增长率"),
                    new FactField("complianceStatus", "关键资质有效性状态及最近一次监管检查结论"),
                    new FactField("activeUserGrowth_QoQ", "最近一季度较上一季度的月活增长率")
            );
        }
        return List.of(
                new FactField("coreEntity", "研究对象中最重要的主体、产品或政策名称"),
                new FactField("keyMetric", "可量化指标、规模、增长率或排名"),
                new FactField("timelineEvent", "与研究主题相关的时间节点或里程碑事件"),
                new FactField("externalSignal", "政策、监管、市场或舆情等外部信号"),
                new FactField("trendJudgment", "对趋势、风险或机会的初步判断")
        );
    }

    private static String buildRetrievalStrategy(String query) {
        if (isPowerGridQuery(query)) {
            return "优先检索内部知识库中的行业年鉴、央企年报摘要、政策原文库及技术白皮书；对近年投运工程、最新法规条文与量化目标，调用外部权威信源补全。";
        }
        if (isMarketResearchQuery(query)) {
            return "优先检索内部知识库中的行业报告、企业档案、财务摘要与专利数据；对最新市场份额、监管状态、融资事件与媒体动态，调用外部权威信源补证。";
        }
        return "先检索内部知识库文档块，再按研究维度聚合证据；如涉及时效性信息或内部证据稀少，则补充外部权威网页证据。";
    }

    private static boolean shouldPreferExternalSearch(String query) {
        if (!StringUtils.hasText(query)) {
            return false;
        }
        if (containsAny(query, INTERNAL_ONLY_HINTS)) {
            return false;
        }
        if (containsAny(query, EXTERNAL_SEARCH_HINTS)) {
            return true;
        }
        Matcher matcher = YEAR_PATTERN.matcher(query);
        while (matcher.find()) {
            int year = Integer.parseInt(matcher.group());
            if (year >= 2023) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPowerGridQuery(String query) {
        return containsAny(query, POWER_GRID_TERMS);
    }

    private static boolean isMarketResearchQuery(String query) {
        return containsAny(query, MARKET_RESEARCH_TERMS);
    }

    private static List<String> extractSignals(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String cleaned = normalizeText(query);
        String stripped = stripLeadingVerbs(cleaned);
        Set<String> signals = new LinkedHashSet<>();

        collectContainedTerms(stripped, signals, POWER_GRID_TERMS);
        collectContainedTerms(stripped, signals, MARKET_RESEARCH_TERMS);

        for (String token : stripped.split("[\\s，。！？；、/\\\\|()（）:：]+")) {
            addSignal(signals, token);
            for (String segment : token.split("(?:的|与|和|及|以及|关于|面向|针对|围绕)")) {
                addSignal(signals, segment);
            }
        }

        Matcher englishMatcher = ENGLISH_TOKEN_PATTERN.matcher(stripped);
        while (englishMatcher.find()) {
            addSignal(signals, englishMatcher.group());
        }

        if (signals.isEmpty() && stripped.length() <= 24) {
            addSignal(signals, stripped);
        }

        List<String> orderedSignals = new ArrayList<>(signals);
        return orderedSignals.size() > 8 ? orderedSignals.subList(0, 8) : orderedSignals;
    }

    private static void collectContainedTerms(String text, Set<String> signals, String[] terms) {
        for (String term : terms) {
            if (containsAny(text, term)) {
                signals.add(term.toLowerCase(Locale.ROOT));
            }
        }
    }

    private static void addSignal(Set<String> signals, String token) {
        String normalized = stripLeadingVerbs(normalizeText(token));
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        if (normalized.length() < 2 || normalized.length() > 32 || isGenericToken(normalized)) {
            return;
        }
        signals.add(normalized.toLowerCase(Locale.ROOT));
    }

    private static String stripLeadingVerbs(String token) {
        String normalized = normalizeText(token);
        boolean changed;
        do {
            changed = false;
            for (String prefix : LEADING_ACTION_PREFIXES) {
                if (normalized.startsWith(prefix) && normalized.length() > prefix.length()) {
                    normalized = normalizeText(normalized.substring(prefix.length()));
                    changed = true;
                }
            }
        } while (changed);
        return normalized;
    }

    private static boolean isGenericToken(String token) {
        return containsAny(token, "研究目标", "研究问题", "综合调研", "分析框架", "核心要素", "关键事实", "指定研究目标");
    }

    private static boolean containsAny(String text, String... candidates) {
        if (!StringUtils.hasText(text) || candidates == null) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate) && normalized.contains(candidate.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static List<PlanDimension> cleanDimensions(List<PlanDimension> dimensions) {
        List<PlanDimension> cleaned = new ArrayList<>();
        if (dimensions == null) {
            return cleaned;
        }
        for (PlanDimension dimension : dimensions) {
            if (dimension == null) {
                continue;
            }
            String name = normalizeText(dimension.getName());
            String rationale = normalizeText(dimension.getRationale());
            if (StringUtils.hasText(name) && StringUtils.hasText(rationale)) {
                cleaned.add(new PlanDimension(name, rationale));
            }
        }
        return cleaned;
    }

    private static List<FactField> cleanFactFields(List<FactField> fields) {
        List<FactField> cleaned = new ArrayList<>();
        if (fields == null) {
            return cleaned;
        }
        for (FactField field : fields) {
            if (field == null) {
                continue;
            }
            String name = normalizeText(field.getName());
            String description = normalizeText(field.getDescription());
            if (StringUtils.hasText(name) && StringUtils.hasText(description)) {
                cleaned.add(new FactField(name, description));
            }
        }
        return cleaned;
    }

    private static List<String> cleanStrings(List<String> values) {
        List<String> cleaned = new ArrayList<>();
        if (values == null) {
            return cleaned;
        }
        for (String value : values) {
            String normalized = normalizeText(value);
            if (StringUtils.hasText(normalized)) {
                cleaned.add(normalized);
            }
        }
        return cleaned;
    }

    private static String composeSubQuery(String query, String suffix) {
        if (!StringUtils.hasText(query)) {
            return suffix;
        }
        if (!StringUtils.hasText(suffix)) {
            return query;
        }
        return query + " " + suffix;
    }

    private static String normalizeQuery(String query) {
        return normalizeText(query);
    }

    private static String normalizeLanguage(String language) {
        return StringUtils.hasText(language) ? language.trim() : "zh-CN";
    }

    private static String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
