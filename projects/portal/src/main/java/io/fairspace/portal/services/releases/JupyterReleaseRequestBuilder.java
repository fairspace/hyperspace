package io.fairspace.portal.services.releases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hapi.release.ReleaseOuterClass;
import hapi.services.tiller.Tiller;
import io.fairspace.portal.model.WorkspaceApp;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static io.fairspace.portal.services.releases.ConfigHelper.toConfig;
import static io.fairspace.portal.utils.HelmUtils.createRandomString;
import static io.fairspace.portal.utils.HelmUtils.getReleaseConfig;
import static io.fairspace.portal.utils.JacksonUtils.*;

@Slf4j
public class JupyterReleaseRequestBuilder extends BaseAppReleaseRequestBuilder {
    public static final String DOMAIN_PREFIX = "jupyter";

    private Map<String, ?> defaultValues;

    public JupyterReleaseRequestBuilder(Map<String, ?> defaultValues) {
        this.defaultValues = defaultValues;
    }

    @Override
    public Tiller.InstallReleaseRequest.Builder appInstall(ReleaseOuterClass.Release workspaceRelease, WorkspaceApp workspaceApp) throws IOException {
        ObjectNode customValues = null;
        try {
            // Lookup information on the workspace
            JsonNode workspaceConfig = getReleaseConfig(workspaceRelease);
            var workspaceDomain = getWorkspaceDomain(workspaceConfig);

            // Initialize base app values for this workspace
            var appDomain = getAppDomain(workspaceDomain, DOMAIN_PREFIX);
            customValues = getAppValues(workspaceRelease.getName(), appDomain);

            var authConfig = customValues.with("jupyterhub").with("auth").with("custom").with("config");
            authConfig.put("client_id", workspaceConfig.with("hyperspace").with("keycloak").get("clientId").asText());
            authConfig.put("client_secret", workspaceConfig.with("hyperspace").with("keycloak").get("clientSecret").asText());
            authConfig.put("session_url", "https://" + workspaceDomain + "/api/v1/account/tokens");

            customValues.with("jupyterhub").with("singleuser").with("extraEnv").put("WORKSPACE_URL", "https://storage." + workspaceDomain);
            customValues.with("jupyterhub").with("hub").with("extraEnv").put("JUPYTERHUB_CRYPT_KEY", createRandomString(64));
            customValues.with("jupyterhub").with("proxy").put("secretToken", createRandomString(64));
        } catch(Exception e) {
            log.error("Error while configuring Jupyter installation for workspace {}", workspaceRelease.getName(), e);
            throw new IllegalStateException("Error configuring Jupyter", e);
        }

        var values = merge(valueToTree(defaultValues), customValues);

        return Tiller.InstallReleaseRequest.newBuilder()
                .setName(workspaceApp.getId())
                .setNamespace(workspaceApp.getId())
                .setValues(toConfig(values));
    }

    @Override
    public Tiller.UninstallReleaseRequest.Builder appUninstall(WorkspaceApp workspaceApp) {
        return Tiller.UninstallReleaseRequest.newBuilder()
                .setName(workspaceApp.getId())
                .setPurge(true);
    }

    @Override
    public Optional<Tiller.UpdateReleaseRequest.Builder> workspaceUpdateAfterAppInstall(ReleaseOuterClass.Release workspaceRelease, WorkspaceApp workspaceApp) throws IOException {
        // Lookup information on the workspace
        JsonNode workspaceConfig = getReleaseConfig(workspaceRelease);
        var workspaceDomain = getWorkspaceDomain(workspaceConfig);

        // Add the url for jupyter to the workspace configuration
        var appDomain = getAppDomain(workspaceDomain, DOMAIN_PREFIX);
        var customValues = createObjectNode();
        customValues.with("services").put("jupyterhub",  String.format("https://%s", appDomain));

        // Add pod annotation for mercury to ensure it will restart
        customValues.with("podAnnotations").with("mercury").put("commit",  "install-jupyter-" + createRandomString(5));

        return Optional.of(
                Tiller.UpdateReleaseRequest.newBuilder()
                        .setName(workspaceRelease.getName())
                        .setReuseValues(true)
                        .setValues(toConfig(customValues))
        );
    }

    @Override
    public Optional<Tiller.UpdateReleaseRequest.Builder> workspaceUpdateAfterAppUninstall(ReleaseOuterClass.Release workspaceRelease, WorkspaceApp workspaceApp) throws IOException {
        // Remove the url for jupyter from the workspace configuration
        var customValues = createObjectNode();
        customValues.with("services").put("jupyterhub", "");

        // Add pod annotation for mercury to ensure it will restart
        customValues.with("podAnnotations").with("mercury").put("commit",  "uninstall-jupyter-" + createRandomString(5));


        return Optional.of(
                Tiller.UpdateReleaseRequest.newBuilder()
                        .setName(workspaceRelease.getName())
                        .setReuseValues(true)
                        .setValues(toConfig(customValues))
        );
    }

}
