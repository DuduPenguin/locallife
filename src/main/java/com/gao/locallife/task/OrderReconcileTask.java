package com.gao.locallife.task;

import com.gao.locallife.service.VoucherOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 定时对账：Redis 预扣 vs DB 权威数据。
 *
 * <p>为什么必须有对账：异步系统一定会短暂不一致（消息丢、消费失败、回补漏掉）。
 * 对账不负责"自动修复"，负责**发现问题**——发现了才谈得上修。
 *
 * <p>生产上这里应该是"发现不一致 → 告警 + 落到失败表 → 人工/半自动补偿"，
 * 本项目只做检测和日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderReconcileTask {

    private final VoucherOrderService voucherOrderService;

    /** 每 5 分钟跑一次（从上一个任务结束后开始计时，避免堆积） */
    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 60 * 1000)
    public void reconcile() {
        Map<String, Object> result = voucherOrderService.reconcile();
        boolean allOk = Boolean.TRUE.equals(result.get("allOk"));
        if (allOk) {
            log.debug("对账通过: {}", result.get("detail"));
        } else {
            // 生产上这里接告警
            log.error("【对账不一致，需要人工介入】{}", result.get("detail"));
        }
    }
}
