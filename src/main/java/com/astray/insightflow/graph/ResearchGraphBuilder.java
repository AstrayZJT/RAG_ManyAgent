package com.astray.insightflow.graph;

import com.astray.insightflow.graph.node.PlannerNode;
import com.astray.insightflow.graph.node.WriteNode;
import com.astray.insightflow.graph.state.ResearchState;
import com.astray.insightflow.graph.subgraph.RetrievalSubgraphBuilder;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeActionWithConfig.node_async;

@Component
public class ResearchGraphBuilder {

    public static final String PLANNER = "planner";
    public static final String WRITE = "write";

    private final PlannerNode plannerNode;
    private final WriteNode writeNode;
    private final RetrievalSubgraphBuilder retrievalSubgraphBuilder;

    public ResearchGraphBuilder(PlannerNode plannerNode,
                                WriteNode writeNode,
                                RetrievalSubgraphBuilder retrievalSubgraphBuilder) {
        this.plannerNode = plannerNode;
        this.writeNode = writeNode;
        this.retrievalSubgraphBuilder = retrievalSubgraphBuilder;
    }

    public CompiledGraph<ResearchState> build() {
        try {
            StateGraph<ResearchState> stateGraph = new StateGraph<>(ResearchState.SCHEMA, ResearchState::new)
                    .addNode(PLANNER, node_async((state, config) -> plannerNode.execute(state)))
                    .addNode(WRITE, node_async((state, config) -> writeNode.execute(state)))
                    .addEdge(START, PLANNER);

            retrievalSubgraphBuilder.attach(stateGraph, PLANNER, WRITE)
                    .addEdge(WRITE, END);

            return stateGraph.compile(CompileConfig.builder()
                    .releaseThread(false)
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build research graph", exception);
        }
    }
}
