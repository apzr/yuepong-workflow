package com.yuepong.workflow.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 业务表单数据绑定流程实例
 * @date 2021/10/26 16:12:47
 **/
@Data
@TableName("s_sys_task_h")
public class SysTask {
    /*
     * uuid
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /*
     * s_sys_flow_h绑定的系统模块
     */
    private String sKey;
    /*
     * 业务数据id
     */
    private String sId;
    /*
     * 流程启动成功后返回的实例id
     */
    private String taskId;
    /*
     * 流程启动成功后返回的实例id
     */
    private String route;

    /*
     * 流程启动成功后返回的实例id
     */
    private String status;
}
