package com.nhattienn.ecommerce.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PresignedUploadRequest(
        @NotBlank(message = "Filename must not be blank.")
        String filename,

        @NotBlank(message = "Content type must not be blank.")
        @Pattern(regexp = "image/(jpeg|png|webp)", message = "Only jpeg, png, webp images are allowed.")
        String contentType
) {}