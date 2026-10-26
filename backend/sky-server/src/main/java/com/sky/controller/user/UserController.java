package com.sky.controller.user;

import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.result.Result;
import com.sky.service.UserService;
import com.sky.util.JwtUtil;
import com.sky.vo.UserLoginVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public UserController(
            UserService userService,
            JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public Result<UserLoginVO> login(
            @Valid @RequestBody UserLoginDTO loginDTO) {

        User user = userService.login(loginDTO);

        String token = jwtUtil.createToken(
                user.getId(),
                "user"
        );

        UserLoginVO loginVO = new UserLoginVO(
                user.getId(),
                user.getName(),
                "user",
                token
        );

        return Result.success(loginVO);
    }
}