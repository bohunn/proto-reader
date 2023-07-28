package com.github.bohunn.proto;

import jakarta.inject.Singleton;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Paths;


@Singleton
public class GitHandler {

    @ConfigProperty(name = "github.repository.url")
    String githubRepositoryUrl;

    @ConfigProperty(name = "github.username")
    String githubUsername;

    @ConfigProperty(name = "github.password")
    String githubPassword;


    public void commitProtobufs() throws GitAPIException {
        Git git = Git.cloneRepository()
            .setURI(githubRepositoryUrl)
            .setDirectory(Paths.get("/model").toFile())
            .call();


        // add only the files from /model
        git.add().addFilepattern("model").call();
        git.commit().setMessage("Commit message").call();
        git.push()
            .setCredentialsProvider(new UsernamePasswordCredentialsProvider(githubUsername, githubPassword))
            .call();

    }

}