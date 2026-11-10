package com.gao.locallife.service.impl;

import com.gao.locallife.common.BusinessException;
import com.gao.locallife.common.UserHolder;
import com.gao.locallife.entity.SeckillVoucher;
import com.gao.locallife.entity.VoucherOrder;
import com.gao.locallife.id.RedisIdWorker;
import com.gao.locallife.mapper.SeckillVoucherMapper;
import com.gao.locallife.mapper.VoucherOrderMapper;
import com.gao.locallife.service.VoucherOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 秒杀下单实现。
 *
 * <p>本类目前只有 <b>M4-A 同步版</b>（{@link #seckillSync}），它是"错的"版本，
 * 用来压出"超卖"数据；M4-B 会用 Lua + MQ 替换整条链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherOrderServiceImpl implements VoucherOrderService {

    private final SeckillVoucherMapper seckillVoucherMapper;
    private final VoucherOrderMapper voucherOrderMapper;
    private final RedisIdWorker idWorker;

    /* ==================== M4-A 同步版（故意有并发问题） ==================== */

    @Override
    @Transactional
    public Long seckillSync(Long voucherId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }

        // 1. 查券
        SeckillVoucher voucher = seckillVoucherMapper.selectById(voucherId);
        if (voucher == null) {
            throw new BusinessException(400, "优惠券不存在");
        }

        // 2. 校验活动时间
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.getBeginTime())) {
            throw new BusinessException(400, "活动尚未开始");
        }
        if (now.isAfter(voucher.getEndTime())) {
            throw new BusinessException(400, "活动已结束");
        }

        // 3. 一人一单：先查后插 —— 并发下这里有窗口（故意的）
        if (voucherOrderMapper.countByUserAndVoucher(userId, voucherId) > 0) {
            throw new BusinessException(400, "每人限购一单");
        }

        // 4. 判断库存：读到的可能是过期值 —— 200 并发时全部通过，这就是超卖的根因
        if (voucher.getStock() <= 0) {
            throw new BusinessException(400, "库存不足");
        }

        // 5. 扣库存：故意用没有 stock > 0 守卫的写法，并发下会扣成负数
        seckillVoucherMapper.deductStockUnsafe(voucherId);

        // 6. 落库
        long orderId = idWorker.nextId("order");
        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setPayValue(1000L);
        order.setActualValue(5000L);
        order.setStatus(1);
        voucherOrderMapper.insert(order);

        log.info("同步版下单成功 userId={} voucherId={} orderId={}", userId, voucherId, orderId);
        return orderId;
    }

    /* ==================== M4-B 待实现 ==================== */

    @Override
    public Long seckill(Long voucherId) {
        throw new UnsupportedOperationException("M4-B 实现：Lua 预扣 + MQ 异步");
    }

    @Override
    public void createVoucherOrder(Long orderId, Long userId, Long voucherId) {
        throw new UnsupportedOperationException("M4-B 实现：消费者落库");
    }
}
