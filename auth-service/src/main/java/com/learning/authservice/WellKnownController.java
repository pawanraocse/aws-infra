package com.learning.authservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Handles requests to /.well-known/* to suppress 404 errors from browser extensions and tools.
 * Returns 204 No Content for all such requests.
 */
@Controller
public class WellKnownController {
    private static final Logger log = LoggerFactory.getLogger(WellKnownController.class);

    @RequestMapping("/.well-known/**")
    public ResponseEntity<Void> handleWellKnown() {
        log.info("operation=handleWellKnown, request to /.well-known/* suppressed");
        return ResponseEntity.noContent().build();
    }
}

