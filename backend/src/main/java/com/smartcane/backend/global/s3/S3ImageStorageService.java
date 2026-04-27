package com.smartcane.backend.global.s3;

import com.smartcane.backend.config.AwsProperties;
import com.smartcane.backend.config.S3Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class S3ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3ImageStorageService.class);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final String prefix;
    private final String region;

    public S3ImageStorageService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            S3Properties s3Properties,
            AwsProperties awsProperties
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = s3Properties.getBucket();
        this.prefix = normalizePrefix(s3Properties.getPrefix());
        this.region = awsProperties.getRegion();
    }

    public String generatePresignedUrl(String s3Url) {
        String urlPrefix = String.format("https://s3.%s.amazonaws.com/%s/", region, bucket);
        String key = s3Url.replace(urlPrefix, "");
        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build())
                .build())
                .url()
                .toString();
    }

    @Async
    public CompletableFuture<String> uploadAsync(String imageBase64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(stripDataUriPrefix(imageBase64));
            String key = prefix + "hazards/" + UUID.randomUUID() + ".jpg";

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("image/jpeg")
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(decoded));

            String url = String.format("https://s3.%s.amazonaws.com/%s/%s", region, bucket, key);
            log.info("S3 업로드 성공 key={}", key);
            return CompletableFuture.completedFuture(url);
        } catch (Exception e) {
            log.warn("S3 업로드 실패 — image_url=null 처리", e);
            return CompletableFuture.completedFuture(null);
        }
    }

    private String stripDataUriPrefix(String base64) {
        int comma = base64.indexOf(',');
        String raw = (base64.startsWith("data:") && comma > 0)
                ? base64.substring(comma + 1)
                : base64;
        return raw.replaceAll("\\s+", "");
    }

    private String normalizePrefix(String p) {
        if (p == null || p.isBlank()) return "";
        return p.endsWith("/") ? p : p + "/";
    }
}
