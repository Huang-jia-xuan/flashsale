package com.ttnn.flashsale.controller;

import com.ttnn.flashsale.common.Result;
import com.ttnn.flashsale.entity.Product;
import com.ttnn.flashsale.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    /** 查询商品列表 */
    @GetMapping
    public Result<List<Product>> list() {
        return Result.success(productService.listProducts());
    }

    /** 查询商品详情（走 Redis 缓存） */
    @GetMapping("/{id}")
    public Result<Product> detail(@PathVariable Long id) {
        Product product = productService.getProductDetail(id);
        if (product == null) {
            return Result.fail(404, "商品不存在");
        }
        return Result.success(product);
    }
}
