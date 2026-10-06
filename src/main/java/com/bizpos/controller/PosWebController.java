package com.bizpos.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PosWebController {

    @GetMapping("/pos")
    public String posPage() {
        return "pos";
    }
}
