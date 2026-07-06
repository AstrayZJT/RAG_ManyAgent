package com.astray.insightflow.config;

import com.astray.insightflow.agent.planner.PlannerAgent;
import com.astray.insightflow.agent.planner.StubPlannerAgent;
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
    public WriterAgent writerAgent(OpenAiChatModelProperties properties, JsonUtils jsonUtils) {
        if (!StringUtils.hasText(properties.apiKey())) {
            return new StubWriterAgent(jsonUtils);
        }
        return AiServices.builder(WriterAgent.class)
                .chatModel(buildChatModel(properties))
                .build();
    }

    private ChatModel buildChatModel(OpenAiChatModelProperties properties) {
        return OpenAiChatModel.builder()
                .baseUrl(properties.baseUrl())
                .apiKey(properties.apiKey())
                .modelName(properties.modelName())
                .logRequests(properties.logRequests())
                .logResponses(properties.logResponses())
                .build();
    }
}
