package com.justdoit.backend.storage;

import java.io.InputStream;
import java.net.URI;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

// Production: Backblaze B2 through its S3-compatible API. The bucket is private (verified 2026-10-22),
// so files are only reachable through the backend's ownership checks (SRS 17).
@Component
@ConditionalOnProperty(name = "storage.provider", havingValue = "s3")
public class S3FileStorage implements FileStorage, DisposableBean {

	private final S3Client s3;
	private final String bucket;

	public S3FileStorage(@Value("${storage.s3.endpoint}") String endpoint, @Value("${storage.s3.bucket}") String bucket,
			@Value("${storage.s3.access-key}") String accessKey, @Value("${storage.s3.secret-key}") String secretKey,
			@Value("${storage.s3.region}") String region) {
		if (accessKey.isBlank() || secretKey.isBlank()) {
			throw new IllegalStateException("storage.provider=s3 needs B2_KEY_ID and B2_APPLICATION_KEY");
		}
		this.bucket = bucket;
		this.s3 = S3Client.builder()
				.endpointOverride(URI.create(endpoint))
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
				// Newer SDKs add extra checksums by default that S3-compatible services may reject; only send what's required
				.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
				.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
				.build();
	}

	@Override
	public void put(String key, InputStream content, long size) {
		try {
			s3.putObject(b -> b.bucket(bucket).key(key).contentLength(size), RequestBody.fromInputStream(content, size));
		}
		catch (SdkException e) {
			throw new StorageException("Could not store " + key, e);
		}
	}

	@Override
	public StoredFile get(String key) {
		try {
			ResponseInputStream<GetObjectResponse> object = s3.getObject(b -> b.bucket(bucket).key(key));
			return new StoredFile(object, object.response().contentLength());
		}
		catch (SdkException e) {
			throw new StorageException("Could not read " + key, e);
		}
	}

	// On B2 this hides the file; the bucket's "keep only the last version" lifecycle rule removes it for good a day later
	@Override
	public void delete(String key) {
		try {
			s3.deleteObject(b -> b.bucket(bucket).key(key));
		}
		catch (SdkException e) {
			throw new StorageException("Could not delete " + key, e);
		}
	}

	@Override
	public void destroy() {
		s3.close();
	}

}
