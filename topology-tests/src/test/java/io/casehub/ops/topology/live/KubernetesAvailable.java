package io.casehub.ops.topology.live;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.HttpURLConnection;
import java.net.URI;

@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(KubernetesAvailable.KubeCondition.class)
@interface KubernetesAvailable {

    class KubeCondition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            String kubeHost = System.getenv("KUBERNETES_SERVICE_HOST");
            if (kubeHost != null) {
                return ConditionEvaluationResult.enabled("Running inside K8s cluster");
            }
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create("http://localhost:8001/api").toURL().openConnection();
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                if (conn.getResponseCode() == 200) {
                    return ConditionEvaluationResult.enabled("K8s API reachable via kubectl proxy");
                }
            } catch (Exception ignored) {
            }
            return ConditionEvaluationResult.disabled("K8s API not reachable");
        }
    }
}
