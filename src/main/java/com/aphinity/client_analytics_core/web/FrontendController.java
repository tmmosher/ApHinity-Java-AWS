package com.aphinity.client_analytics_core.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendController {
    // example for now. For SPA routing this will need to be more of a catch-all
    // to handle API requests as a forward to index.html
    @GetMapping("/")
    public String forwardToIndex() {
        return "forward:index.html";
    }
}
