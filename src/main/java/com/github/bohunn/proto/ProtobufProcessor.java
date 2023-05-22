package com.github.bohunn.proto;

import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.mutiny.Multi;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.StreamSupport;

@ApplicationScoped
public class ProtobufProcessor {

    @Inject
    PgPool client;

    @ConsumeEvent(value = "process-protobufs", blocking = true)
    public void process(String message) throws IOException, GitAPIException {
        client.query("SELECT obj_type, protobuf FROM public.protobuf_table").execute()
                .onItem().transformToMulti(rowSet ->
                        Multi.createFrom().items(() ->
                                StreamSupport.stream(rowSet.spliterator(), false)))
                .subscribe().with(row -> {
                    try {
                        processRow(row);
                    } catch (Exception e) {
                        // Log or handle exception
                    }
                });
    }
    private void processRow(Row row) throws IOException, GitAPIException {
        String objType = row.getString("obj_type");
        byte[] protobuf = row.getBuffer("protobuf").getBytes();

        String tempDirectory;
        if (System.getProperty("os.name").startsWith("Windows")) {
            // App is running on Windows, use project directory for /temp
            tempDirectory = System.getProperty("user.dir");
        } else {
            // App is running on Linux, use root path for /temp
            tempDirectory = "/";
        }

        Path protoFilePath = Paths.get(tempDirectory, "temp", objType + ".proto");
        Files.createDirectories(protoFilePath.getParent());
        try (FileOutputStream fileOutputStream = new FileOutputStream(protoFilePath.toFile())) {
            fileOutputStream.write(protobuf);
        }

        Process p = new ProcessBuilder("protoc", "--java_out=" + tempDirectory, protoFilePath.toString()).start();

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


//        Git git = Git.open(Paths.get(protoFilePath.getRoot().toString()).toFile());
//        git.add().addFilepattern(".").call();
//        git.commit().setMessage("Commit message").call();
//        git.push()
//                .setCredentialsProvider(new UsernamePasswordCredentialsProvider("username", "password"))
//                .call();
    }
}

