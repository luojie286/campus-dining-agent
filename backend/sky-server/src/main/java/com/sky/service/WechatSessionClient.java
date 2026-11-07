package com.sky.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sky.exception.BadRequestException;
import com.sky.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;

@Component
@Profile("!bootstrap")
public class WechatSessionClient {

    private final String appId;
    private final String appSecret;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WechatSessionClient(
            @Value("${sky.wechat.app-id:}") String appId,
            @Value("${sky.wechat.app-secret:}") String appSecret,
            ObjectMapper objectMapper
    ) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .baseUrl("https://api.weixin.qq.com")
                .requestFactory(factory)
                .build();
    }

    public WechatIdentity exchange(String code) {
        if (appId.isBlank() || appSecret.isBlank()) {
            throw new ServiceUnavailableException("微信登录尚未配置");
        }

        SessionResponse response;

        try {
            // 第一步：先接收文本，微信标记为 text/plain 也能读取。
            String body = restClient.get()
                    .uri("/sns/jscode2session?appid={appid}&secret={secret}"
                                    + "&js_code={code}&grant_type=authorization_code",
                            appId, appSecret, code)
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                throw new ServiceUnavailableException("微信登录返回了空结果");
            }

            // 第二步：明确按照 JSON 格式解析，生成 Java 对象。
            response = objectMapper.readValue(body, SessionResponse.class);

        } catch (RestClientException exception) {
            throw new ServiceUnavailableException("微信登录服务暂不可用");

        } catch (JsonProcessingException exception) {
            throw new ServiceUnavailableException("微信登录响应格式异常");
        }

        if (response == null) {
            throw new ServiceUnavailableException("微信登录返回了空结果");
        }

        Integer errorCode = response.errcode();
        if (Integer.valueOf(40029).equals(errorCode)
                || Integer.valueOf(40163).equals(errorCode)) {
            throw new BadRequestException("微信登录凭证无效，请重新登录");
        }
        if (errorCode != null && errorCode != 0) {
            throw new ServiceUnavailableException("微信登录暂时失败，请稍后重试");
        }
        if (response.openid() == null || response.openid().isBlank()) {
            throw new ServiceUnavailableException("微信登录未返回用户身份");
        }

        return new WechatIdentity(appId, response.openid());
    }

    public record WechatIdentity(String appId, String openId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionResponse(String openid, Integer errcode) {}
}