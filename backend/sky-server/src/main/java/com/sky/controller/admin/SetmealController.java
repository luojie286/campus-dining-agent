package com.sky.controller.admin;
import com.sky.dto.SetmealCreateDTO;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/admin/setmeal")
public class SetmealController {
    private final SetmealService service;
    public SetmealController(SetmealService service) { this.service = service; }

    @GetMapping("/page")
    public Result<com.sky.result.PageResult<SetmealVO>> page(
            @Valid @ModelAttribute com.sky.dto.SetmealPageQueryDTO dto) {
        return Result.success(service.pageQuery(dto));
    }

    @GetMapping("/{id}")
    public Result<SetmealVO> detail(@PathVariable("id") Long id) {
        return Result.success(service.getById(id));
    }

    @PutMapping
    public Result<SetmealVO> update(@Valid @RequestBody com.sky.dto.SetmealUpdateDTO dto) {
        return Result.success(service.update(dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        service.delete(id);
        return Result.success(null);
    }

    @PostMapping
    public Result<SetmealVO> create(@Valid @RequestBody SetmealCreateDTO dto) {
        return Result.success(service.create(dto));
    }
}
