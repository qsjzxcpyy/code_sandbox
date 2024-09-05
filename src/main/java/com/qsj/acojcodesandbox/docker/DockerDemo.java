package com.qsj.acojcodesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DockerClientBuilder;

public class DockerDemo {
    public static void main(String[] args) throws InterruptedException {
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        String image = "nginx:latest";
        PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {

            @Override
            public void onNext(PullResponseItem item) {
                System.out.println("下载镜像: " + item.getStatus());
                super.onNext(item);
            }
        };
        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
        pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
        System.out.println("镜像下载完成");
    }
}
