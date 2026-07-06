package com.astray.insightflow.agent.planner;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface PlannerAgent {

    @SystemMessage(fromResource = "prompts/planner-system.txt")
    @UserMessage(fromResource = "prompts/planner-user.txt")
    PlanResult plan(@V("query") String query, @V("language") String language);
}
