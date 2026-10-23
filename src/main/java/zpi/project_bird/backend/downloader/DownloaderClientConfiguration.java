package zpi.project_bird.backend.downloader;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

import zpi.project_bird.backend.downloader.v1.DownloaderServiceGrpc;

@Configuration(proxyBeanMethods = false)
public class DownloaderClientConfiguration {
    @Bean
    DownloaderServiceGrpc.DownloaderServiceBlockingStub downloaderServiceBlockingStub(
            GrpcChannelFactory channels) {
        return DownloaderServiceGrpc.newBlockingStub(
            channels.createChannel("downloader"));
    }
}
