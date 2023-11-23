package com.github.bohunn.proto;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PackagingProcessor {
    
    public void createJarFromPackages(Path inputFolder, Path outputJar) throws IOException {
        // check if input folder exists
        if (!Files.exists(inputFolder)) {
            throw new IllegalArgumentException("Input folder does not exist: " + inputFolder);
        }

        // check if output folder exists
        if (!Files.exists(outputJar.getParent())) {
            // if not then create it
            Files.createDirectories(outputJar.getParent());
        }
        
        // create JAR file
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputJar.toFile()))) {
            Files.walk(inputFolder)
                .filter(Files::isRegularFile)
                .forEach(path -> addFileToJar(jos, path));
        }
    }

    // add file to JAR
    private void addFileToJar(JarOutputStream jos, Path path) {
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            JarEntry entry = new JarEntry(path.toString());
            jos.putNextEntry(entry);
 
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                jos.write(buffer, 0, length);
            }
            jos.closeEntry();
        } catch (IOException e) {
            throw new RuntimeException("Error adding file to JAR", e);
        }
    }

}