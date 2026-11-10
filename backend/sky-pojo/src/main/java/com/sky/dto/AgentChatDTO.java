package com.sky.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 空 sessionId 表示新建会话；服务端分配并绑定身份，不接受客户端自填角色或用户ID。 */
@Getter
@Setter
public class AgentChatDTO {

    @NotBlank
    @Size(max = 1000)
    private String message;

    @Size(max = 64)
    private String sessionId = "";

    public void setMessage(String message) {
        this.message = message == null ? null : message.trim();
    }
}
