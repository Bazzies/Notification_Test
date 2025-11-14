package org.example.notifications.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EventRequest {
    @NotBlank(message = "URL은 필수입니다")
    private String url;
    
    @NotNull(message = "상태 코드는 필수입니다")
    @Min(value = 100, message = "유효한 HTTP 상태 코드여야 합니다")
    private Integer status;
    
    @NotNull(message = "응답 시간은 필수입니다")
    @Min(value = 0, message = "응답 시간은 0 이상이어야 합니다")
    private Integer latency;
    
    @NotBlank(message = "타임스탬프는 필수입니다")
    private String timestamp;
}

