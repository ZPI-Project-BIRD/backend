package zpi.project_bird.backend.aws;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration(proxyBeanMethods = false)
public class AwsClientConfiguration {
    @Bean
    LambdaClient lambdaClient() {
        return LambdaClient.builder()
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .build();
    }

    @Bean
    SqsClient sqsClient() {
        return SqsClient.builder()
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .build();
    }
}
