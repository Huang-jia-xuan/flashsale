package com.ttnn.flashsale.service;

import com.ttnn.flashsale.entity.Product;

import java.util.List;

public interface ProductService {

    /** 查询全部商品列表 */
    List<Product> listProducts();

    /** 查询商品详情（优先读缓存） */
    Product getProductDetail(Long id);
}
