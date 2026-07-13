package com.tip.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class PatternsDisabledException extends RuntimeException {

    public PatternsDisabledException() {
        super("patterns_disabled");
    }
}
