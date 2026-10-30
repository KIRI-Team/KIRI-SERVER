package com.kiri.server.global.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@ConfigurationProperties(prefix = "aws.s3")
public record S3Properties(
        String region,
        String bucket,
        Duration presignedUrlExpiration,
        DataSize maxUploadSize
) {
}
