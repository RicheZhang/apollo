package io.logz.apollo;

import io.logz.apollo.clients.ApolloTestClient;
import io.logz.apollo.exceptions.ApolloClientException;
import io.logz.apollo.helpers.Common;
import io.logz.apollo.helpers.Fabric8TestMethods;
import io.logz.apollo.helpers.ModelsGenerator;
import io.logz.apollo.helpers.RealDeploymentGenerator;
import io.logz.apollo.helpers.StandaloneApollo;
import io.logz.apollo.kubernetes.ApolloToKubernetes;
import io.logz.apollo.kubernetes.ApolloToKubernetesStore;
import io.logz.apollo.models.*;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;
import org.rapidoid.commons.Str;

import java.util.HashMap;
import java.util.Optional;
import java.util.List;

import static io.logz.apollo.helpers.ModelsGenerator.createAndSubmitDeployment;
import static io.logz.apollo.helpers.ModelsGenerator.createAndSubmitEnvironment;
import static io.logz.apollo.helpers.ModelsGenerator.createAndSubmitService;
import static io.logz.apollo.helpers.ModelsGenerator.createAndSubmitDeployableVersion;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by roiravhon on 1/5/17.
 */
public class DeploymentTest {

    private static ApolloTestClient apolloTestClient;
    private static ApolloToKubernetesStore apolloToKubernetesStore;
    private static StandaloneApollo standaloneApollo;

    @BeforeClass
    public static void init() throws Exception {
        standaloneApollo = StandaloneApollo.getOrCreateServer();
        apolloTestClient = Common.signupAndLogin();
        apolloToKubernetesStore = standaloneApollo.getInstance(ApolloToKubernetesStore.class);
    }

    @Test
    public void testGetAndAddDeployment() throws Exception {

        Deployment testDeployment = createAndSubmitDeployment(apolloTestClient);

        Deployment returnedDeployment = apolloTestClient.getDeployment(testDeployment.getId());

        assertThat(returnedDeployment.getEnvironmentId()).isEqualTo(testDeployment.getEnvironmentId());
        assertThat(returnedDeployment.getServiceId()).isEqualTo(testDeployment.getServiceId());
        assertThat(returnedDeployment.getDeployableVersionId()).isEqualTo(testDeployment.getDeployableVersionId());
        assertThat(returnedDeployment.getUserEmail()).isEqualTo(apolloTestClient.getTestUser().getUserEmail());
        assertThat(returnedDeployment.getStatus()).isEqualTo(Deployment.DeploymentStatus.PENDING);
        assertThat(returnedDeployment.getDeploymentMessage()).isNotBlank();
    }

    @Test
    public void testGetAllDeployments() throws Exception {

        Deployment testDeployment = createAndSubmitDeployment(apolloTestClient);

        Optional<Deployment> deploymentFromApi = apolloTestClient.getAllDeployments().stream()
                .filter(deployment -> deployment.getId() == testDeployment.getId()).findFirst();

        boolean found = false;

        if (deploymentFromApi.isPresent()) {
            if (deploymentFromApi.get().getEnvironmentId() == testDeployment.getEnvironmentId() &&
                    deploymentFromApi.get().getServiceId() == testDeployment.getServiceId() &&
                    deploymentFromApi.get().getDeployableVersionId() == testDeployment.getDeployableVersionId() &&
                    deploymentFromApi.get().getStatus().toString().equals(Deployment.DeploymentStatus.PENDING.toString())) {
                found = true;
            }
        }

        assertThat(found).isTrue();
    }

    @Test
    public void testGetDeploymentHistory() throws Exception {
        String emptySearchTerm = null;
        String randomSearchTerm = Common.randomStr(20);
        String globalSearchTerm = apolloTestClient.getTestUser().getUserEmail();

        Deployment testDeployment1 = createAndSubmitDeployment(apolloTestClient);
        Deployment testDeployment2 = createAndSubmitDeployment(apolloTestClient);

        verifyGetAllHistoryDeployments(emptySearchTerm, testDeployment2);
        verifyGetAllHistoryDeployments(globalSearchTerm, testDeployment2);
        verifyGetFilteredHistoryDeployments(randomSearchTerm, testDeployment2);
    }

    private void verifyGetAllHistoryDeployments(String searchTerm, Deployment testDeployment) throws ApolloClientException {
        int pageNumber = 1;
        int pageSize = 1;
        Boolean descending = true;

        DeploymentHistory deploymentsHistoryFromApi = apolloTestClient.getDeploymentsHistory(descending, pageNumber, pageSize, searchTerm);
        assertThat(deploymentsHistoryFromApi).isNotNull();

        Optional<DeploymentHistoryDetails> data = deploymentsHistoryFromApi.getData().stream()
                .filter(deployment -> deployment.getId() == testDeployment.getId()).findFirst();
        assertThat(data).isPresent();
        assertThat(deploymentsHistoryFromApi.getRecordsTotal()).isEqualTo(deploymentsHistoryFromApi.getRecordsFiltered());
        assertThat(deploymentsHistoryFromApi.getData().size()).isEqualTo(deploymentsHistoryFromApi.getRecordsTotal());
    }

    private void verifyGetFilteredHistoryDeployments(String searchTerm, Deployment testDeployment) throws ApolloClientException {
        int pageNumber = 1;
        int pageSize = 1;
        Boolean descending = true;

        DeploymentHistory deploymentsHistoryFromApi = apolloTestClient.getDeploymentsHistory(descending, pageNumber, pageSize, searchTerm);
        assertThat(deploymentsHistoryFromApi).isNotNull();

        assertThat(deploymentsHistoryFromApi.getRecordsTotal()).isNotEqualTo(deploymentsHistoryFromApi.getRecordsFiltered());
        assertThat(deploymentsHistoryFromApi.getData().size()).isEqualTo(deploymentsHistoryFromApi.getRecordsFiltered());
    }

    @Test
    public void testSimultaneousDeployments() throws Exception {

        Deployment deployment1 = createAndSubmitDeployment(apolloTestClient);

        // Submit that again to verify we can't run the same one twice
        MultiDeploymentResponseObject result = apolloTestClient.addDeployment(deployment1);
        assertThat(result.getUnsuccessful().size()).isEqualTo(1);
        assertThat(result.getSuccessful().size()).isEqualTo(0);

        // Just to make sure we are not blocking different deployments to run on the same time
        createAndSubmitDeployment(apolloTestClient);
    }

    @Test
    public void testMultiServiceDeployment() throws Exception {
        Environment env1 = createAndSubmitEnvironment(apolloTestClient);
        Environment env2 = createAndSubmitEnvironment(apolloTestClient);
        Service service1 = createAndSubmitService(apolloTestClient);
        Service service2 = createAndSubmitService(apolloTestClient);
        DeployableVersion deployableVersion1 = createAndSubmitDeployableVersion(apolloTestClient, service1);
        createAndSubmitDeployableVersion(apolloTestClient, service2, deployableVersion1.getGithubRepositoryUrl(), deployableVersion1.getGitCommitSha());

        String envIdsCsv = String.valueOf(env1.getId()) + "," + String.valueOf(env2.getId());
        String serviceIdsCsv = String.valueOf(service1.getId()) + "," + String.valueOf(service2.getId());

        ModelsGenerator.createAndSubmitPermissions(apolloTestClient, Optional.of(env1), Optional.empty(), DeploymentPermission.PermissionType.ALLOW);
        ModelsGenerator.createAndSubmitPermissions(apolloTestClient, Optional.of(env2), Optional.empty(), DeploymentPermission.PermissionType.ALLOW);

        MultiDeploymentResponseObject result = apolloTestClient.addDeployment(envIdsCsv, serviceIdsCsv, deployableVersion1.getId(), false);

        assertThat(result.getSuccessful().size()).isEqualTo(4);
        assertThat(result.getUnsuccessful().size()).isEqualTo(0);

        Deployment deployment = result.getSuccessful().get(0).getDeployment();
        assertThat(apolloTestClient.getDeployment(deployment.getId())).isNotNull();
    }

    @Test
    public void testDeploymentWithAdditionalParams() throws Exception {
        HashMap<String, Object> params = new HashMap<>();

        String deploymentValue = "deployment_value";
        String serviceValue = "service_value";
        String ingressValue = "ingress_value";

        params.put("image", "great image");
        params.put(deploymentValue, deploymentValue);
        params.put(serviceValue, serviceValue);
        params.put(ingressValue, ingressValue);

        Environment env = createAndSubmitEnvironment(apolloTestClient, new JSONObject(params).toString());

        RealDeploymentGenerator realDeploymentGenerator = new RealDeploymentGenerator
                ("{{ image }}", null, null, 0, null, null, env, null, true);

        Deployment deployment = realDeploymentGenerator.getDeployment();

        ApolloToKubernetes apolloToKubernetes = apolloToKubernetesStore.getOrCreateApolloToKubernetes(deployment);

        Fabric8TestMethods.assertDeploymentLabelExists(apolloToKubernetes.getKubernetesDeployment(),
                RealDeploymentGenerator.DEFAULT_DEPLOYMENT_LABLE_KEY, deploymentValue);
        Fabric8TestMethods.assertServiceLabelExists(apolloToKubernetes.getKubernetesService(),
                RealDeploymentGenerator.DEFAULT_SERVICE_LABLE_KEY, serviceValue);
        Fabric8TestMethods.assertIngressLabelExists(apolloToKubernetes.getKubernetesIngress(),
                RealDeploymentGenerator.DEFAULT_INGRESS_LABLE_KEY, ingressValue);
    }
}
