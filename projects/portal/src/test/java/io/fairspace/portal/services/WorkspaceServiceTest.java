package io.fairspace.portal.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.util.concurrent.ListenableFuture;
import hapi.chart.ChartOuterClass;
import hapi.chart.ConfigOuterClass;
import hapi.chart.MetadataOuterClass;
import hapi.release.ReleaseOuterClass;
import hapi.services.tiller.Tiller;
import io.fairspace.portal.errors.NotFoundException;
import io.fairspace.portal.model.Workspace;
import io.fairspace.portal.model.WorkspaceApp;
import io.fairspace.portal.services.releases.AppReleaseRequestBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.microbean.helm.ReleaseManager;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class WorkspaceServiceTest {
    public static final String APP_TYPE = "appType";
    private static final String domain = "example.com";

    @Mock
    private ReleaseManager releaseManager;
    @Mock
    private CachedReleaseList releaseList;
    @Mock
    private ChartRepo chartRepo;

    @Mock
    private AppReleaseRequestBuilder appReleaseRequestBuilder;

    ChartOuterClass.Chart.Builder workspaceChart = ChartOuterClass.Chart.newBuilder();
    ChartOuterClass.Chart.Builder appChart = ChartOuterClass.Chart.newBuilder();

    @Mock
    private ListenableFuture<Tiller.InstallReleaseResponse> installFuture;

    @Mock
    private ListenableFuture<Tiller.UpdateReleaseResponse> updateFuture;

    @Mock
    private ListenableFuture<Tiller.UninstallReleaseResponse> uninstallFuture;


    private WorkspaceService workspaceService;

    @Before
    public void setUp() throws IOException {
        var workspaceValues = Map.of("saturn", Map.of("persistence",  Map.of("key", "value")));
        Map<String, Map<String, ?>> defaultValues = Map.of("workspace", workspaceValues);

        when(chartRepo.get("workspace")).thenReturn(workspaceChart);
        when(chartRepo.contains("workspace")).thenReturn(true);
        when(chartRepo.get(APP_TYPE)).thenReturn(appChart);
        when(chartRepo.contains(APP_TYPE)).thenReturn(true);

        Map<String, AppReleaseRequestBuilder> appRequestBuilders = Map.of(APP_TYPE, appReleaseRequestBuilder);

        workspaceService = new WorkspaceService(releaseManager, releaseList, chartRepo, appRequestBuilders, domain, defaultValues);

        when(releaseManager.install(any(), any())).thenReturn(installFuture);
        when(releaseManager.update(any(), any())).thenReturn(updateFuture);
        when(releaseManager.uninstall(any())).thenReturn(uninstallFuture);

        Answer runFuture = invocation -> {
            Runnable callback = invocation.getArgument(0);
            Executor executor = invocation.getArgument(1);
            executor.execute(callback);
            return null;
        };

        doAnswer(runFuture).when(installFuture).addListener(any(), any());
        doAnswer(runFuture).when(updateFuture).addListener(any(), any());
        doAnswer(runFuture).when(uninstallFuture).addListener(any(), any());
    }

    @Test
    public void cacheIsInvalidatedAfterInstallation() throws IOException, InterruptedException {
        workspaceService.installWorkspace(Workspace.builder().id("test").build());
        Thread.sleep(100);

        // The cache is supposed to be invalidated immediately after installation starts,
        // and again when the installation finishes
        verify(releaseList, times(2)).invalidateCache();
    }

    @Test
    public void itSetsConfigurationOnWorkspaceInstallation() throws IOException {
        var ws = Workspace.builder()
                .id("test")
                .name("Test")
                .description("description")
                .logAndFilesVolumeSize(1)
                .databaseVolumeSize(2)
                .build();

        workspaceService.installWorkspace(ws);

        verify(releaseManager).install(
            argThat(request -> {
                if(!request.getName().equals(ws.getId())) {
                    return false;
                }

                try {
                    JsonNode releaseConfig = new ObjectMapper(new YAMLFactory()).readTree(request.getValues().getRaw());

                    // Check whether the defaultConfig is added
                    assertEquals("value", releaseConfig.with("saturn").with("persistence").get("key").asText());

                    // Check whether the parameters are correctly specified
                    assertEquals("1Gi", releaseConfig.with("saturn").with("persistence").with("files").get("size").asText());
                    assertEquals("2Gi", releaseConfig.with("saturn").with("persistence").with("database").get("size").asText());

                    // Check whether the domain is correctly set
                    assertEquals("test.example.com", releaseConfig.with("workspace").with("ingress").get("domain").asText());
                } catch (IOException e) {
                    System.err.println("Error parsing release configuration");
                    return false;
                }

                return true;
            }),
            eq(workspaceChart)
        );
    }

    @Test
    public void installApp() throws NotFoundException, IOException {
        var release = ReleaseOuterClass.Release.newBuilder().build();
        when(releaseList.getRelease("workspaceId")).thenReturn(Optional.of(release));
        var app = WorkspaceApp.builder()
                .id("app")
                .type(APP_TYPE)
                .build();

        var installReleaseRequest = Tiller.InstallReleaseRequest.newBuilder();
        when(appReleaseRequestBuilder.appInstall(release, app)).thenReturn(installReleaseRequest);

        workspaceService.installApp("workspaceId", app);

        verify(releaseManager).install(installReleaseRequest, appChart);
        verify(releaseManager, times(0)).update(any(), any());
    }

    @Test(expected = NotFoundException.class)
    public void installAppFailsForUnknownAppType() throws NotFoundException, IOException {
        var app = WorkspaceApp.builder()
                .id("app")
                .type("otherAppType")
                .build();

        workspaceService.installApp("workspaceId", app);
    }

    @Test(expected = NotFoundException.class)
    public void installAppFailsForUnknownWorkspace() throws NotFoundException, IOException {
        var release = ReleaseOuterClass.Release.newBuilder().build();
        when(releaseList.getRelease("workspaceId")).thenReturn(Optional.empty());
        var app = WorkspaceApp.builder()
                .id("app")
                .type(APP_TYPE)
                .build();

        workspaceService.installApp("workspaceId", app);
    }

    @Test
    public void installAppUpdatesWorkspace() throws NotFoundException, IOException {
        var release = ReleaseOuterClass.Release.newBuilder().build();
        when(releaseList.getRelease("workspaceId")).thenReturn(Optional.of(release));
        var app = WorkspaceApp.builder()
                .id("app")
                .type(APP_TYPE)
                .build();

        var installReleaseRequest = Tiller.InstallReleaseRequest.newBuilder();
        var updateReleaseRequest = Tiller.UpdateReleaseRequest.newBuilder();
        when(appReleaseRequestBuilder.appInstall(release, app)).thenReturn(installReleaseRequest);
        when(appReleaseRequestBuilder.shouldUpdateWorkspace()).thenReturn(true);
        when(appReleaseRequestBuilder.workspaceUpdateAfterAppInstall(release, app)).thenReturn(updateReleaseRequest);

        workspaceService.installApp("workspaceId", app);

        verify(releaseManager).install(installReleaseRequest, appChart);
        verify(releaseManager).update(updateReleaseRequest, workspaceChart);
    }

    @Test
    public void uninstallApp() throws NotFoundException, IOException {
        var release = ReleaseOuterClass.Release.newBuilder().build();

        when(releaseList.getRelease("workspaceId")).thenReturn(Optional.of(release));
        when(releaseList.getRelease("app")).thenReturn(Optional.of(getAppRelease(APP_TYPE)));

        var uninstallReleaseRequest = Tiller.UninstallReleaseRequest.newBuilder();
        when(appReleaseRequestBuilder.appUninstall(any())).thenReturn(uninstallReleaseRequest);

        workspaceService.uninstallApp("app");

        verify(releaseManager).uninstall(uninstallReleaseRequest.build());
        verify(releaseManager, times(0)).update(any(), any());
    }

    @Test(expected = NotFoundException.class)
    public void uninstallAppFailsForUnknownAppType() throws NotFoundException, IOException {
        var release = ReleaseOuterClass.Release.newBuilder().build();

        when(releaseList.getRelease("workspaceId")).thenReturn(Optional.of(release));
        when(releaseList.getRelease("app")).thenReturn(Optional.of(getAppRelease("unknownAppType")));

        workspaceService.uninstallApp("app");
    }

    @Test(expected = NotFoundException.class)
    public void uninstallAppFailsForUnknownWorkspace() throws NotFoundException, IOException {
        when(releaseList.getRelease("workspaceId")).thenReturn(Optional.empty());
        when(releaseList.getRelease("app")).thenReturn(Optional.of(getAppRelease(APP_TYPE)));

        workspaceService.uninstallApp("app");
    }

    @Test
    public void uninstallAppUpdatesWorkspace() throws NotFoundException, IOException {
        var release = ReleaseOuterClass.Release.newBuilder().build();

        when(releaseList.getRelease("workspaceId")).thenReturn(Optional.of(release));
        when(releaseList.getRelease("app")).thenReturn(Optional.of(getAppRelease(APP_TYPE)));

        var uninstallReleaseRequest = Tiller.UninstallReleaseRequest.newBuilder();
        var updateReleaseRequest = Tiller.UpdateReleaseRequest.newBuilder();
        when(appReleaseRequestBuilder.appUninstall(any())).thenReturn(uninstallReleaseRequest);
        when(appReleaseRequestBuilder.shouldUpdateWorkspace()).thenReturn(true);
        when(appReleaseRequestBuilder.workspaceUpdateAfterAppUninstall(eq(release), any())).thenReturn(updateReleaseRequest);

        workspaceService.uninstallApp("app");

        verify(releaseManager).uninstall(uninstallReleaseRequest.build());
        verify(releaseManager).update(eq(updateReleaseRequest), any());
    }

    private ReleaseOuterClass.Release getAppRelease(String appType) {
        var config = ConfigOuterClass.Config.newBuilder().setRaw("{\"workspace\": {\"id\": \"workspaceId\"}}").build();
        MetadataOuterClass.Metadata chartMetadata = MetadataOuterClass.Metadata.newBuilder().setName(appType).build();
        ChartOuterClass.Chart chart = ChartOuterClass.Chart.newBuilder().setMetadata(chartMetadata).build();
        return ReleaseOuterClass.Release.newBuilder().setConfig(config).setChart(chart).build();
    }

}
