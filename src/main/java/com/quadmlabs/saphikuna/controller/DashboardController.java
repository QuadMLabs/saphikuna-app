package com.quadmlabs.saphikuna.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class DashboardController {

    @GetMapping
    public String dashboard(org.springframework.ui.Model model) {
        model.addAttribute("activeMenu", "home");
        return "admin/dashboard";
    }
    
}
