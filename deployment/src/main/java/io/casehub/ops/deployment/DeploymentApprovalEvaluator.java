package io.casehub.ops.deployment;

import io.casehub.desiredstate.api.DesiredNode;
import io.casehub.desiredstate.api.StepAction;
import io.casehub.ops.api.approval.ApprovalDecision;
import io.casehub.ops.api.approval.ApprovalEvaluator;
import io.casehub.ops.api.approval.ApprovalPlan;
import io.casehub.ops.api.approval.ApprovalThresholds;
import io.casehub.ops.api.approval.RiskClassification;
import io.casehub.ops.api.deployment.AgentNodeSpec;
import io.casehub.ops.api.deployment.CaseHubApplicationSpec;
import io.casehub.ops.api.deployment.CaseTypeNodeSpec;
import io.casehub.ops.api.deployment.ChannelNodeSpec;
import io.casehub.ops.api.deployment.DeploymentNodeSpec;
import io.casehub.ops.api.deployment.DetectionNodeSpec;
import io.casehub.ops.api.deployment.EndpointNodeSpec;
import io.casehub.ops.api.deployment.PoolNodeSpec;
import io.casehub.ops.api.deployment.TrustPolicyNodeSpec;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DeploymentApprovalEvaluator implements ApprovalEvaluator {

    private static final ApprovalThresholds THRESHOLDS =
            new ApprovalThresholds(RiskClassification.HIGH);

    @Override
    public ApprovalDecision evaluate(DesiredNode node, StepAction action, String tenancyId) {
        if (!(node.spec() instanceof DeploymentNodeSpec spec)) {
            return new ApprovalDecision.AutoApproved();
        }

        RiskClassification risk = classifyRisk(spec);
        if (!THRESHOLDS.requiresApproval(risk)) {
            return new ApprovalDecision.AutoApproved();
        }

        var plan = new ApprovalPlan(
                node.id(), action, risk,
                generateSummary(spec, action),
                tenancyId, node.spec(), null);
        return new ApprovalDecision.RequiresApproval(plan);
    }

    private RiskClassification classifyRisk(DeploymentNodeSpec spec) {
        return switch (spec) {
            case TrustPolicyNodeSpec s -> RiskClassification.HIGH;
            case CaseHubApplicationSpec s -> RiskClassification.HIGH;
            case AgentNodeSpec s -> RiskClassification.MEDIUM;
            case ChannelNodeSpec s -> RiskClassification.LOW;
            case CaseTypeNodeSpec s -> RiskClassification.LOW;
            case EndpointNodeSpec s -> RiskClassification.LOW;
            case DetectionNodeSpec s -> RiskClassification.LOW;
            case PoolNodeSpec s -> RiskClassification.LOW;
        };
    }

    private String generateSummary(DeploymentNodeSpec spec, StepAction action) {
        String verb = action == StepAction.PROVISION ? "Provision" : "Deprovision";
        return switch (spec) {
            case TrustPolicyNodeSpec s -> verb + " trust policy '" + s.capability()
                                          + "': threshold=" + s.threshold() + ", blendFactor=" + s.blendFactor();
            case AgentNodeSpec s -> verb + " agent '" + s.agentId()
                                    + "' (" + s.provider() + "/" + s.modelFamily() + ")";
            case ChannelNodeSpec s -> verb + " channel '" + s.name() + "'";
            case CaseTypeNodeSpec s -> verb + " case type '" + s.namespace() + "/" + s.name() + "'";
            case EndpointNodeSpec s -> verb + " endpoint '" + s.path() + "'";
            case DetectionNodeSpec s -> verb + " detection '" + s.situationId() + "'";
            case PoolNodeSpec s -> verb + " pool '" + s.agentId() + "' (backend=" + s.backend() + ")";
            case CaseHubApplicationSpec s -> verb + " application '" + s.applicationId()
                                             + "' (image=" + (s.image() != null ? s.image() : "build:" + s.source()) + ")";
        };
    }
}
