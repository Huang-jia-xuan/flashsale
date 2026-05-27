package com.ttnn.flashsale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ttnn.flashsale.entity.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 扣减库存（原子操作，WHERE 条件保证库存不会被扣成负数）
     *
     * @return 受影响行数，0 表示库存不足
     */
    @Update("UPDATE t_product SET stock = stock - #{quantity} WHERE id = #{productId} AND stock >= #{quantity}")
    int deductStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
