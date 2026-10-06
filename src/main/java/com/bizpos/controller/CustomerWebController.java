package com.bizpos.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CustomerWebController {

    @GetMapping("/customers")
    public String customersPage() {
        return "customers";
    }
}
