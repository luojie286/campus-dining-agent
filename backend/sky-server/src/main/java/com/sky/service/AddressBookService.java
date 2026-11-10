package com.sky.service;

import com.sky.dto.AddressSaveDTO;
import com.sky.exception.BadRequestException;
import com.sky.exception.ConflictException;
import com.sky.exception.NotFoundException;
import com.sky.mapper.AddressBookRepository;
import com.sky.vo.AddressVO;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Profile("!bootstrap")
public class AddressBookService {
    private final UserTransactionLock users;
    private final AddressBookRepository addresses;

    public AddressBookService(UserTransactionLock users, AddressBookRepository addresses) {
        this.users = users;
        this.addresses = addresses;
    }

    public List<AddressVO> list() { return addresses.list(users.currentUserId()); }

    public AddressVO get(long id) { return requireOwned(users.currentUserId(), id); }

    @Transactional
    public AddressVO create(AddressSaveDTO dto) {
        if (dto.getId() != 0) throw new BadRequestException("新增地址时id必须为0");
        long userId = users.lockCurrentUser(false);
        if (addresses.count(userId) >= 20) throw new ConflictException("最多保存20条地址");
        if (dto.getIsDefault()) addresses.clearDefault(userId);
        return requireOwned(userId, addresses.insert(userId, dto));
    }

    @Transactional
    public AddressVO update(AddressSaveDTO dto) {
        long userId = users.lockCurrentUser(false);
        requireOwned(userId, dto.getId());
        if (dto.getIsDefault()) addresses.clearDefault(userId);
        addresses.update(userId, dto);
        return requireOwned(userId, dto.getId());
    }

    @Transactional
    public AddressVO setDefault(long id) {
        long userId = users.lockCurrentUser(false);
        AddressVO target = requireOwned(userId, id);
        if (target.isDefault()) return target;
        addresses.clearDefault(userId);
        addresses.setDefault(userId, id);
        return requireOwned(userId, id);
    }

    @Transactional
    public void delete(long id) {
        validateId(id);
        long userId = users.lockCurrentUser(false);
        if (addresses.delete(userId, id) == 0) throw new NotFoundException("地址不存在");
    }

    private AddressVO requireOwned(long userId, long id) {
        validateId(id);
        return addresses.find(userId, id).orElseThrow(() -> new NotFoundException("地址不存在"));
    }

    private void validateId(long id) {
        if (id < 1 || id > 9007199254740991L) throw new BadRequestException("地址ID不合法");
    }
}
