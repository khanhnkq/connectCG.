package org.example.connectcg_be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMemberDTO {
    private Integer id;
    private String fullName;
    private String avatarUrl;
    private String role;
}
