package ai.pipestream.account.util;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

public class WireMockTestResource implements QuarkusTestResourceLifecycleManager {

    private GenericContainer<?> wireMockContainer;

    @SuppressWarnings("resource")
    @Override
    public Map<String, String> start() {
        wireMockContainer = new GenericContainer<>(DockerImageName.parse("docker.io/pipestreamai/pipestream-wiremock-server:0.1.28"))
                .withExposedPorts(50052)
                .waitingFor(Wait.forLogMessage(".*Direct Streaming gRPC Server started.*", 1));
        
        wireMockContainer.start();

        return Map.of(
            "pipestream.registration.registration-service.host", wireMockContainer.getHost(),
            "pipestream.registration.registration-service.port", wireMockContainer.getMappedPort(50052).toString()
        );
    }

    @Override
    public void stop() {
        if (wireMockContainer != null) {
            wireMockContainer.stop();
        }
    }
}
