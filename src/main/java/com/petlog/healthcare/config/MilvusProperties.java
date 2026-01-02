package com.petlog.healthcare.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aws.vectorstore.milvus")
public class MilvusProperties {
    private String collectionName = "diary_vectors";
    private int embeddingDimension = 1024;
    private Client client = new Client();

    @Data
    public static class Client {
        private String host;
        private int port;
    }
}