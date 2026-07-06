package com.astray.insightflow.agent.verifier;

import com.astray.insightflow.agent.extractor.ExtractedFact;
import com.astray.insightflow.common.util.JsonUtils;
import com.astray.insightflow.retrieval.model.Evidence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class StubVerifierAgent implements VerifierAgent {

    private final JsonUtils jsonUtils;

    public StubVerifierAgent(JsonUtils jsonUtils) {
        this.jsonUtils = jsonUtils;
    }

    @Override
    public VerificationResult verify(String query, String language, String factsJson, String evidenceJson, int loopCount) {
        FactListWrapper factsWrapper = jsonUtils.fromJson(factsJson, FactListWrapper.class);
        EvidenceListWrapper evidenceWrapper = jsonUtils.fromJson(evidenceJson, EvidenceListWrapper.class);

        Map<String, List<ExtractedFact>> factsByDimension = factsWrapper.getItems().stream()
                .collect(Collectors.groupingBy(ExtractedFact::getDimension, LinkedHashMap::new, Collectors.toList()));

        List<VerifiedClaim> claims = new ArrayList<>();
        int conflictCount = 0;
        for (Map.Entry<String, List<ExtractedFact>> entry : factsByDimension.entrySet()) {
            List<ExtractedFact> dimensionFacts = entry.getValue();
            dimensionFacts.sort(Comparator.comparingDouble(ExtractedFact::getConfidence).reversed());
            LinkedHashSet<String> supportIds = dimensionFacts.stream()
                    .map(ExtractedFact::getEvidenceId)
                    .filter(id -> id != null && !id.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            Map<String, Long> normalizedCounts = dimensionFacts.stream()
                    .collect(Collectors.groupingBy(ExtractedFact::getNormalizedValue, LinkedHashMap::new, Collectors.counting()));
            List<String> conflicts = normalizedCounts.entrySet().stream()
                    .filter(item -> item.getKey() != null && !item.getKey().isBlank() && item.getValue() == 1L)
                    .map(Map.Entry::getKey)
                    .limit(Math.max(0, dimensionFacts.size() - 1))
                    .toList();

            VerifiedClaim claim = new VerifiedClaim();
            claim.setId(UUID.randomUUID().toString());
            claim.setDimension(entry.getKey());
            claim.setClaimText(buildClaimText(entry.getKey(), dimensionFacts.get(0)));
            claim.setSupportingEvidenceIds(new ArrayList<>(supportIds));
            claim.setConflictingEvidenceIds(findConflictingEvidenceIds(conflicts, dimensionFacts));

            double confidence = 0.42D
                    + Math.min(0.28D, supportIds.size() * 0.16D)
                    + Math.min(0.10D, loopCount * 0.06D)
                    - Math.min(0.20D, claim.getConflictingEvidenceIds().size() * 0.08D);
            claim.setConfidenceScore(Math.max(0.20D, Math.min(0.95D, confidence)));
            claim.setLowConfidence(claim.getConfidenceScore() < 0.70D);
            if (!claim.getConflictingEvidenceIds().isEmpty()) {
                claim.setStatus(VerifiedClaimStatus.CONFLICTING);
                claim.setRationale("不同证据对该维度给出了不完全一致的描述，需要补证。");
                conflictCount += claim.getConflictingEvidenceIds().size();
            } else if (claim.getSupportingEvidenceIds().size() >= 2) {
                claim.setStatus(VerifiedClaimStatus.SUPPORTED);
                claim.setRationale("存在多条支持证据，可进入写作环节。");
            } else if (claim.getSupportingEvidenceIds().size() == 1) {
                claim.setStatus(VerifiedClaimStatus.PARTIAL);
                claim.setRationale("当前只有单条支持证据，建议补充验证。");
            } else {
                claim.setStatus(VerifiedClaimStatus.INSUFFICIENT);
                claim.setRationale("缺少支持证据，无法稳定成文。");
            }
            claims.add(claim);
        }

        double supportRatio = claims.isEmpty()
                ? 0D
                : claims.stream().filter(claim -> claim.getStatus() == VerifiedClaimStatus.SUPPORTED
                        || claim.getStatus() == VerifiedClaimStatus.PARTIAL).count() / (double) claims.size();

        VerifyDecision decision = new VerifyDecision();
        boolean enoughEvidence = evidenceWrapper.getItems().size() >= 2;
        decision.setReadyForWrite(!claims.isEmpty() && enoughEvidence && supportRatio >= 0.55D && conflictCount == 0);
        decision.setRerunRetrieval(!decision.isReadyForWrite());
        decision.setSupportRatio(supportRatio);
        decision.setConflictCount(conflictCount);
        decision.setLowConfidenceMode(!decision.isReadyForWrite());
        decision.setReason(decision.isReadyForWrite()
                ? "支持度达到阈值，可继续写作。"
                : "支持度或证据数量不足，建议回到检索阶段补证。");

        VerificationResult result = new VerificationResult();
        result.setClaims(claims);
        result.setDecision(decision);
        return result;
    }

    private String buildClaimText(String dimension, ExtractedFact fact) {
        return dimension + "维度显示：" + fact.getSubject() + " 的 " + fact.getAttribute() + " 为 " + fact.getValue();
    }

    private List<String> findConflictingEvidenceIds(List<String> conflicts, List<ExtractedFact> facts) {
        if (conflicts.isEmpty()) {
            return List.of();
        }
        return facts.stream()
                .filter(fact -> conflicts.contains(fact.getNormalizedValue()))
                .map(ExtractedFact::getEvidenceId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
    }

    public static class FactListWrapper {

        private List<ExtractedFact> items = new ArrayList<>();

        public List<ExtractedFact> getItems() {
            return items;
        }

        public void setItems(List<ExtractedFact> items) {
            this.items = items == null ? new ArrayList<>() : items;
        }
    }

    public static class EvidenceListWrapper {

        private List<Evidence> items = new ArrayList<>();

        public List<Evidence> getItems() {
            return items;
        }

        public void setItems(List<Evidence> items) {
            this.items = items == null ? new ArrayList<>() : items;
        }
    }
}
