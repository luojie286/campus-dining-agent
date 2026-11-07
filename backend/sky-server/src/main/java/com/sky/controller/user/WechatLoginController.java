package com.sky.controller.user;

import com.sky.dto.WechatLoginDTO;
import com.sky.entity.User;
import com.sky.result.Result;
import com.sky.service.WechatLoginService;
import com.sky.util.JwtUtil;
import com.sky.vo.UserLoginVO;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/user/wechat")
public class WechatLoginController {

    private final WechatLoginService loginService;
    private final JwtUtil jwtUtil;

    public WechatLoginController(WechatLoginService loginService, JwtUtil jwtUtil) {
        this.loginService = loginService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public Result<UserLoginVO> login(@Valid @RequestBody WechatLoginDTO dto) {
        User user = loginService.login(dto.getCode());
        String token = jwtUtil.createToken(user.getId(), "user");

        return Result.success(
                new UserLoginVO(user.getId(), user.getName(), "user", token)
        );
    }
}