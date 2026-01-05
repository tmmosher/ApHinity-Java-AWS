package com.aphinity.client_analytics_core.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendController {
    // TODO change this get mapping to intercept api requests
    @GetMapping("/")
    public String forwardToIndex() {
        return "forward:index.html";
    }
}
