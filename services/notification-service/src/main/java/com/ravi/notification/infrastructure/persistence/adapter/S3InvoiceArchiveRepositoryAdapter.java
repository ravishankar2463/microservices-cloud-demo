package com.ravi.notification.infrastructure.persistence.adapter;

import com.ravi.notification.domain.model.Invoice;
import com.ravi.notification.domain.repository.InvoiceArchiveRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class S3InvoiceArchiveRepositoryAdapter implements InvoiceArchiveRepository {

    private static final Logger log = LoggerFactory.getLogger(S3InvoiceArchiveRepositoryAdapter.class);

    private final Optional<S3Client> s3Client;
    private final Map<String, Invoice> localInvoiceCache = new ConcurrentHashMap<>();

    @Value("${notification.s3.bucket-name:order-invoices-bucket}")
    private String bucketName;

    public S3InvoiceArchiveRepositoryAdapter(Optional<S3Client> s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public void save(Invoice invoice) {
        String key = "invoices/" + invoice.orderId() + ".json";
        localInvoiceCache.put(invoice.orderId(), invoice);

        if (s3Client.isPresent()) {
            try {
                S3Client client = s3Client.get();
                ensureBucketExists(client);

                PutObjectRequest request = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType("application/json")
                        .build();

                client.putObject(request, RequestBody.fromString(invoice.payload(), StandardCharsets.UTF_8));
                log.info("Successfully archived invoice for order {} into AWS S3 bucket: s3://{}/{}",
                        invoice.orderId(), bucketName, key);
            } catch (Exception e) {
                log.warn("S3 upload failed (Local AWS emulator offline or bucket uninitialized): {}. Cached in memory.", e.getMessage());
            }
        } else {
            log.info("S3Client not configured; invoice stored in in-memory archive for order: {}", invoice.orderId());
        }
    }

    private void ensureBucketExists(S3Client client) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (Exception missing) {
            log.info("Creating missing S3 bucket in LocalStack: {}", bucketName);
            client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        }
    }

    @Override
    public Optional<Invoice> findByOrderId(String orderId) {
        return Optional.ofNullable(localInvoiceCache.get(orderId));
    }

    @Override
    public int clearAll() {
        int clearedCount = localInvoiceCache.size();
        localInvoiceCache.clear();

        if (s3Client.isPresent()) {
            try {
                S3Client client = s3Client.get();
                ListObjectsV2Response listResp = client.listObjectsV2(
                        ListObjectsV2Request.builder().bucket(bucketName).build());
                int deletedS3 = 0;
                for (S3Object obj : listResp.contents()) {
                    client.deleteObject(DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(obj.key())
                            .build());
                    deletedS3++;
                }
                log.info("Cleared {} S3 invoice object(s) from bucket {}", deletedS3, bucketName);
                return Math.max(clearedCount, deletedS3);
            } catch (Exception e) {
                log.warn("Could not clear S3 bucket {} during reset: {}", bucketName, e.getMessage());
            }
        }
        return clearedCount;
    }
}
