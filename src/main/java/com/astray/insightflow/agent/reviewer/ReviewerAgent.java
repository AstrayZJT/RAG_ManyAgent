package com.astray.insightflow.agent.reviewer;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ReviewerAgent {

    @SystemMessage(fromResource = "prompts/reviewer-system.txt")
    @UserMessage(fromResource = "prompts/reviewer-user.txt")
    ReviewResult review(
            @V("query") String query,
            @V("language") String language,
            @V("reportJson") String reportJson,
            @V("claimsJson") String claimsJson,
            @V("evidenceJson") String evidenceJson,
            @V("loopCount") int loopCount
    );
}
