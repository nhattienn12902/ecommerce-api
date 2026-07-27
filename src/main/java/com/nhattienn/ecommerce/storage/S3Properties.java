package com.nhattienn.ecommerce.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.aws.s3")
public record S3Properties(
        String bucketName,
        String region,
        String accessKey,
        String secretKey,
        int presignedUrlExpiration
) {}