package com.bizpos.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProductWebController {

    @GetMapping("/products")
    public String productsPage() {
        return "products";
    }
}
