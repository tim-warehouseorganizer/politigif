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
 * S3-compatible storage through the AWS SDK v2: DigitalOcean Spaces (endpoint set) or AWS S3 itself,
 * e.g. a Heroku Bucketeer bucket (endpoint blank → the AWS endpoint for the region). Objects are
 * private: every byte reaches a visitor through the app, never a public bucket URL. Every key is
 * stored under {@code keyPrefix} (default {@code politigif/}) so a shared bucket stays partitioned.
 */
public class SpacesStorage implements MediaStorage {

    private final S3Client s3;
    private final String bucket;
    private final String keyPrefix;

    public SpacesStorage(String endpoint, String region, String bucket, String key, String secret, String keyPrefix) {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(key, secret)));
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        this.s3 = builder.build();
        this.bucket = bucket;
        this.keyPrefix = keyPrefix;
    }

    /** The object key in the bucket: the app's key under the prefix ({@code politigif/gifs/<id>.mp4}). */
    static String objectKey(String keyPrefix, String key) {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            return key;
        }
        return keyPrefix.endsWith("/") ? keyPrefix + key : keyPrefix + "/" + key;
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey(keyPrefix, key))
                        .contentType(contentType)
                        .acl(ObjectCannedACL.PRIVATE)
                        .build(),
                RequestBody.fromBytes(data));
    }

    @Override
    public InputStream open(String key) {
        return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(objectKey(keyPrefix, key)).build());
    }

    @Override
    public InputStream openRange(String key, long first, long last) {
        return s3.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey(keyPrefix, key))
                .range("bytes=" + first + "-" + last)
                .build());
    }

    @Override
    public long size(String key) {
        return s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey(keyPrefix, key)).build()).contentLength();
    }
}
