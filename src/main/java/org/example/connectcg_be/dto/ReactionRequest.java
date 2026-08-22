package org.example.connectcg_be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReactionRequest {
    @NotBlank(message = "Loại cảm xúc không được để trống")
    @Pattern(
            regexp = "LIKE|LOVE|HAHA|WOW|SAD|ANGRY",
            message = "Loại cảm xúc không hợp lệ")
    private String reaction;
}
