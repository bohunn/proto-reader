package com.github.bohunn.proto;

import jakarta.inject.Singleton;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@Singleton
public class SchemaRegistryHandler {

    private static final Logger LOGGER = Logger.getLogger(SchemaRegistryHandler.class);

    @ConfigProperty(name = "schema.registry.url")
    String schemaRegistryUrl;

    @ConfigProperty(name = "schema.registry.upload.enabled", defaultValue = "false")
    boolean schemaRegistryUploadEnabled;

    public void uploadSchemas() {
        if (schemaRegistryUploadEnabled) {
            // checkProtoSchemasSubjects();
            
            String tempDirectory;
            if (System.getProperty("os.name").startsWith("Windows")) {
                tempDirectory = System.getProperty("user.dir");
            } else {
                tempDirectory = "/";
            }

            Path tempPath = Paths.get(tempDirectory, "temp");
            Client client = ClientBuilder.newClient();

            try {
                Files.createDirectories(tempPath);
                try (Stream<Path> paths = Files.walk(tempPath)) {
                    paths.filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".proto"))
                            .forEach(p -> {
                                try {
                                    String schema = Files.readString(p);
                                    // call method that will include wrappers.proto in schema
                                    schema = includeWrappersProto(schema);
                                    JsonObject schemaJson = Json.createObjectBuilder()
                                            .add("schema", schema)
                                            .add("schemaType", "PROTOBUF")
                                            .build();

                                    LOGGER.infof("Uploading schema: %s", schemaJson.toString());
                                    String subjectName = p.getFileName().toString().replace(".proto", "") + "-value";
                                    String response = client.target(schemaRegistryUrl + "/subjects/" + subjectName + "/versions")
                                            .request(MediaType.APPLICATION_JSON_TYPE)
                                            .post(Entity.json(schemaJson.toString()), String.class);

                                    System.out.println("Registry response: " + response);
                                } catch (IOException e) {
                                   LOGGER.errorf(e, "Error uploading schema"); 
                                }
                            });
                }
            } catch (IOException e) {
                LOGGER.errorf(e, "Error creating temp directory");
            }
        }
    }

    private String includeWrappersProto(String schema) {
        // method to include wrappers.proto in the schema
        String tempDirectory;
        if (System.getProperty("os.name").startsWith("Windows")) {
            tempDirectory = System.getProperty("user.dir");
        } else {
            tempDirectory = "/";
        }

        Path tempPath = Paths.get(tempDirectory, "temp");

        // read wrappers.proto file
        Path wrappersProtoPath = Paths.get(tempPath.toString(), "wrappers.proto");
        String wrappersProto = "";
        try {
            wrappersProto = Files.readString(wrappersProtoPath);
        } catch (IOException e) {
            LOGGER.errorf(e, "Error reading wrappers.proto file");
        }

        // read only messages from wrappers.proto schema
        String[] wrappersProtoMessages = wrappersProto.split("message");
        
        // include them include them in the schema
        for (String message : wrappersProtoMessages) {
            if (!message.isBlank()) {
                // remove the last } from the message
                message = message.substring(0, message.length() - 1);
                // add the message to the schema
                schema = schema + message + "\n}";
            }
        }

        return schema;
    }

}
