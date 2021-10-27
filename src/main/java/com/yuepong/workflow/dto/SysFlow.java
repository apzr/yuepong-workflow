package com.yuepong.workflow.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.List;

/**
 * SysTask
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/10/26 16:09:39
 **/
@Data
@TableName("s_sys_flow_h")
public class SysFlow {
    /*
     * uuid
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /*
     * 系统模块
     */
    private String sysModel;
    /*
     * 系统表
     */
    private String sysTable;
    /*
     * 流程id
     */
    private String flowId;

    public SysFlow(){

    }

    public SysFlow(String id, String sysModel, String sysTable, String flowId){
        this.id = id;
        this.sysModel = sysModel;
        this.sysTable = sysTable;
        this.flowId = flowId;
    }
}
