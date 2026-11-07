package com.sky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sky.entity.User;
import com.sky.entity.UserWechatIdentity;
import com.sky.exception.ConflictException;
import com.sky.exception.ForbiddenException;
import com.sky.exception.NotFoundException;
import com.sky.mapper.UserMapper;
import com.sky.mapper.UserWechatIdentityMapper;
import com.sky.service.WechatSessionClient.WechatIdentity;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("!bootstrap")
public class WechatLoginService {

    private final WechatSessionClient sessionClient;
    private final UserMapper userMapper;
    private final UserWechatIdentityMapper identityMapper;
    private final TransactionTemplate transactions;

    public WechatLoginService(
            WechatSessionClient sessionClient,
            UserMapper userMapper,
            UserWechatIdentityMapper identityMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.sessionClient = sessionClient;
        this.userMapper = userMapper;
        this.identityMapper = identityMapper;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    public User login(String code) {
        WechatIdentity identity = sessionClient.exchange(code);

        User user;
        try {
            user = transactions.execute(status -> findOrCreate(identity));
        } catch (DuplicateKeyException exception) {
            // 前一个事务已回滚。用新事务读取并发请求建立的身份关系。
            user = transactions.execute(status -> findBoundUser(identity));
            if (user == null) {
                throw new ConflictException("微信账号建档冲突，请重新登录");
            }
        }

        if (user == null) {
            throw new IllegalStateException("微信登录没有返回本站用户");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new ForbiddenException("账号已停用");
        }

        return user;
    }

    private User findOrCreate(WechatIdentity identity) {
        User existing = findBoundUser(identity);
        if (existing != null) {
            return existing;
        }

        User user = new User();
        user.setName("微信用户");
        user.setPhone("");
        user.setStatus(1);

        if (userMapper.insert(user) != 1 || user.getId() == null) {
            throw new IllegalStateException("创建点餐用户失败");
        }

        UserWechatIdentity binding = new UserWechatIdentity();
        binding.setUserId(user.getId());
        binding.setAppId(identity.appId());
        binding.setOpenId(identity.openId());

        if (identityMapper.insert(binding) != 1) {
            throw new IllegalStateException("保存微信身份关联失败");
        }

        return user;
    }

    private User findBoundUser(WechatIdentity identity) {
        UserWechatIdentity binding = identityMapper.selectOne(
                new LambdaQueryWrapper<UserWechatIdentity>()
                        .eq(UserWechatIdentity::getAppId, identity.appId())
                        .eq(UserWechatIdentity::getOpenId, identity.openId())
        );

        if (binding == null) {
            return null;
        }

        User user = userMapper.selectById(binding.getUserId());
        if (user == null) {
            throw new NotFoundException("微信身份关联的点餐账号不存在");
        }
        return user;
    }
}