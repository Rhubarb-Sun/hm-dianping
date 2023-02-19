package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private SeckillVoucherServiceImpl seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 判断在有效时间内
        LocalDateTime beginTime = voucher.getBeginTime();
        if (LocalDateTime.now().isBefore(beginTime)) {
            return Result.fail("before start");
        }
        LocalDateTime endTime = voucher.getEndTime();
        if (LocalDateTime.now().isAfter(endTime)) {
            return Result.fail("after end");
        }
        // 判断库存
        if (voucher.getStock() < 1) {
            log.info("stock not suffices");
            return Result.fail("stock not suffices");
        }

        Long userId = UserHolder.getUser().getId();
        // 获取字符串常量池的值，作为监视器。
        synchronized (userId.toString().intern()) {
            log.debug("获取到锁！");
            // 通过代理对象调用，确保事务注解生效
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            log.debug("proxy: {}", proxy);
            return proxy.createOrder(voucherId);
        }
    }

    /**
     * spring事务通过aop代理对象实现，且事务要在悲观锁内部，确保安全性。
     * 如果锁在事务内部，锁释放后到spring事务提交前有可能其他线程进入，可能导致一人一单的校验逻辑失效（未提交）
     *
     * synchronized不加在方法上。否则锁了this，导致串行执行，虽然可以保证一人一单，但是影响多个用户同时抢券，性能太差。
     * 所以对用户ID加锁。但是要注意不能对Long对象加锁（失效），不能对toString加锁（失效），可以对intern加锁
     */
    @Transactional
    @Override
    public Result createOrder(Long voucherId) {
        // 确保一人一单，要先校验订单没有该用户和优惠券的记录
        // 根据用户和优惠券查询订单表
        Long userId = UserHolder.getUser().getId();
        int count = this.query().eq("user_id", userId).eq("voucher_id", voucherId).count();

        log.info("已经下单：{} 次", count);
        if (count >= 1) {
            log.info("User {} can buy at most 1 for each kind of voucher {}", userId, voucherId);
            return Result.fail("Each user can buy at most 1 for each kind of voucher");
        }

        // 扣库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();

        // 失败
        if (!success) {
            log.info("seckill failed");
            return Result.fail("seckill failed");
        }

        log.info("用户 {} 库存扣减成功", userId);

        // 7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 7.1.订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 7.2.用户id
        voucherOrder.setUserId(userId);
        // 7.3.代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        // 7.返回订单id
        return Result.ok(orderId);
    }
}
