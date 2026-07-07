package com.astray.insightflow.config;

import com.astray.insightflow.agent.extractor.ExtractorAgent;
import com.astray.insightflow.agent.extractor.StubExtractorAgent;
import com.astray.insightflow.agent.planner.PlannerAgent;
import com.astray.insightflow.agent.planner.StubPlannerAgent;
import com.astray.insightflow.agent.reviewer.ReviewerAgent;
import com.astray.insightflow.agent.reviewer.StubReviewerAgent;
import com.astray.insightflow.agent.verifier.StubVerifierAgent;
import com.astray.insightflow.agent.verifier.VerifierAgent;
import com.astray.insightflow.agent.writer.StubWriterAgent;
import com.astray.insightflow.agent.writer.WriterAgent;
import com.astray.insightflow.common.util.JsonUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class LangChainConfig {

    @Bean
    public PlannerAgent plannerAgent(OpenAiChatModelProperties properties) {
        if (!StringUtils.hasText(properties.apiKey())) {
            return new StubPlannerAgent();
        }
        return AiServices.builder(PlannerAgent.class)
                .chatModel(buildChatModel(properties))
                .build();
    }

    @Bean
    public ExtractorAgent extractorAgent(OpenAiChatModelProperties properties, JsonUtils jsonUtils) {
        if (!StringUtils.hasText(properties.apiKey())) {
            return new StubExtractorAgent(jsonUtils);
        }
        return AiServices.builder(ExtractorAgent.class)
                .chatModel(buildChatModel(properties))
                .build();
    }

    @Bean
    public VerifierAgent verifierAgent(OpenAiChatModelProperties properties, JsonUtils jsonUtils) {
        if (!StringUtils.hasText(properties.apiKey())) {
            return new StubVerifierAgent(jsonUtils);
        }
        return AiServices.builder(VerifierAgent.class)
                .chatModel(buildChatModel(properties))
                .build();
    }

    @Bean
    public WriterAgent writerAgent(OpenAiChatModelProperties properties, JsonUtils jsonUtils) {
        if (!StringUtils.hasText(properties.apiKey())) {
            return new StubWriterAgent(jsonUtils);
        }
        return AiServices.builder(WriterAgent.class)
                .chatModel(buildChatModel(properties))
                .build();
    }

    @Bean
    public ReviewerAgent reviewerAgent(OpenAiChatModelProperties properties, JsonUtils jsonUtils) {
        if (!StringUtils.hasText(properties.apiKey())) {
            return new StubReviewerAgent(jsonUtils);
        }
        return AiServices.builder(ReviewerAgent.class)
                .chatModel(buildChatModel(properties))
                .build();
    }

    private ChatModel buildChatModel(OpenAiChatModelProperties properties) {
        return OpenAiChatModel.builder()
                .baseUrl(properties.baseUrl())
                .apiKey(properties.apiKey())
                .modelName(properties.modelName())
                .temperature(properties.temperature())
                .maxCompletionTokens(properties.maxCompletionTokens())
                .timeout(properties.timeout())
                .maxRetries(properties.maxRetries())
                .strictJsonSchema(properties.strictJsonSchema())
                .logRequests(properties.logRequests())
                .logResponses(properties.logResponses())
                .build();
    }
}
