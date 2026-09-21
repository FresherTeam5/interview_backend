package com.baseProject.myBaseProject.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.baseProject.myBaseProject.config.properites.StorageProperties;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class StorageConfig {

    private final StorageProperties properties;

    // communicate with minio
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())
                ))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .build();
    }

    // create temporaly link to download or view
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())
                ))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    // run when project start
    @Bean
    public ApplicationRunner initMinioBucket(S3Client s3Client) {
        return args -> {
            String bucket = properties.bucketName();
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
                log.info("MinIO bucket '{}' already exists and is ready.", bucket);
            } catch (NoSuchBucketException e) {
                log.info("MinIO bucket '{}' does not exist. Creating bucket...", bucket);
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("MinIO bucket '{}' created successfully.", bucket);
            } catch (S3Exception e) {
                if (e.statusCode() == 404) {
                    log.info("MinIO bucket '{}' does not exist (404). Creating bucket...", bucket);
                    s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                    log.info("MinIO bucket '{}' created successfully.", bucket);
                } else {
                    log.warn("Failed to check MinIO bucket '{}': {}", bucket, e.getMessage());
                }
            } catch (Exception e) {
                log.warn("Could not connect to MinIO on startup (is MinIO running?): {}", e.getMessage());
            }
        };
    }
}
