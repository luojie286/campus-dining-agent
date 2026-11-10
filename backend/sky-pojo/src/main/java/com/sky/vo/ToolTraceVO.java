package com.sky.vo;

/** 只展示安全摘要：工具名、摘要结果、成功或失败，不含隐藏推理或密钥。 */
public record ToolTraceVO(String name, String summary, String state) {
}
