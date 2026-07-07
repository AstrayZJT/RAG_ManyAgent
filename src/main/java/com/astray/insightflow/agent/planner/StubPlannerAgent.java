package com.astray.insightflow.agent.planner;

public class StubPlannerAgent implements PlannerAgent {

    @Override
    public PlanResult plan(String query, String language) {
        return PlannerPlanTemplates.buildFallbackPlan(query, language);
    }
}
