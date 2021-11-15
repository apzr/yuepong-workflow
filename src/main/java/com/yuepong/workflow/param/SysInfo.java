package com.yuepong.workflow.param;

import com.yuepong.workflow.dto.SysFlow;
import com.yuepong.workflow.dto.SysFlowExt;
import lombok.Data;

import java.util.List;

/**
 * SysInfo
 * 流程保存参数封装
 * <br/>
 *
 * @author apr
 * @date 2021/10/27 13:51:21
 **/
@Data
public class SysInfo {
    private SysFlow flow;
    private List<SysFlowExt> nodes;
}
