package com.yuepong.workflow.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

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
     * 系统启用激活
     */
    private Boolean sysDisable;
    /*
     * 流程id
     */
    private String flowId;

    /*
     * 部署id
     */
    private String deploymentId;

    public SysFlow(){
    }

    public SysFlow(String id, String sysModel, Boolean sysDisable, String flowId, String deploymentId){
        this.id = id;
        this.sysModel = sysModel;
        this.sysDisable = sysDisable;
        this.flowId = flowId;
        this.deploymentId = deploymentId;
    }
}
