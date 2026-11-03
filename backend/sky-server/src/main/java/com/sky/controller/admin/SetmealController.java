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

    @PostMapping
    public Result<SetmealVO> create(@Valid @RequestBody SetmealCreateDTO dto) {
        return Result.success(service.create(dto));
    }
}
