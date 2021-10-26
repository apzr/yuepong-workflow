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
@TableName("s_sys_flow_b")
public class SysFlow {
    private static final long serialVersionUID = 1L;
    /*
     * uuid
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /*
     * 系统模块
     */
    private String sys_model;
    /*
     * 系统表
     */
    private String sys_table;
    /*
     * 流程id
     */
    private String flow_id;
}
