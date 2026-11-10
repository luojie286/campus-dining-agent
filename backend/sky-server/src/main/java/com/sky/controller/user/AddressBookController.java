package com.sky.controller.user;

import com.sky.dto.AddressSaveDTO;
import com.sky.exception.BadRequestException;
import com.sky.result.Result;
import com.sky.service.AddressBookService;
import com.sky.vo.AddressVO;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/user/addressBook")
public class AddressBookController {
    private final AddressBookService addresses;

    public AddressBookController(AddressBookService addresses) { this.addresses = addresses; }

    @GetMapping("/list")
    public Result<List<AddressVO>> list() { return Result.success(addresses.list()); }

    @GetMapping("/{id}")
    public Result<AddressVO> get(@PathVariable("id") long id) { return Result.success(addresses.get(id)); }

    @PostMapping
    public Result<AddressVO> create(@Valid @RequestBody AddressSaveDTO dto) {
        return Result.success(addresses.create(dto));
    }

    @PutMapping
    public Result<AddressVO> update(@Valid @RequestBody AddressSaveDTO dto) {
        return Result.success(addresses.update(dto));
    }

    @PutMapping("/{id}/default")
    public Result<AddressVO> setDefault(@PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        if (!body.isEmpty()) throw new BadRequestException("请求体必须为空对象");
        return Result.success(addresses.setDefault(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") long id) {
        addresses.delete(id);
        return Result.success(null);
    }
}
