package io.casehub.ops.testing;

import io.casehub.ops.api.compliance.ComplianceControlSpec;
import io.casehub.ops.api.compliance.EvidenceCollector;
import io.casehub.ops.api.compliance.EvidenceResult;

public class StubEvidenceCollector implements EvidenceCollector {

    private final String strategy;
    private EvidenceResult result = new EvidenceResult.Pass("stub evidence");

    public StubEvidenceCollector(String strategy) {
        this.strategy = strategy;
    }

    @Override
    public String strategy() {
        return strategy;
    }

    @Override
    public EvidenceResult collect(ComplianceControlSpec spec, String tenancyId) {
        return result;
    }

    public void setResult(EvidenceResult result) {
        this.result = result;
    }
}
