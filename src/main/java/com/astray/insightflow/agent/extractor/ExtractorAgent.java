package com.astray.insightflow.agent.extractor;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.List;

public interface ExtractorAgent {

    @SystemMessage(fromResource = "prompts/extractor-system.txt")
    @UserMessage(fromResource = "prompts/extractor-user.txt")
    List<ExtractedFact> extract(
            @V("query") String query,
            @V("language") String language,
            @V("planJson") String planJson,
            @V("evidenceJson") String evidenceJson
    );
}
