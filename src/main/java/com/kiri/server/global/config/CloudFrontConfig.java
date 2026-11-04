package com.kiri.server.global.config;

import com.kiri.server.global.cloudfront.CloudFrontProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties(CloudFrontProperties.class)
public class CloudFrontConfig {

    @Bean
    public CloudFrontUtilities cloudFrontUtilities(){
        return CloudFrontUtilities.create();
    }

    @Bean
    public PrivateKey cloudFrontPrivateKey(CloudFrontProperties properties) throws GeneralSecurityException {
        byte[] der= Base64.getDecoder().decode(properties.privateKey());
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }
}
