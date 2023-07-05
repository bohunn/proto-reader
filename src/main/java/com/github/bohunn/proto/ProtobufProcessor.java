package com.github.bohunn.proto;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.xml.bind.DatatypeConverter;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.StreamSupport;

@ApplicationScoped
public class ProtobufProcessor {

    private static final Logger LOGGER = Logger.getLogger(ProtobufProcessor.class);

    @Inject
    PgPool client;

    private String getQuery(String queryName) {
        Config config = ConfigProvider.getConfig();
        String dbType = config.getValue("db.type", String.class);
        return config.getValue(dbType + ".sql." + queryName, String.class);
    }

    public void loadProtoFromDb() {
        String query1 = getQuery("query1");

        try{
            moveProtoFiles();
        } catch (IOException e) {
            LOGGER.errorf(e, "Error moving proto files");
        }

        client.query(query1).execute()
                .onItem().transformToMulti(rowSet ->
                        Multi.createFrom().items(() ->
                                StreamSupport.stream(rowSet.spliterator(), false)))
                .subscribe().with(row -> {
                    String objTypeId = row.getString("obj_type_id");
                    getSchema(objTypeId).subscribe().with(schema -> {
                        try {
                            processRow(objTypeId, schema.getBytes(StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            LOGGER.errorf(e, "Error processing obj_type_id: %s", objTypeId);
                        }
                    });
                });
    }

    private Uni<String> getSchema(String objTypeId) {
        String query2 = getQuery("query2");

        return client.preparedQuery(query2).execute(Tuple.of(objTypeId))
                .onItem().transform(RowSet::iterator)
                .onItem().transform(iterator -> iterator.hasNext() ? iterator.next().getString(0) : null);
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