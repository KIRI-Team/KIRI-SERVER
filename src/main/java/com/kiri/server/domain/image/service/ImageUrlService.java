package com.kiri.server.domain.image.service;

import com.kiri.server.global.cloudfront.CloudFrontProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.services.cloudfront.model.CannedSignerRequest;

import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class ImageUrlService {

    private static final String PUBLIC_PREFIX="public/";

    private final CloudFrontUtilities cloudFrontUtilities;
    private final PrivateKey cloudFrontPrivateKey;
    private final CloudFrontProperties cloudFrontProperties;
    private final Clock clock;

    public String toUrl(String imageKey){
        if(imageKey==null){
            return null;
        }

        String url=cloudFrontProperties.cdnBaseUrl()+"/"+imageKey;
        if (imageKey.startsWith(PUBLIC_PREFIX)) {
            return url;
        }

        Instant expiresAt=clock.instant()
                .truncatedTo(ChronoUnit.HOURS)
                .plus(cloudFrontProperties.signedUrlExpiration());

        CannedSignerRequest request= CannedSignerRequest.builder()
                .resourceUrl(url)
                .privateKey(cloudFrontPrivateKey)
                .keyPairId(cloudFrontProperties.keyPairId())
                .expirationDate(expiresAt)
                .build();

        return cloudFrontUtilities.getSignedUrlWithCannedPolicy(request).url();
    }

}
