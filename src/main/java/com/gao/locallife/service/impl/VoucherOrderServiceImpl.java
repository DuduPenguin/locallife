package com.gao.locallife.service.impl;

import com.gao.locallife.common.BusinessException;
import com.gao.locallife.common.RedisConstants;
import com.gao.locallife.common.SeckillCode;
import com.gao.locallife.common.UserHolder;
import com.gao.locallife.entity.SeckillVoucher;
import com.gao.locallife.entity.VoucherOrder;
import com.gao.locallife.id.RedisIdWorker;
import com.gao.locallife.mapper.SeckillVoucherMapper;
import com.gao.locallife.mapper.VoucherOrderMapper;
import com.gao.locallife.mq.OrderCancelProducer;
import com.gao.locallife.mq.SeckillMessage;
import com.gao.locallife.mq.SeckillOrderProducer;
import com.gao.locallife.service.VoucherOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 秒杀下单与订单生命周期。
 *
 * <p>M4-A 同步版（{@link #seckillSync}）保留用于压测对比；
 * <p>M4-B 是 {@link #seckill}：Lua 预扣 + MQ 异步落库；
 * <p>M5 补上支付、超时取消、对账。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherOrderServiceImpl implements VoucherOrderService {

    private final SeckillVoucherMapper seckillVoucherMapper;
    private final VoucherOrderMapper voucherOrderMapper;
    private final RedisIdWorker idWorker;
    private final StringRedisTemplate stringRedisTemplate;
    private final SeckillOrderProducer producer;
    private final OrderCancelProducer orderCancelProducer;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);

        ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_SCRIPT.setLocation(new ClassPathResource("lua/rollback_stock.lua"));
        ROLLBACK_SCRIPT.setResultType(Long.class);
    }

    /* ==================== M4-B：Lua 预扣 + MQ 异步 ==================== */

    @Override
    public Long seckill(Long voucherId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }

        // 1. 活动时间校验（读 Redis，避免打 DB）
        String timeRange = stringRedisTemplate.opsForValue()
                .get(RedisConstants.SECKILL_TIME_KEY + voucherId);
        if (timeRange == null) {
            throw new BusinessException(400, "活动不存在或未开始");
        }
        String[] parts = timeRange.split(":");
        long begin = Long.parseLong(parts[0]);
        long end = Long.parseLong(parts[1]);
        long now = System.currentTimeMillis() / 1000;
        if (now < begin) {
            throw new BusinessException(400, "活动尚未开始");
        }
        if (now > end) {
            throw new BusinessException(400, "活动已结束");
        }

        // 2. 执行 Lua：判库存 + 判重 + 扣减 + 记录，一次往返原子完成
        Long code = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                // hash tag {} 保证两个 key 同槽（集群模式必需）
                List.of(RedisConstants.SECKILL_STOCK_KEY + "{" + voucherId + "}",
                        RedisConstants.SECKILL_ORDER_KEY + "{" + voucherId + "}"),
                String.valueOf(userId));

        long result = code == null ? -1 : code;
        if (result == SeckillCode.SOLD_OUT) {
            throw new BusinessException(400, "库存不足");
        }
        if (result == SeckillCode.DUPLICATE) {
            throw new BusinessException(400, "每人限购一单");
        }
        if (result == SeckillCode.NOT_PREHEATED) {
            throw new BusinessException(500, "活动库存未就绪");
        }
        if (result != SeckillCode.SUCCESS) {
            throw new BusinessException(500, "下单失败，请重试");
        }

        // 3. 生成订单号 + 发 MQ（同步链路到此结束）
        long orderId = idWorker.nextId("order");
        try {
            producer.send(new SeckillMessage(orderId, userId, voucherId));
        } catch (Exception e) {
            // MQ 挂了：回滚 Redis 预扣，否则库存被白扣（少卖）
            stringRedisTemplate.execute(ROLLBACK_SCRIPT,
                    List.of(RedisConstants.SECKILL_STOCK_KEY + "{" + voucherId + "}",
                            RedisConstants.SECKILL_ORDER_KEY + "{" + voucherId + "}"),
                    String.valueOf(userId));
            log.error("发送秒杀消息失败，已回滚预扣 orderId={}", orderId, e);
            throw new BusinessException(500, "系统繁忙，请稍后再试");
        }

        return orderId;
    }

    /**
     * 消费者落库：三层幂等 + 条件更新 + 唯一索引兜底，成功后投递"30 分钟超时取消"消息。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createVoucherOrder(Long orderId, Long userId, Long voucherId) {
        // 第一层：按订单号查重（消息重投时直接返回）
        if (voucherOrderMapper.selectById(orderId) != null) {
            log.debug("订单已存在，跳过 orderId={}", orderId);
            return;
        }
        // 第二层：按用户 + 券查重
        if (voucherOrderMapper.countByUserAndVoucher(userId, voucherId) > 0) {
            log.debug("用户已购买，跳过 userId={} voucherId={}", userId, voucherId);
            return;
        }
        // 第三层：条件更新扣库存，返回 0 行说明 DB 库存不足
        int rows = seckillVoucherMapper.deductStock(voucherId);
        if (rows == 0) {
            throw new BusinessException(400, "数据库库存不足，订单作废");
        }

        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setPayValue(1000L);
        order.setActualValue(5000L);
        order.setStatus(1);
        // 唯一索引 uk_user_voucher 在这兜底，冲突抛 DuplicateKeyException
        voucherOrderMapper.insert(order);

        // 落库成功才开始计时：30 分钟未支付 → 自动取消并回补库存
        orderCancelProducer.sendDelay(orderId);
    }

    /* ==================== M5：支付 / 取消 / 对账 ==================== */

    @Override
    public boolean payOrder(Long orderId) {
        int rows = voucherOrderMapper.payIfPending(orderId);
        if (rows > 0) {
            log.info("订单支付成功 orderId={}", orderId);
            return true;
        }
        VoucherOrder order = voucherOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        if (order.getStatus() == 2) {
            return false;      // 幂等：重复支付不再处理
        }
        throw new BusinessException(400, "订单状态不允许支付，当前状态: " + order.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId) {
        VoucherOrder order = voucherOrderMapper.selectById(orderId);
        if (order == null) {
            log.warn("取消订单失败：不存在 orderId={}", orderId);
            return;
        }
        // 状态机：只有待支付能取消；重复调用返回 0 行，天然幂等
        int rows = voucherOrderMapper.cancelIfPending(orderId);
        if (rows == 0) {
            log.info("订单状态不是待支付，无需取消 orderId={} status={}", orderId, order.getStatus());
            return;
        }

        Long voucherId = order.getVoucherId();
        Long userId = order.getUserId();

        // 1. DB 库存回补
        seckillVoucherMapper.increaseStock(voucherId, 1);

        // 2. Redis 库存回补 + 移出已购集合（Lua 内先判 SISMEMBER，防重复回补）
        Long restored = stringRedisTemplate.execute(ROLLBACK_SCRIPT,
                List.of(RedisConstants.SECKILL_STOCK_KEY + "{" + voucherId + "}",
                        RedisConstants.SECKILL_ORDER_KEY + "{" + voucherId + "}"),
                String.valueOf(userId));

        log.info("订单已取消 orderId={} userId={} redisRestored={}", orderId, userId, restored);
    }

    /**
     * 对账：Redis 是"预扣层"，DB 是"权威层"，两者必须能对上。
     *
     * <p>两个守恒式：
     * <pre>
     *   DB:    dbStock    + dbSold    == 初始库存
     *   Redis: redisStock + redisSold == 初始库存
     *   一致性: dbSold == redisSold
     * </pre>
     */
    @Override
    public Map<String, Object> reconcile() {
        List<SeckillVoucher> vouchers = seckillVoucherMapper.selectList(null);
        List<Map<String, Object>> details = new ArrayList<>();
        boolean allOk = true;

        for (SeckillVoucher v : vouchers) {
            Long id = v.getVoucherId();
            String stockKey = RedisConstants.SECKILL_STOCK_KEY + "{" + id + "}";
            String orderKey = RedisConstants.SECKILL_ORDER_KEY + "{" + id + "}";

            int dbStock = v.getStock();
            int dbSold = voucherOrderMapper.countValidByVoucher(id);

            Long size = stringRedisTemplate.opsForSet().size(orderKey);
            long redisSold = size == null ? 0 : size;

            String redisStockStr = stringRedisTemplate.opsForValue().get(stockKey);
            long redisStock = redisStockStr == null ? -1 : Long.parseLong(redisStockStr);

            String initStr = stringRedisTemplate.opsForValue().get(RedisConstants.SECKILL_INIT_KEY + id);
            long init = initStr == null ? -1 : Long.parseLong(initStr);

            boolean soldMatch = dbSold == redisSold;
            boolean dbConserved = init < 0 || (long) dbStock + dbSold == init;
            boolean redisConserved = init < 0 || redisStock + redisSold == init;
            boolean ok = soldMatch && dbConserved && redisConserved;
            if (!ok) {
                allOk = false;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("voucherId", id);
            item.put("initStock", init);
            item.put("dbStock", dbStock);
            item.put("dbSold", dbSold);
            item.put("redisStock", redisStock);
            item.put("redisSold", redisSold);
            item.put("soldMatch", soldMatch);
            item.put("dbConserved", dbConserved);
            item.put("redisConserved", redisConserved);
            item.put("ok", ok);
            details.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("allOk", allOk);
        result.put("detail", details);
        if (!allOk) {
            log.warn("对账发现不一致: {}", details);
        }
        return result;
    }

    /* ==================== M4-A：同步版（仅用于压测对比） ==================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long seckillSync(Long voucherId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }

        SeckillVoucher voucher = seckillVoucherMapper.selectById(voucherId);
        if (voucher == null) {
            throw new BusinessException(400, "优惠券不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.getBeginTime())) {
            throw new BusinessException(400, "活动尚未开始");
        }
        if (now.isAfter(voucher.getEndTime())) {
            throw new BusinessException(400, "活动已结束");
        }

        if (voucherOrderMapper.countByUserAndVoucher(userId, voucherId) > 0) {
            throw new BusinessException(400, "每人限购一单");
        }
        if (voucher.getStock() <= 0) {
            throw new BusinessException(400, "库存不足");
        }

        seckillVoucherMapper.deductStockUnsafe(voucherId);

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
}
