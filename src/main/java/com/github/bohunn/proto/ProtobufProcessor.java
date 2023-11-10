package com.github.bohunn.proto;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import com.github.bohunn.model.QueryReturnType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;

@ApplicationScoped
public class ProtobufProcessor {

    @Inject
    AgroalDataSource dataSource;

    private static final Logger LOGGER = Logger.getLogger(ProtobufProcessor.class);

    private String getQuery(String queryName) {
        Config config = ConfigProvider.getConfig();
        String dbType = config.getValue("db.type", String.class);
        return config.getValue(dbType + ".sql." + queryName, String.class);
    }

    public void loadProtoFromDb() {
        String query1 = getQuery("query1");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(query1);
             ResultSet resultSet = preparedStatement.executeQuery()) {

            while (resultSet.next()) {
                int objTypeId = resultSet.getInt("obj_type_id");
                String schema = getSchema(objTypeId);
                if (schema != null) {
                    processRow(objTypeId, schema); // Pass the schema directly as a string
                } else {
                    LOGGER.errorf("Schema not found for obj_type_id: %d", objTypeId);
                }
            }
        } catch (SQLException | IOException e) {
            LOGGER.errorf(e, "Error processing the query");
        }
    }

    public void loadProtoFromDbWithType() {
        String query1 = getQuery("query1");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(query1);
             ResultSet resultSet = preparedStatement.executeQuery()) {

            while (resultSet.next()) {
                int objTypeId = resultSet.getInt("obj_type_id");
                QueryReturnType queryReturnType = getSchemaWithType(objTypeId);
                if (queryReturnType.getSchemaClob() != null) {
                    processRow(objTypeId, queryReturnType.getSchemaClob().getSubString(1, (int) queryReturnType.getSchemaClob().length())); // Pass the schema directly as a string
                } else {
                    LOGGER.errorf("Schema not found for obj_type_id: %d", objTypeId);
                }
            }
        } catch (SQLException | IOException e) {
            LOGGER.errorf(e, "Error processing the query");
        }        
    }

    private QueryReturnType getSchemaWithType(int objTypeId) {
        String query = getQuery("query2");
        QueryReturnType queryReturnType = new QueryReturnType();

        LOGGER.infof("Getting schema for obj_type_id: %d", objTypeId);

        try (Connection connection = dataSource.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            
            preparedStatement.setInt(1, objTypeId);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                ResultSetMetaData metaData = resultSet.getMetaData();

                if (resultSet.next()) {
                    if (metaData.getColumnCount() > 0) {
                        LOGGER.infof("Column count: %d", metaData.getColumnCount());
                        LOGGER.infof("Column name: %s, column type: %d", metaData.getColumnName(1), metaData.getColumnType(1));
                        QueryReturnType localQueryType = resultSet.getObject("clob", QueryReturnType.class);
                        LOGGER.infof("Returned query row: %v", localQueryType); 
                    } else {
                        LOGGER.infof("Column count: 0");
                    }
                }

                // if there are non-null values in the clob column, then the query returns a row
                // if (resultSet.next()) {
                    // LOGGER.infof("Query results: %s", resultSet);
                    // queryReturnType.setBdeIntlId(resultSet.getString("bde_intl_id"));
                    // queryReturnType.setSchemaClob(resultSet.getClob("clob"));
                // }
            }
        } catch (SQLException e) {
            LOGGER.errorf(e, "Error processing the query");
        }

        return queryReturnType;
    }

    private String getSchema(int objTypeId) throws SQLException {
        String query = getQuery("query2");
        String clobValue = null;

        LOGGER.infof("Getting schema for obj_type_id: %d", objTypeId);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(query)) {

            preparedStatement.setInt(1, objTypeId);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    Clob clob = resultSet.getClob("clob");
                    if (clob != null) {
                        try (Reader reader = clob.getCharacterStream()) {
                            StringBuilder stringBuilder = new StringBuilder();
                            char[] buffer = new char[1024];
                            int bytesRead;
                            while ((bytesRead = reader.read(buffer)) != -1) {
                                stringBuilder.append(buffer, 0, bytesRead);
                            }
                            clobValue = stringBuilder.toString();
                        } catch (IOException e) {
                            throw new SQLException(e);
                        }
                    }
                }
            }
        }

        return clobValue;
    }

    private void processRow(int objType, String schema) throws IOException {
        String tempDirectory;
        if (System.getProperty("os.name").startsWith("Windows")) {
            tempDirectory = System.getProperty("user.dir");
        } else {
            tempDirectory = "/";
        }

        Path protoFilePath = Paths.get(tempDirectory, "temp", objType + ".proto");
        LOGGER.infof("Creating a .proto file: %s", objType);

        Files.createDirectories(protoFilePath.getParent());

        Files.writeString(protoFilePath, schema);

        Path tempDirPath;
        Path tempJavaPath;
        String protoFileName = protoFilePath.getFileName().toString();
        if (System.getProperty("os.name").startsWith("Windows")) {
            // In Windows, replace the backslashes in the path string with forward slashes for protoc
            tempDirPath = Paths.get(protoFilePath.getParent().toString().replace("\\", "/")).toAbsolutePath();
            tempJavaPath = Paths.get(protoFilePath.getParent().toString().replace("\\", "/"), "model").toAbsolutePath();
        } else {
            // In Unix-based systems, just use the path as is
            tempDirPath = protoFilePath.getParent().toAbsolutePath();
            tempJavaPath = Paths.get(protoFilePath.getParent().toString(), "model").toAbsolutePath();
        }
        // create a model directory for the generated Java class
        Files.createDirectories(tempJavaPath);

        try {
            LOGGER.infof("Creating a Java class...");
            Process p = new ProcessBuilder("protoc", "-I=" + tempDirPath, "--java_out=" + tempJavaPath, protoFileName).start();

            try (BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
                 BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {

                String s;
                while ((s = stdInput.readLine()) != null) {
                    System.out.println(s);
                }

                while ((s = stdError.readLine()) != null) {
                    System.err.println(s);
                }
            } catch (Exception e) {
                LOGGER.errorf(e, "Error creating a Java class");
            }
        } catch (Exception e) {
            LOGGER.errorf(e, "Error creating a Java class");
        }
    }

    // method to move meta_model.proto and options.proto from resources folder to model folder
    public void moveProtoFiles() throws IOException {
        String tempDirectory;
        if (System.getProperty("os.name").startsWith("Windows")) {
            tempDirectory = System.getProperty("user.dir");
        } else {
            tempDirectory = "/";
        }

        Path protoFilePath = Paths.get(tempDirectory, "temp", "meta_model.proto");
        Path protoFilePath2 = Paths.get(tempDirectory, "temp", "options.proto");
        Path tempJavaPath;
        if (System.getProperty("os.name").startsWith("Windows")) {
            // In Windows, replace the backslashes in the path string with forward slashes for protoc
            tempJavaPath = Paths.get(protoFilePath.getParent().toString().replace("\\", "/"), "model").toAbsolutePath();
        } else {
            // In Unix-based systems, just use the path as is
            tempJavaPath = Paths.get(protoFilePath.getParent().toString(), "model").toAbsolutePath();
        }
        // create a model directory for the generated Java class
        Files.createDirectories(tempJavaPath);

        LOGGER.infof("Moving meta_model.proto and options.proto to model folder...");
        Files.move(protoFilePath, Paths.get(tempJavaPath.toString(), "meta_model.proto"));
        Files.move(protoFilePath2, Paths.get(tempJavaPath.toString(), "options.proto"));
    }
}