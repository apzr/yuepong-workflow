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
@TableName("s_sys_flow_h")
public class SysFlowExt {
    private static final long serialVersionUID = 1L;
    /*
     * uuid
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /*
     * 主表id
     */
    private String h_id;
    /*
     * 流程节点
     */
    private String node;
    /*
     * 节点类型
     */
    private String type;
    /*
     * 业务变量
     */
    private String filed;
    /*
     * 条件
     */
    private String conditions;
    /*
     * 值
     */
    private String value;
    /*
     * 下一个节点
     */
    private String next_node;
}
