package com.kiri.server.global.cloudfront;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "aws.cloudfront")
public record CloudFrontProperties (
        String cdnBaseUrl,
        String keyPairId,
        String privateKey,
        Duration signedUrlExpiration
){
}
