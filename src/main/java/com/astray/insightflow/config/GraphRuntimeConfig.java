package com.astray.insightflow.config;

import com.astray.insightflow.graph.ResearchGraphBuilder;
import com.astray.insightflow.graph.state.ResearchState;
import org.bsc.langgraph4j.CompiledGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphRuntimeConfig {

    @Bean
    public CompiledGraph<ResearchState> researchCompiledGraph(ResearchGraphBuilder researchGraphBuilder) {
        return researchGraphBuilder.build();
    }
}
