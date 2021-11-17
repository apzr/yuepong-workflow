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

    private String currentNodeName;

    private String currentNodeId;

    private String currentAssign;

    private String currentAssignName;

    private String creator;

    private String createTime;

    private String endTime;

    private SysTask header;

}
