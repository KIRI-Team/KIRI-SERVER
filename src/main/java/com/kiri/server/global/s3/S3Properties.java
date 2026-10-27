package com.kiri.server.global.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "aws.s3")
public record S3Properties(
        String region,
        String bucket,
        Duration presignedUrlExpiration
) {
}
