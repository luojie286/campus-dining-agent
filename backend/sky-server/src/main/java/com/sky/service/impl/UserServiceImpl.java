package com.sky.service.impl;

import org.springframework.context.annotation.Profile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.exception.BusinessException;
import com.sky.mapper.UserMapper;
import com.sky.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Profile("!bootstrap")
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User login(UserLoginDTO loginDTO) {

        // 1. 根据登录账号查询用户。
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(
                                User::getUsername,
                                loginDTO.getUsername().trim()
                        )
        );

        // 2. 用户不存在，登录失败。
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        // 3. 校验输入密码与数据库中的 BCrypt 哈希。
        boolean passwordCorrect = passwordEncoder.matches(
                loginDTO.getPassword(),
                user.getPassword()
        );

        if (!passwordCorrect) {
            throw new BusinessException("用户名或密码错误");
        }

        // 4. 密码正确后，再检查账号是否启用。
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException("账号已停用");
        }

        return user;
    }
}
