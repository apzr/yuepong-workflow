package com.yuepong.workflow.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 业务模块关联流程 从表(业务用户对应流程节点的操作记录)
 *
 * @author apr
 * @date 2021/10/26 16:12:58
 **/
@Data
@TableName("s_sys_task_b")
public class SysTaskExt {
    /*
     * uuid
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /*
     * 主表id
     */
	private String hId;
	/*
     * 流程节点(发起人为开始节点)
     */
    private String node;
	/*
     * 操作人
     */
    private String user;
    /*
     * 操作人名
     */
    private String userName;
    /*
     * 操作人类型
     */
    private String userType;
	/*
     * 操作记录(1,2,3 同意,作废,打回草稿)
     */
    private String record;
    /*
     * 操作
     */
    private String opinion;
	/*
     * 操作时间
     */
    private String time;
	/*
     * 停留时长(从上个节点结束的时间到当前节点操作的时间)
     */
    private String operTime;
}
