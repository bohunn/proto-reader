package com.github.bohunn.proto;

import io.quarkus.vertx.ConsumeEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class EventConsumer {

    @ConfigProperty(name = "schema.registry.upload.enabled", defaultValue = "false")
    boolean schemaRegistryUploadEnabled;

    @Inject
    ProtobufProcessor protobufProcessor;

    @Inject
    SchemaRegistryHandler schemaRegistryHandler;

    @ConsumeEvent(value = "process-protobufs", blocking = true)
    public void process(String message) {
        protobufProcessor.loadProtoFromDb();

        if (schemaRegistryUploadEnabled) {
            schemaRegistryHandler.uploadSchemas();
        }
    }

    @ConsumeEvent(value =  "process-protobufs-with-type", blocking = true)
    public void processWithType(String message) {
        protobufProcessor.loadProtoFromDbWithType();

        if (schemaRegistryUploadEnabled) {
            schemaRegistryHandler.uploadSchemas();
        }
    }
}
