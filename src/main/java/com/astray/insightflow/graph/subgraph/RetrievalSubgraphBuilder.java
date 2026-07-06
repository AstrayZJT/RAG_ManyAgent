package com.astray.insightflow.graph.subgraph;

import com.astray.insightflow.graph.node.MergeRerankNode;
import com.astray.insightflow.graph.node.RetrieveExternalNode;
import com.astray.insightflow.graph.node.RetrieveInternalNode;
import com.astray.insightflow.graph.state.ResearchState;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import static org.bsc.langgraph4j.action.AsyncNodeActionWithConfig.node_async;

@Component
public class RetrievalSubgraphBuilder {

    public static final String RETRIEVE_INTERNAL = "retrieveInternal";
    public static final String RETRIEVE_EXTERNAL = "retrieveExternal";
    public static final String MERGE_RERANK = "mergeRerank";

    private final RetrieveInternalNode retrieveInternalNode;
    private final RetrieveExternalNode retrieveExternalNode;
    private final MergeRerankNode mergeRerankNode;

    public RetrievalSubgraphBuilder(RetrieveInternalNode retrieveInternalNode,
                                    RetrieveExternalNode retrieveExternalNode,
                                    MergeRerankNode mergeRerankNode) {
        this.retrieveInternalNode = retrieveInternalNode;
        this.retrieveExternalNode = retrieveExternalNode;
        this.mergeRerankNode = mergeRerankNode;
    }

    public StateGraph<ResearchState> attach(StateGraph<ResearchState> graph, String previousNode, String nextNode)
            throws Exception {
        return graph
                .addNode(RETRIEVE_INTERNAL, node_async((state, config) -> retrieveInternalNode.execute(state)))
                .addNode(RETRIEVE_EXTERNAL, node_async((state, config) -> retrieveExternalNode.execute(state)))
                .addNode(MERGE_RERANK, node_async((state, config) -> mergeRerankNode.execute(state)))
                .addEdge(previousNode, RETRIEVE_INTERNAL)
                .addEdge(RETRIEVE_INTERNAL, RETRIEVE_EXTERNAL)
                .addEdge(RETRIEVE_EXTERNAL, MERGE_RERANK)
                .addEdge(MERGE_RERANK, nextNode);
    }
}
