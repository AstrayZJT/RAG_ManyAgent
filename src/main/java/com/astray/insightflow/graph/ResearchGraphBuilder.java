package com.astray.insightflow.graph;

import com.astray.insightflow.graph.node.ExtractNode;
import com.astray.insightflow.graph.node.PlannerNode;
import com.astray.insightflow.graph.node.ReviewNode;
import com.astray.insightflow.graph.node.VerifyNode;
import com.astray.insightflow.graph.node.WriteNode;
import com.astray.insightflow.graph.router.ReviewRouteDecider;
import com.astray.insightflow.graph.router.VerifyRouteDecider;
import com.astray.insightflow.graph.state.ResearchState;
import com.astray.insightflow.graph.subgraph.RetrievalSubgraphBuilder;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeActionWithConfig.node_async;

@Component
public class ResearchGraphBuilder {

    public static final String PLANNER = "planner";
    public static final String EXTRACT = "extract";
    public static final String VERIFY = "verify";
    public static final String WRITE = "write";
    public static final String REVIEW = "review";

    private final PlannerNode plannerNode;
    private final ExtractNode extractNode;
    private final VerifyNode verifyNode;
    private final WriteNode writeNode;
    private final ReviewNode reviewNode;
    private final RetrievalSubgraphBuilder retrievalSubgraphBuilder;
    private final VerifyRouteDecider verifyRouteDecider;
    private final ReviewRouteDecider reviewRouteDecider;

    public ResearchGraphBuilder(PlannerNode plannerNode,
                                ExtractNode extractNode,
                                VerifyNode verifyNode,
                                WriteNode writeNode,
                                ReviewNode reviewNode,
                                RetrievalSubgraphBuilder retrievalSubgraphBuilder,
                                VerifyRouteDecider verifyRouteDecider,
                                ReviewRouteDecider reviewRouteDecider) {
        this.plannerNode = plannerNode;
        this.extractNode = extractNode;
        this.verifyNode = verifyNode;
        this.writeNode = writeNode;
        this.reviewNode = reviewNode;
        this.retrievalSubgraphBuilder = retrievalSubgraphBuilder;
        this.verifyRouteDecider = verifyRouteDecider;
        this.reviewRouteDecider = reviewRouteDecider;
    }

    public CompiledGraph<ResearchState> build() {
        try {
            StateGraph<ResearchState> stateGraph = new StateGraph<>(ResearchState.SCHEMA, ResearchState::new)
                    .addNode(PLANNER, node_async((state, config) -> plannerNode.execute(state)))
                    .addNode(EXTRACT, node_async((state, config) -> extractNode.execute(state)))
                    .addNode(VERIFY, node_async((state, config) -> verifyNode.execute(state)))
                    .addNode(WRITE, node_async((state, config) -> writeNode.execute(state)))
                    .addNode(REVIEW, node_async((state, config) -> reviewNode.execute(state)))
                    .addEdge(START, PLANNER);

            retrievalSubgraphBuilder.attach(stateGraph, PLANNER, EXTRACT)
                    .addEdge(EXTRACT, VERIFY)
                    .addConditionalEdges(
                            VERIFY,
                            edge_async(verifyRouteDecider::decide),
                            Map.of(
                                    VerifyRouteDecider.GO_RETRIEVAL, RetrievalSubgraphBuilder.RETRIEVE_INTERNAL,
                                    VerifyRouteDecider.GO_WRITE, WRITE
                            )
                    )
                    .addEdge(WRITE, REVIEW)
                    .addConditionalEdges(
                            REVIEW,
                            edge_async(reviewRouteDecider::decide),
                            Map.of(
                                    ReviewRouteDecider.APPROVED, END,
                                    ReviewRouteDecider.RERUN_RETRIEVAL, RetrievalSubgraphBuilder.RETRIEVE_INTERNAL,
                                    ReviewRouteDecider.RERUN_VERIFY, VERIFY
                            )
                    );

            return stateGraph.compile(CompileConfig.builder()
                    .releaseThread(false)
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build research graph", exception);
        }
    }
}
