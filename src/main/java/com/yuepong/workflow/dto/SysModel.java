package com.yuepong.workflow.dto;

import lombok.Data;

import java.util.List;

/**
 * SysModel
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/10/30 15:56:14
 **/
@Data
public class SysModel {
    SysFlow flow;

    List<SysFlowExt> nodes;
}
