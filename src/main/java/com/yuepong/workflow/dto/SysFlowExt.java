package com.yuepong.workflow.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 业务模块关联流程 从表(流程节点关联业务操作用户或改变走向的值)
 *
 * @author apr
 * @date 2021/10/26 16:10
 */
@Data
@TableName("s_sys_flow_b")
public class SysFlowExt {
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
     * 流程节点
     */
    private String node;
    /*
     * 节点类型
     */
    private String nodeType;
    /*
     * 节点attr
     */
    private String nodeSkip;
    /*
     * 业务变量
     */
    private String field;
    /*
     * 条件
     */
    private String conditions;
    /*
     * 值
     */
    private String value;
    /*
     * 用户id
     */
    private String operation;
    /*
     * 用户名
     */
    private String operName;
    /*
     * 用户类型
     */
    private String userType;
    /*
     * 下一个节点
     */
    private String nextNode;

}
