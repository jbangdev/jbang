//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.github.docker-java:docker-java:3.2.1
//DEPS javax.activation:activation:1.1.1
//DEPS org.slf4j:slf4j-simple:1.7.25

import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.BuildImageResultCallback;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class docker
{
    public static void main(String[] args)
    {
        final var dockerClient = DockerClientBuilder.getInstance().build();

        BuildImageResultCallback callback = new BuildImageResultCallback()
        {
            @Override
            public void onNext(BuildResponseItem item)
            {
                System.out.print(item.getStream());
                super.onNext(item);
            }
        };

        dockerClient
                .listImagesCmd()
                .exec().stream()
                    .filter(s->s!=null)
                    .forEach(it -> System.out.println(it.getId() + " " +
                                   String.join(",", Objects.requireNonNullElse(it.getRepoTags(), new String[0]))));
    }
}