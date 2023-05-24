package com.github.bohunn.proto;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;


@ApplicationScoped
public class GitHandler {

    @ConfigProperty(name = "github.repository.url")
    private String githubRepositoryUrl;

    @ConfigProperty(name = "github.username")
    private String githubUsername;

    @ConfigProperty(name = "github.password")
    private String githubPassword;


    public void commitProtobufs() {
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