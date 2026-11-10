/**
 * 智能 Agent：DeepSeek 模型适配、Tool Registry 白名单、多轮工具编排、Redis 会话与写操作确认。
 * 只复用已验收的业务 Service；模型不直接访问数据库，也不能绕过身份与权限。
 */
package com.sky.agent;
