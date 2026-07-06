package com.astray.insightflow.agent.verifier;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface VerifierAgent {

    @SystemMessage(fromResource = "prompts/verifier-system.txt")
    @UserMessage(fromResource = "prompts/verifier-user.txt")
    VerificationResult verify(
            @V("query") String query,
            @V("language") String language,
            @V("factsJson") String factsJson,
            @V("evidenceJson") String evidenceJson,
            @V("loopCount") int loopCount
    );
}
