package com.yuepong.workflow.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
@TableName("ACT_GE_BYTEARRAY")
public class BpmnByte {

    @TableId(type = IdType.INPUT,value = "ID_")
    private String id;

    @TableField("REV_")
    private int rev;

    @TableField("NAME_")
    private String name;

    @TableField("DEPLOYMENT_ID_")
    private String deploymentId;

    @TableField("BYTES_")
    private byte[] bytes;

    @TableField("GENERATED_")
    private int generated;

    public BpmnByte(){
    }
}
