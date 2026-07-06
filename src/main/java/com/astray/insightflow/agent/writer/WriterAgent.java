package com.astray.insightflow.agent.writer;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface WriterAgent {

    @SystemMessage(fromResource = "prompts/writer-system.txt")
    @UserMessage(fromResource = "prompts/writer-user.txt")
    ReportDraft write(
            @V("query") String query,
            @V("language") String language,
            @V("planJson") String planJson,
            @V("evidenceJson") String evidenceJson
    );
}
