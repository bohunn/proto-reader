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


    private void checkProtoSchemasSubjects() {
        //check if wrappers.proto subject is already uploaded
        Client client = ClientBuilder.newClient();
        String response = client.target(schemaRegistryUrl + "/subjects")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(String.class);
        
        if (!response.contains("wrappers.proto")) {
            LOGGER.info("Uploading wrappers.proto schema");
            uploadProtoFileSchema("wrappers.proto");
        }

        if (!response.contains("options.proto")) {
            LOGGER.info("Uploading options.proto schema");
            uploadProtoFileSchema("options.proto");
        }

        if (!response.contains("meta_model.proto")) {
            LOGGER.info("Uploading meta_model.proto schema");
            uploadProtoFileSchema("meta_model.proto");
        }
    }

    // TODO: write a test that checks the schema registry naming etc.    
    private void uploadProtoFileSchema(String filename) {
        //method to upload the .proto file schema to the schema registry
        String tempDirectory;
        if (System.getProperty("os.name").startsWith("Windows")) {
            tempDirectory = System.getProperty("user.dir");
        } else {
            tempDirectory = "/";
        }

        Path tempPath = Paths.get(tempDirectory, "temp");
        try {
            Files.createDirectories(tempPath);
            // search for the filename .proto file
            try (Stream<Path> paths = Files.walk(tempPath)) {
                paths.filter(Files::isRegularFile)
                //filename matches regexp filename.proto
                .filter(p -> p.toString().matches(filename + "$"))
                .forEach(p -> {
                    try {
                        String schema = Files.readString(p);
                        JsonObject schemaJson = Json.createObjectBuilder()
                                .add("schema", schema)
                                .add("schemaType", "PROTOBUF")
                                .build();

                        LOGGER.infof("Uploading schema: %s", schemaJson.toString());
                        String subjectName = p.getFileName().toString();
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

    public void uploadSchemas() {
        if (schemaRegistryUploadEnabled) {
            checkProtoSchemasSubjects();
            
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

}
