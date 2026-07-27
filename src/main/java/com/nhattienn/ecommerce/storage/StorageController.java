package com.nhattienn.ecommerce.storage;

import com.nhattienn.ecommerce.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/storage")
public class StorageController {

    private final StorageService storageService;

    public StorageController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping("/presigned-upload-url")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StorageService.PresignedUploadResponse>> getPresignedUploadUrl(
            @Valid @RequestBody PresignedUploadRequest request) {
        StorageService.PresignedUploadResponse response =
                storageService.generatePresignedUploadUrl("products", request.filename(), request.contentType());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}