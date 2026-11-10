package com.gao.locallife.controller;

import com.gao.locallife.common.Result;
import com.gao.locallife.service.VoucherOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 秒杀与订单接口。 */
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

    /** 模拟支付：状态机 1 -> 2。 */
    @PostMapping("/pay/{orderId}")
    public Result<Boolean> pay(@PathVariable("orderId") Long orderId) {
        return Result.ok(voucherOrderService.payOrder(orderId));
    }

    /** 取消订单并回补库存（超时取消也走这里）。 */
    @PostMapping("/cancel/{orderId}")
    public Result<Void> cancel(@PathVariable("orderId") Long orderId) {
        voucherOrderService.cancelOrder(orderId);
        return Result.ok();
    }

    /** 手动触发对账（定时任务也会跑）。 */
    @GetMapping("/reconcile")
    public Result<Map<String, Object>> reconcile() {
        return Result.ok(voucherOrderService.reconcile());
    }
}
