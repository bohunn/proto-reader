package com.github.bohunn.proto;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.xml.bind.DatatypeConverter;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Types;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

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

        try {
            moveProtoFiles();
        } catch (IOException e) {
            LOGGER.errorf(e, "Error moving proto files");
        }

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(query1);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            while (resultSet.next()) {
                String objTypeId = resultSet.getString("obj_type_id");
                String schema = getSchema(objTypeId);
                assert schema != null;
                processRow(objTypeId, schema.getBytes(StandardCharsets.UTF_8));
            }

        } catch (SQLException | IOException e) {
            LOGGER.errorf(e, "Error processing the query");
        }
    }

    private String getSchema(String objTypeId) throws SQLException {
        String query2 = getQuery("query2");
    
        try (Connection connection = dataSource.getConnection();
            CallableStatement callableStatement = connection.prepareCall(query2)) {

            BigInteger objTypeIdBigInt = new BigInteger(objTypeId);

            callableStatement.setObject(1, objTypeIdBigInt, Types.NUMERIC);
            callableStatement.registerOutParameter(1, Types.CLOB);
            callableStatement.execute();
    
            Clob clob = callableStatement.getClob(1);
            if (clob != null) {
                try (Reader reader = clob.getCharacterStream()) {
                    StringBuilder stringBuilder = new StringBuilder();
                    char[] buffer = new char[1024];
                    int bytesRead;
                    while ((bytesRead = reader.read(buffer)) != -1) {
                        stringBuilder.append(buffer, 0, bytesRead);
                    }
                    return stringBuilder.toString();
                } catch (IOException e) {
                    LOGGER.errorf(e, "Error reading the CLOB");
                    return null;
                }
            } else {
                return null;
            }
        }
    }

    private void processRow(String objType, byte[] protobufHex) throws IOException {
        String protobufStr = new String(DatatypeConverter.parseHexBinary(new String(protobufHex, StandardCharsets.UTF_8).substring(2)), StandardCharsets.UTF_8);
        String tempDirectory;
        if (System.getProperty("os.name").startsWith("Windows")) {
            tempDirectory = System.getProperty("user.dir");
        } else {
            tempDirectory = "/";
        }

        Path protoFilePath = Paths.get(tempDirectory, "temp", objType + ".proto");
        LOGGER.infof("Creating a .proto file: %s", objType);

        Files.createDirectories(protoFilePath.getParent());

        Files.writeString(protoFilePath, protobufStr);

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