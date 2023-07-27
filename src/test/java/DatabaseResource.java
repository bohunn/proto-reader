import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

public class DatabaseResource implements QuarkusTestResourceLifecycleManager {

    private static final DockerImageName imageName = DockerImageName.parse("gvenzl/oracle-xe")
            .asCompatibleSubstituteFor("oracle:18.4.0-xe");
    private static final OracleContainer ORACLE = new OracleContainer(imageName);

    @Override
    public Map<String, String> start() {
        ORACLE.start();
        return new HashMap<>(Map.of(
                "quarkus.datasource.jdbc.url", ORACLE.getJdbcUrl(),
                "quarkus.datasource.username", ORACLE.getUsername(),
                "quarkus.datasource.password", ORACLE.getPassword()
        ));
    }

    @Override
    public void stop() {
        ORACLE.stop();
    }
}

