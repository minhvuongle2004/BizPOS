package com.bizpos.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CategoryWebController {

    @GetMapping("/categories")
    public String categoriesPage() {
        return "categories";
    }
}
