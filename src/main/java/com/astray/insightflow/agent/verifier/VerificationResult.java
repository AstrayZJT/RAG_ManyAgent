package com.astray.insightflow.agent.verifier;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class VerificationResult implements Serializable {

    private List<VerifiedClaim> claims = new ArrayList<>();
    private VerifyDecision decision = new VerifyDecision();

    public List<VerifiedClaim> getClaims() {
        return claims;
    }

    public void setClaims(List<VerifiedClaim> claims) {
        this.claims = claims == null ? new ArrayList<>() : claims;
    }

    public VerifyDecision getDecision() {
        return decision;
    }

    public void setDecision(VerifyDecision decision) {
        this.decision = decision == null ? new VerifyDecision() : decision;
    }
}
