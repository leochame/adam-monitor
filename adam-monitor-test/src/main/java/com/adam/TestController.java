package com.adam;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("/monitor")
public class TestController {

    @PostMapping
    public void TestPost(){

    }

}
