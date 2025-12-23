package com.footstone.audit.service.deployment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kubernetes Deployment Validation Tests
 * Tests validate Kubernetes YAML files and deployment configurations
 */
@DisplayName("Kubernetes Deployment Tests")
@Tag("deployment")
public class KubernetesDeploymentTest {

    private static final String K8S_DIR = "docs/deployment/k8s";
    private static final String PROJECT_ROOT = System.getProperty("user.dir", ".").replace("/sql-audit-service/sql-audit-service-web", "");

    @Test
    @DisplayName("Test K8s StatefulSet should deploy")
    public void testK8s_StatefulSet_shouldDeploy() throws Exception {
        Path statefulSetPath = Path.of(PROJECT_ROOT, K8S_DIR, "statefulset.yaml");
        assertTrue(Files.exists(statefulSetPath), "StatefulSet YAML file should exist");

        String content = Files.readString(statefulSetPath);

        // Validate basic structure
        assertTrue(content.contains("kind: StatefulSet"), "Should be a StatefulSet");
        assertTrue(content.contains("name: sql-audit-service"), "Should have correct name");
        assertTrue(content.contains("replicas: 3"), "Should have 3 replicas");

        // Validate container spec
        assertTrue(content.contains("image: sql-audit-service"), "Should have image reference");
        assertTrue(content.contains("containerPort: 8090"), "Should expose port 8090");
        assertTrue(content.contains("containerPort: 8091"), "Should expose port 8091 for actuator");

        // Validate probes
        assertTrue(content.contains("livenessProbe"), "Should have liveness probe");
        assertTrue(content.contains("readinessProbe"), "Should have readiness probe");
        assertTrue(content.contains("/actuator/health/liveness"), "Should use correct liveness path");
        assertTrue(content.contains("/actuator/health/readiness"), "Should use correct readiness path");
    }

    @Test
    @DisplayName("Test K8s multiple replicas should scale")
    public void testK8s_multipleReplicas_shouldScale() throws Exception {
        Path statefulSetPath = Path.of(PROJECT_ROOT, K8S_DIR, "statefulset.yaml");
        String content = Files.readString(statefulSetPath);

        // Should support scaling
        assertTrue(content.contains("replicas: 3"), "Should have multiple replicas");
        assertTrue(content.contains("podAntiAffinity"), "Should have pod anti-affinity for HA");
    }

    @Test
    @DisplayName("Test K8s ConfigMap should mount")
    public void testK8s_ConfigMap_shouldMount() throws Exception {
        Path configMapPath = Path.of(PROJECT_ROOT, K8S_DIR, "configmap.yaml");
        assertTrue(Files.exists(configMapPath), "ConfigMap YAML should exist");

        String content = Files.readString(configMapPath);

        // Validate ConfigMap structure
        assertTrue(content.contains("kind: ConfigMap"), "Should be a ConfigMap");
        assertTrue(content.contains("name: audit-config"), "Should have correct name");

        // Validate key configurations
        assertTrue(content.contains("kafka.bootstrap.servers"), "Should have Kafka config");
        assertTrue(content.contains("kafka.consumer.group"), "Should have consumer group");
        assertTrue(content.contains("checker.slowQuery.threshold"), "Should have checker config");
    }

    @Test
    @DisplayName("Test K8s Secret should inject")
    public void testK8s_Secret_shouldInject() throws Exception {
        Path secretPath = Path.of(PROJECT_ROOT, K8S_DIR, "secret.yaml");
        assertTrue(Files.exists(secretPath), "Secret YAML should exist");

        String content = Files.readString(secretPath);

        // Validate Secret structure
        assertTrue(content.contains("kind: Secret"), "Should be a Secret");
        assertTrue(content.contains("name: audit-secrets"), "Should have correct name");
        assertTrue(content.contains("type: Opaque"), "Should be Opaque type");

        // Validate secret keys (not values, for security)
        assertTrue(content.contains("postgres.url"), "Should have postgres URL key");
        assertTrue(content.contains("postgres.username"), "Should have postgres username key");
        assertTrue(content.contains("postgres.password"), "Should have postgres password key");
        assertTrue(content.contains("clickhouse.url"), "Should have clickhouse URL key");
    }

    @Test
    @DisplayName("Test K8s PersistentVolumeClaim should provision")
    public void testK8s_PersistentVolumeClaim_shouldProvision() throws Exception {
        Path statefulSetPath = Path.of(PROJECT_ROOT, K8S_DIR, "statefulset.yaml");
        String content = Files.readString(statefulSetPath);

        // Validate PVC template
        assertTrue(content.contains("volumeClaimTemplates"), "Should have volume claim templates");
        assertTrue(content.contains("name: cache"), "Should have cache volume");
        assertTrue(content.contains("ReadWriteOnce"), "Should use ReadWriteOnce access mode");
        assertTrue(content.contains("storage: 10Gi"), "Should request storage");
    }

    @Test
    @DisplayName("Test K8s Service should expose")
    public void testK8s_Service_shouldExpose() throws Exception {
        Path servicePath = Path.of(PROJECT_ROOT, K8S_DIR, "service.yaml");
        assertTrue(Files.exists(servicePath), "Service YAML should exist");

        String content = Files.readString(servicePath);

        // Validate Service structure
        assertTrue(content.contains("kind: Service"), "Should be a Service");
        assertTrue(content.contains("name: sql-audit-service"), "Should have correct name");
        assertTrue(content.contains("type: ClusterIP"), "Should be ClusterIP type");

        // Validate ports
        assertTrue(content.contains("port: 8090"), "Should expose port 8090");
        assertTrue(content.contains("port: 8091"), "Should expose port 8091");
    }

    @Test
    @DisplayName("Test K8s Ingress should route")
    public void testK8s_Ingress_shouldRoute() throws Exception {
        Path ingressPath = Path.of(PROJECT_ROOT, K8S_DIR, "ingress.yaml");
        assertTrue(Files.exists(ingressPath), "Ingress YAML should exist");

        String content = Files.readString(ingressPath);

        // Validate Ingress structure
        assertTrue(content.contains("kind: Ingress"), "Should be an Ingress");
        assertTrue(content.contains("name: sql-audit-service"), "Should have correct name");

        // Validate TLS
        assertTrue(content.contains("tls:"), "Should have TLS configuration");
        assertTrue(content.contains("secretName: audit-tls"), "Should reference TLS secret");

        // Validate routing rules
        assertTrue(content.contains("host: audit.example.com"), "Should have host rule");
        assertTrue(content.contains("path: /"), "Should have path rule");
    }

    @Test
    @DisplayName("Test K8s health check should restart on failure")
    public void testK8s_healthCheck_shouldRestartOnFailure() throws Exception {
        Path statefulSetPath = Path.of(PROJECT_ROOT, K8S_DIR, "statefulset.yaml");
        String content = Files.readString(statefulSetPath);

        // Validate liveness probe configuration
        assertTrue(content.contains("livenessProbe"), "Should have liveness probe");
        assertTrue(content.contains("failureThreshold: 3"), "Should have failure threshold");
        assertTrue(content.contains("periodSeconds: 10"), "Should have period");

        // Validate readiness probe configuration
        assertTrue(content.contains("readinessProbe"), "Should have readiness probe");
    }

    @Test
    @DisplayName("Test K8s rolling update should zero downtime")
    public void testK8s_rollingUpdate_shouldZeroDowntime() throws Exception {
        Path statefulSetPath = Path.of(PROJECT_ROOT, K8S_DIR, "statefulset.yaml");
        String content = Files.readString(statefulSetPath);

        // StatefulSet provides ordered rolling updates
        // Check for pod management policy
        assertTrue(content.contains("kind: StatefulSet"), "Should use StatefulSet for ordered updates");

        // Check for pod disruption budget
        assertTrue(content.contains("PodDisruptionBudget"), "Should have PodDisruptionBudget");
        assertTrue(content.contains("minAvailable: 2"), "Should keep 2 pods available during updates");
    }

    @Test
    @DisplayName("Test K8s resource limits should enforce")
    public void testK8s_resourceLimits_shouldEnforce() throws Exception {
        Path statefulSetPath = Path.of(PROJECT_ROOT, K8S_DIR, "statefulset.yaml");
        String content = Files.readString(statefulSetPath);

        // Validate resource requests
        assertTrue(content.contains("requests:"), "Should have resource requests");
        assertTrue(content.contains("cpu: \"2\""), "Should request CPU");
        assertTrue(content.contains("memory: \"4Gi\""), "Should request memory");

        // Validate resource limits
        assertTrue(content.contains("limits:"), "Should have resource limits");
        assertTrue(content.contains("cpu: \"4\""), "Should limit CPU");
        assertTrue(content.contains("memory: \"8Gi\""), "Should limit memory");
    }

    @Test
    @DisplayName("Test K8s node affinity should schedule")
    public void testK8s_nodeAffinity_shouldSchedule() throws Exception {
        Path statefulSetPath = Path.of(PROJECT_ROOT, K8S_DIR, "statefulset.yaml");
        String content = Files.readString(statefulSetPath);

        // Validate pod anti-affinity for HA
        assertTrue(content.contains("podAntiAffinity"), "Should have pod anti-affinity");
        assertTrue(content.contains("preferredDuringSchedulingIgnoredDuringExecution"),
            "Should use preferred affinity for flexibility");
    }

    @Test
    @DisplayName("Test K8s pod disruption budget should respect")
    public void testK8s_podDisruptionBudget_shouldRespect() throws Exception {
        Path statefulSetPath = Path.of(PROJECT_ROOT, K8S_DIR, "statefulset.yaml");
        String content = Files.readString(statefulSetPath);

        // Validate PDB
        assertTrue(content.contains("kind: PodDisruptionBudget"), "Should have PodDisruptionBudget");
        assertTrue(content.contains("minAvailable: 2"), "Should maintain minimum available pods");
    }

    @Test
    @DisplayName("Test K8s horizontal pod autoscaler should scale")
    public void testK8s_horizontalPodAutoscaler_shouldScale() throws Exception {
        Path ingressPath = Path.of(PROJECT_ROOT, K8S_DIR, "ingress.yaml");
        String content = Files.readString(ingressPath);

        // Validate HPA
        assertTrue(content.contains("HorizontalPodAutoscaler"), "Should have HPA");
        assertTrue(content.contains("minReplicas: 3"), "Should have min replicas");
        assertTrue(content.contains("maxReplicas: 10"), "Should have max replicas");
        assertTrue(content.contains("averageUtilization: 70"), "Should scale on CPU utilization");
    }

    @Test
    @DisplayName("Test K8s network policy should isolate")
    public void testK8s_networkPolicy_shouldIsolate() throws Exception {
        Path ingressPath = Path.of(PROJECT_ROOT, K8S_DIR, "ingress.yaml");
        String content = Files.readString(ingressPath);

        // Validate Network Policy
        assertTrue(content.contains("kind: NetworkPolicy"), "Should have NetworkPolicy");
        assertTrue(content.contains("policyTypes:"), "Should define policy types");
        assertTrue(content.contains("Ingress"), "Should have ingress policy");
        assertTrue(content.contains("Egress"), "Should have egress policy");
    }

    @Test
    @DisplayName("Test K8s service account should authorize")
    public void testK8s_serviceAccount_shouldAuthorize() throws Exception {
        Path secretPath = Path.of(PROJECT_ROOT, K8S_DIR, "secret.yaml");
        String content = Files.readString(secretPath);

        // Validate ServiceAccount
        assertTrue(content.contains("kind: ServiceAccount"), "Should have ServiceAccount");
        assertTrue(content.contains("name: sql-audit-service"), "Should have correct SA name");

        // Validate RBAC
        assertTrue(content.contains("kind: Role"), "Should have Role");
        assertTrue(content.contains("kind: RoleBinding"), "Should have RoleBinding");
    }
}
