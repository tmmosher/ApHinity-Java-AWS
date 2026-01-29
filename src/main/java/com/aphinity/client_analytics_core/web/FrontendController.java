package com.aphinity.client_analytics_core.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class FrontendController {
    @GetMapping({"/"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
