package com.toomanyissues.api.Controller.Requests;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RatingRequest(
        @NotBlank(message = "userid required")
        String userId,
        @NotBlank(message = "product required")
        String productId,
        @NotBlank()
        @Size(min = 1, max = 5)
        Integer rating
) {
}
