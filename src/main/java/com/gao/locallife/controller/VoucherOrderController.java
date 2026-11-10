package com.gao.locallife.controller;

import com.gao.locallife.common.Result;
import com.gao.locallife.service.VoucherOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 秒杀接口。 */
@RestController
@RequestMapping("/voucher-order")
@RequiredArgsConstructor
public class VoucherOrderController {

    private final VoucherOrderService voucherOrderService;

    /** ⚠️ M4-A 同步版：并发下会超卖，仅用于压测对比。 */
    @PostMapping("/seckill-sync/{id}")
    public Result<Long> seckillSync(@PathVariable("id") Long voucherId) {
        return Result.ok(voucherOrderService.seckillSync(voucherId));
    }

    /** M4-B：Lua 预扣 + MQ 异步。 */
    @PostMapping("/seckill/{id}")
    public Result<Long> seckill(@PathVariable("id") Long voucherId) {
        return Result.ok(voucherOrderService.seckill(voucherId));
    }
}
