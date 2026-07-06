package com.astray.insightflow.graph.subgraph;

import com.astray.insightflow.graph.node.MergeRerankNode;
import com.astray.insightflow.graph.node.RetrieveExternalNode;
import com.astray.insightflow.graph.node.RetrieveInternalNode;
import com.astray.insightflow.graph.router.InternalRouteDecider;
import com.astray.insightflow.graph.state.ResearchState;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeActionWithConfig.node_async;

@Component
public class RetrievalSubgraphBuilder {

    public static final String RETRIEVE_INTERNAL = "retrieveInternal";
    public static final String RETRIEVE_EXTERNAL = "retrieveExternal";
    public static final String MERGE_RERANK = "mergeRerank";

    private final RetrieveInternalNode retrieveInternalNode;
    private final RetrieveExternalNode retrieveExternalNode;
    private final MergeRerankNode mergeRerankNode;
    private final InternalRouteDecider internalRouteDecider;

    public RetrievalSubgraphBuilder(RetrieveInternalNode retrieveInternalNode,
                                    RetrieveExternalNode retrieveExternalNode,
                                    MergeRerankNode mergeRerankNode,
                                    InternalRouteDecider internalRouteDecider) {
        this.retrieveInternalNode = retrieveInternalNode;
        this.retrieveExternalNode = retrieveExternalNode;
        this.mergeRerankNode = mergeRerankNode;
        this.internalRouteDecider = internalRouteDecider;
    }

    public StateGraph<ResearchState> attach(StateGraph<ResearchState> graph, String previousNode, String nextNode)
            throws Exception {
        return graph
                .addNode(RETRIEVE_INTERNAL, node_async((state, config) -> retrieveInternalNode.execute(state)))
                .addNode(RETRIEVE_EXTERNAL, node_async((state, config) -> retrieveExternalNode.execute(state)))
                .addNode(MERGE_RERANK, node_async((state, config) -> mergeRerankNode.execute(state)))
                .addEdge(previousNode, RETRIEVE_INTERNAL)
                .addConditionalEdges(
                        RETRIEVE_INTERNAL,
                        edge_async(internalRouteDecider::decide),
                        Map.of(
                                InternalRouteDecider.SEARCH_EXTERNAL, RETRIEVE_EXTERNAL,
                                InternalRouteDecider.SKIP_EXTERNAL, MERGE_RERANK
                        )
                )
                .addEdge(RETRIEVE_EXTERNAL, MERGE_RERANK)
                .addEdge(MERGE_RERANK, nextNode);
    }
}
