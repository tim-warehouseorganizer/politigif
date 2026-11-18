/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.storage;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.net.URI;

/**
 * DigitalOcean Spaces through the AWS SDK v2 (any S3-compatible store works). Objects are private:
 * every byte reaches a visitor through the app, never a public bucket URL.
 */
public class SpacesStorage implements MediaStorage {

    private final S3Client s3;
    private final String bucket;

    public SpacesStorage(String endpoint, String region, String bucket, String key, String secret) {
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(key, secret)))
                .build();
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .acl(ObjectCannedACL.PRIVATE)
                        .build(),
                RequestBody.fromBytes(data));
    }

    @Override
    public InputStream open(String key) {
        return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public InputStream openRange(String key, long first, long last) {
        return s3.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .range("bytes=" + first + "-" + last)
                .build());
    }

    @Override
    public long size(String key) {
        return s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).contentLength();
    }
}
