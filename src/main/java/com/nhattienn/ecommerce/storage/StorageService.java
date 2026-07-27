package com.nhattienn.ecommerce.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;


import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
public class StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    public StorageService(S3Client s3Client, S3Presigner s3Presigner, S3Properties s3Properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.s3Properties = s3Properties;
    }

    /**
     * Upload file lên S3 và trả về object key.
     * Object key là định danh duy nhất của file trong bucket —
     * không phải URL, vì URL có thể thay đổi theo presign expiration.
     */
    public String upload(MultipartFile file, String folder) {
        String key = buildKey(folder, file.getOriginalFilename());

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(s3Properties.bucketName())
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(
                    file.getInputStream(), file.getSize()));

            log.debug("Uploaded file to S3: {}", key);
            return key;

        } catch (IOException e) {
            throw new StorageException("Failed to upload file to S3: " + key, e);
        }
    }

    /**
     * Generate presigned URL để client download trực tiếp từ S3.
     * URL hết hạn sau `presignedUrlExpiration` phút — không expose bucket publicly.
     */
    public String generatePresignedUrl(String key) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(s3Properties.presignedUrlExpiration()))
                .getObjectRequest(req -> req
                        .bucket(s3Properties.bucketName())
                        .key(key))
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }

    /**
     * Xóa file khỏi S3 theo object key.
     */
    public void delete(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(s3Properties.bucketName())
                .key(key)
                .build();

        s3Client.deleteObject(request);
        log.debug("Deleted file from S3: {}", key);
    }

    private String buildKey(String folder, String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return folder + "/" + UUID.randomUUID() + extension;
    }

    public record PresignedUploadResponse(String uploadUrl, String objectKey) {}

    public PresignedUploadResponse generatePresignedUploadUrl(String folder, String filename, String contentType) {
        String key = buildKey(folder, filename);

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(s3Properties.presignedUrlExpiration()))
            .putObjectRequest(req -> req
                    .bucket(s3Properties.bucketName())
                    .key(key)
                    .contentType(contentType))
            .build();

            PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        return new PresignedUploadResponse(presigned.url().toString(), key);
}
}