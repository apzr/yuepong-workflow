package com.yuepong.workflow.param;

import com.yuepong.workflow.dto.SysTask;
import lombok.Data;

/**
 * ProcessInstanceList
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/11/01 15:26:20
 **/
@Data
public class ProcessInstanceDTO{

    private String instanceId;

    private String status;

    private String currentNodeId;//节点

    private String currentNodeName;

    private String currentAssign;//当前执行人

    private String currentAssignName;

    private String creator;//发起人

    private String creatorName;

    private String createTime;

    private String endTime;

    private SysTask header;

}
