package com.yuepong.workflow.controller;

import com.yuepong.jdev.api.bean.ResponseResult;
import com.yuepong.jdev.code.CodeMsgs;
import com.yuepong.jdev.exception.BizException;
import com.yuepong.workflow.dto.SysTask;
import com.yuepong.workflow.param.PermissionResult;
import com.yuepong.workflow.param.TaskCompleteParam;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.activiti.engine.task.Task;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * CommonController
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/11/18 10:21:09
 **/
@Controller
@Api(tags = "通用")
public class CommonController {

    @GetMapping("/ver")
    @ResponseBody
    public String getVersion() {
        return "0.0.1";
    }

}
