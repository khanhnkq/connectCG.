package org.example.connectcg_be.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCommentRequest {
    @Size(max = 2000, message = "Bình luận tối đa 2000 kí tự")
    private String content;
    private Integer parentId;
    @Size(max = 2048, message = "URL ảnh quá dài")
    private String imageUrl;

    @AssertTrue(message = "Bình luận phải có nội dung hoặc hình ảnh")
    public boolean isContentOrImagePresent() {
        return (content != null && !content.isBlank())
                || (imageUrl != null && !imageUrl.isBlank());
    }
}
