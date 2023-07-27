import io.agroal.api.AgroalDataSource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(DatabaseResource.class)
public class IntegrationTests {

    private static final DockerImageName imageName = DockerImageName.parse("gvenzl/oracle-xe")
            .asCompatibleSubstituteFor("oracle:18.4.0-xe");
    private static final OracleContainer oracle = new OracleContainer(imageName);

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

    @AfterAll
    public static void tearDown() {
        oracle.stop();
    }
}