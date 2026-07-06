package com.astray.insightflow.graph.router;

import com.astray.insightflow.agent.reviewer.ReviewResult;
import com.astray.insightflow.agent.reviewer.ReviewRerunTarget;
import com.astray.insightflow.agent.verifier.VerifyDecision;
import com.astray.insightflow.config.WorkflowProperties;
import com.astray.insightflow.graph.state.ResearchState;
import com.astray.insightflow.retrieval.model.Evidence;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteDeciderTests {

    private final WorkflowProperties workflowProperties = new WorkflowProperties(2, 2, 0.70D, 0.80D, 2);

    @Test
    void internalRouteSkipsExternalWhenEvidenceEnough() {
        InternalRouteDecider decider = new InternalRouteDecider(workflowProperties);
        ResearchState state = new ResearchState(Map.of(
                ResearchState.NEED_EXTERNAL_SEARCH, true,
                ResearchState.INTERNAL_EVIDENCES, List.of(new Evidence(), new Evidence())
        ));

        assertEquals(InternalRouteDecider.SKIP_EXTERNAL, decider.decide(state));
    }

    @Test
    void internalRouteRequestsExternalWhenEvidenceInsufficient() {
        InternalRouteDecider decider = new InternalRouteDecider(workflowProperties);
        ResearchState state = new ResearchState(Map.of(
                ResearchState.NEED_EXTERNAL_SEARCH, true,
                ResearchState.INTERNAL_EVIDENCES, List.of(new Evidence())
        ));

        assertEquals(InternalRouteDecider.SEARCH_EXTERNAL, decider.decide(state));
    }

    @Test
    void verifyRouteReturnsRetrievalWhenDecisionFailsBeforeLoopLimit() {
        VerifyRouteDecider decider = new VerifyRouteDecider(workflowProperties);
        VerifyDecision decision = new VerifyDecision();
        decision.setReadyForWrite(false);
        decision.setRerunRetrieval(true);
        ResearchState state = new ResearchState(Map.of(
                ResearchState.VERIFY_DECISION, decision,
                ResearchState.LOOP_COUNT, 1
        ));

        assertEquals(VerifyRouteDecider.GO_RETRIEVAL, decider.decide(state));
    }

    @Test
    void verifyRouteFallsThroughToWriteAfterLoopLimit() {
        VerifyRouteDecider decider = new VerifyRouteDecider(workflowProperties);
        VerifyDecision decision = new VerifyDecision();
        decision.setReadyForWrite(false);
        decision.setRerunRetrieval(true);
        ResearchState state = new ResearchState(Map.of(
                ResearchState.VERIFY_DECISION, decision,
                ResearchState.LOOP_COUNT, 3
        ));

        assertEquals(VerifyRouteDecider.GO_WRITE, decider.decide(state));
    }

    @Test
    void reviewRouteSupportsVerifyAndRetrievalFallbacks() {
        ReviewRouteDecider decider = new ReviewRouteDecider(workflowProperties);

        ReviewResult verifyResult = new ReviewResult();
        verifyResult.setApproved(false);
        verifyResult.setRerunFrom(ReviewRerunTarget.VERIFY);
        ResearchState verifyState = new ResearchState(Map.of(
                ResearchState.REVIEW_RESULT, verifyResult,
                ResearchState.LOOP_COUNT, 1
        ));
        assertEquals(ReviewRouteDecider.RERUN_VERIFY, decider.decide(verifyState));

        ReviewResult retrievalResult = new ReviewResult();
        retrievalResult.setApproved(false);
        retrievalResult.setRerunFrom(ReviewRerunTarget.RETRIEVAL);
        ResearchState retrievalState = new ResearchState(Map.of(
                ResearchState.REVIEW_RESULT, retrievalResult,
                ResearchState.LOOP_COUNT, 1
        ));
        assertEquals(ReviewRouteDecider.RERUN_RETRIEVAL, decider.decide(retrievalState));
    }
}
