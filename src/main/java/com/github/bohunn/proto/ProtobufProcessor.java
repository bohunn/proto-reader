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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.List;

@ApplicationScoped
public class ProtobufProcessor {

    @Inject
    AgroalDataSource dataSource;

    @Inject
    PackagingProcessor packagingProcessor;

    private static final Logger LOGGER = Logger.getLogger(ProtobufProcessor.class);

    private Path tempDirPath;

    private Path tempJavaPath;

    private Path jarOutputPath;

    private List<String> helperProtoFiles = List.of("options.proto", "wrappers.proto", "meta_model.proto");

    public ProtobufProcessor() {
        if (System.getProperty("os.name").startsWith("Windows")) {
            String userDir = System.getProperty("user.dir");
            this.tempDirPath = Paths.get(userDir, "temp").toAbsolutePath();
            this.tempJavaPath = Paths.get(userDir, "temp", "src/main/java").toAbsolutePath();
            this.jarOutputPath = Paths.get(userDir, "temp", "output_package", "proto.jar").toAbsolutePath();
        } else {
            this.tempDirPath = Paths.get("/", "temp").toAbsolutePath();
            this.tempJavaPath = Paths.get("/", "temp", "src/main/java").toAbsolutePath();
            this.jarOutputPath = Paths.get("/", "temp", "output_package", "proto.jar").toAbsolutePath();
        }
    }

    // get query from the configuration file
    private String getQuery(String queryName) {
        Config config = ConfigProvider.getConfig();
        String dbType = config.getValue("db.type", String.class);
        return config.getValue(dbType + ".sql." + queryName, String.class);
    }

    // load proto files from the database
    public void loadProtoFromDbWithType() throws IOException {
        String query1 = getQuery("query1");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(query1);
             ResultSet resultSet = preparedStatement.executeQuery()) {

            while (resultSet.next()) {
                Integer objTypeId = (resultSet.getObject("obj_type_id") != null) ? resultSet.getInt("obj_type_id") : null;
                Integer metaTypId = (resultSet.getObject("meta_typ_id") != null) ? resultSet.getInt("meta_typ_id") : null;

                LOGGER.infof("returned: obj_type_id: %d, meta_typ_id: %d", objTypeId, metaTypId);

                QueryReturnType queryReturnType = getSchemaWithType(objTypeId, metaTypId);
                if (queryReturnType.getSchemaClob() != null) {
                    processRow(queryReturnType); // Pass the schema as a QueryReturnType object
                } else {
                    LOGGER.errorf("Schema not found for obj_type_id: %d", objTypeId);
                }
            }

        } catch (SQLException e) {
            LOGGER.errorf(e, "Error processing the query");
        }   

        // create a java class for options, wrappers and meta_model proto files
        for (String protoFileName : helperProtoFiles) {
            generateJavaClass(tempDirPath, tempJavaPath, protoFileName);
        }

        // create a jar file from the generated Java classes
        packagingProcessor.createJarFromPackages(tempJavaPath, jarOutputPath); 

    }

    // get schema from the database - custom SQL type
    // see ./docs/type.sql
    private QueryReturnType getSchemaWithType(Integer objTypeId, Integer metaTypId) {
        String query = getQuery("query2");
        QueryReturnType queryReturnType = new QueryReturnType();

        LOGGER.infof("Getting schema for obj_type_id: %d, meta_typ_id: %d", objTypeId, metaTypId);

        try (Connection connection = dataSource.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            
            preparedStatement.setObject(1, objTypeId);
            preparedStatement.setObject(2, metaTypId);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                ResultSetMetaData metaData = resultSet.getMetaData();

                if (resultSet.next()) {
                    if (metaData.getColumnCount() > 0) {
                        Struct struct = resultSet.getObject("CLOB", Struct.class);
                        if (struct != null) {
                            QueryReturnType localQueryType = new QueryReturnType(struct);
                            LOGGER.infof("Returned query row: %s", localQueryType); 
                            return localQueryType;
                        }
                    } else {
                        LOGGER.infof("Column count: 0");
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.errorf(e, "Error processing the query");
        }

        return queryReturnType;
    }

    // process a row from the query result set - custom SQL type
    private void processRow(QueryReturnType entity) throws IOException {
        processRow(entity.getBdeIntlId(), entity.clobToString());        
    }

    // process a row from the query result set - single usage
    // !!! remember objType is the name of the .proto file !!!
    private void processRow(String objType, String schema) throws IOException {
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

        String protoFileName = protoFilePath.getFileName().toString();

        // create a model directory for the generated Java class
        // Files.createDirectories(tempJavaPath);

        // run protoc for the .proto file
        // generateJavaClass(tempDirPath, tempJavaPath, protoFileName);
    }

    // method to run protoc command
    private void generateJavaClass(Path protoPath, Path javaPath, String fileName) {
        try {
            LOGGER.infof("Creating a Java class for: %s", fileName);
            Process p = new ProcessBuilder("protoc", "-I=" + protoPath, "--java_out=" + javaPath, fileName).start();

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

}