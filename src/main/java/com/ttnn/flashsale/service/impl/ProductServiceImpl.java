package com.ttnn.flashsale.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttnn.flashsale.entity.Product;
import com.ttnn.flashsale.mapper.ProductMapper;
import com.ttnn.flashsale.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 商品服务实现
 * <p>
 * 【阶段三 · Redis 缓存】查询商品详情时，优先从 Redis 读取；缓存未命中则回源数据库并回写缓存。
 */
@Service
@Slf4j
public class ProductServiceImpl implements ProductService {

    private static final String PRODUCT_CACHE_PREFIX = "product:detail:";
    private static final long CACHE_TTL_MINUTES = 30;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<Product> listProducts() {
        return productMapper.selectList(null);
    }

    @Override
    public Product getProductDetail(Long id) {
        String cacheKey = PRODUCT_CACHE_PREFIX + id;

        // ① 优先从 Redis 缓存读取
        String json = redisTemplate.opsForValue().get(cacheKey);
        if (json != null) {
            log.debug("命中缓存: key={}", cacheKey);
            try {
                return objectMapper.readValue(json, Product.class);
            } catch (JsonProcessingException e) {
                log.warn("缓存反序列化失败，将回源数据库", e);
            }
        }

        // ② 缓存未命中，查询数据库
        Product product = productMapper.selectById(id);

        if (product != null) {
            // ③ 回写缓存
            try {
                redisTemplate.opsForValue().set(
                        cacheKey,
                        objectMapper.writeValueAsString(product),
                        CACHE_TTL_MINUTES,
                        TimeUnit.MINUTES
                );
                log.debug("写入缓存: key={}", cacheKey);
            } catch (JsonProcessingException e) {
                log.warn("缓存序列化失败", e);
            }
        }

        return product;
    }
}
