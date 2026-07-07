package com.astray.insightflow.agent.extractor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ExtractResult implements Serializable {

    private List<ExtractedFact> items = new ArrayList<>();

    public List<ExtractedFact> getItems() {
        return items;
    }

    public void setItems(List<ExtractedFact> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }
}
