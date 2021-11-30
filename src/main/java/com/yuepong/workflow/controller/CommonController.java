package com.yuepong.workflow.controller;

import io.swagger.annotations.Api;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;


/**
 * CommonController
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/11/18 10:21:09
 **/
@Controller
@Api(tags = "通用接口")
public class CommonController {

    @GetMapping("/ver")
    @ResponseBody
    public String getVersion() {
        return "1.3.0";
    }

}
