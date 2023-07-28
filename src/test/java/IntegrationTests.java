import io.agroal.api.AgroalDataSource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.utility.DockerImageName;

import com.github.bohunn.proto.SchemaRegistryHandler;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

//@QuarkusTest
//@QuarkusTestResource(DatabaseResource.class)
public class IntegrationTests {

    private static final DockerImageName imageName = DockerImageName.parse("gvenzl/oracle-xe")
            .asCompatibleSubstituteFor("oracle:18.4.0-xe");
    private static final OracleContainer oracle = new OracleContainer(imageName);

    private static Logger LOGGER = Logger.getLogger(IntegrationTests.class);

    @Inject
    AgroalDataSource dataSource;

    @BeforeAll
    public static void init() {
        oracle.start();

        try (Connection connection = oracle.createConnection("");
             Statement statement = connection.createStatement()
        ) {
            // create table PROTO_SCHEMA
            statement.execute("CREATE TABLE PROTO_SCHEMA (obj_type_id NUMBER, schema CLOB)");

            // insert example protobuf schemas into PROTO_SCHEMA
            statement.execute("INSERT INTO PROTO_SCHEMA VALUES (1, 'syntax = \"proto3\"; message Person { string name = 1; int32 id = 2; repeated string email = 3; }')");
            statement.execute("INSERT INTO PROTO_SCHEMA VALUES (2, 'syntax = \"proto3\"; message AddressBook { repeated Person people = 1; }')");
            statement.execute("INSERT INTO PROTO_SCHEMA VALUES (3, 'syntax = \"proto3\"; message Person { string name = 1; int32 id = 2; repeated string email = 3; }')");
            statement.execute("INSERT INTO PROTO_SCHEMA VALUES (4, 'syntax = \"proto3\"; message AddressBook { repeated Person people = 1; }')");
            statement.execute("INSERT INTO PROTO_SCHEMA VALUES (5, 'syntax = \"proto3\"; message Person { string name = 1; int32 id = 2; repeated string email = 3; }')");
            // insert example.proto from .docs folder
            Path path = Paths.get("src", "test", "resources", "example.proto");
            try {
                String schema = new String(Files.readAllBytes(path));
                statement.execute("INSERT INTO PROTO_SCHEMA VALUES (6, '" + schema + "')");
                String sql = "INSERT INTO PROTO_SCHEMA (obj_type_id, schema) VALUES (6, ?)";
                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    Reader reader = new StringReader(schema);
                    pstmt.setCharacterStream(1, reader, schema.length());
                    pstmt.executeUpdate();
                }
            } catch (IOException e) {
                throw new RuntimeException("Error on reading example.proto", e);
            }

            // create table CODE_BDE_ENTITY
            statement.execute("CREATE TABLE CODE_BDE_ENTITY (obj_type_id NUMBER, activ VARCHAR2(10))");

            //insert rows into CODE_BDE_ENTITY
            for (int i = 1; i<= 5; i++) {
                statement.execute("INSERT INTO CODE_BDE_ENTITY VALUES (" + i + ", '+')");
            }

            statement.execute("CREATE OR REPLACE FUNCTION GET_SCHEMA(p_obj_type_id NUMBER) " +
                    "RETURN CLOB " +
                    "IS " +
                    "   v_schema CLOB; " +
                    "BEGIN " +
                    "   SELECT schema " +
                    "   INTO v_schema " +
                    "   FROM PROTO_SCHEMA " +
                    "   WHERE obj_type_id = p_obj_type_id; " +
                    "   RETURN v_schema; " +
                    "EXCEPTION " +
                    "   WHEN NO_DATA_FOUND THEN" +
                    "   RETURN NULL;" +
                    "END");
        } catch (SQLException e) {
            throw new RuntimeException("Error on table creation", e);
        }
    }

    @Test
    public void testReadingProtoSchema() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT GET_SCHEMA(1) FROM DUAL")
        ) {
            resultSet.next();
            String schema = resultSet.getString(1);
            assertEquals("syntax = \"proto3\"; message Person { string name = 1; int32 id = 2; repeated string email = 3; }", schema);
        } catch (SQLException e) {
            throw new RuntimeException("Error on Reading schema from a table", e);
        }
    }

    // test including wrappers.proto in the schema
    @Test
    public void testIncludingWrappers() {
        SchemaRegistryHandler schemaRegistryHandler = new SchemaRegistryHandler();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT GET_SCHEMA(6) FROM DUAL")
        ) {
            resultSet.next();
            String schema = resultSet.getString(1);
            LOGGER.info(schema);
            String included = schemaRegistryHandler.includeWrappersProto(schema);
            LOGGER.info(included);
            // assertEquals("syntax = \"proto3\"; import \"google/protobuf/wrappers.proto\"; message Person { string name = 1; int32 id = 2; repeated string email = 3; }", schema);
        } catch (SQLException e) {
            throw new RuntimeException("Error on Reading schema from a table", e);
        }
    }

    @AfterAll
    public static void tearDown() {
        oracle.stop();
    }
}