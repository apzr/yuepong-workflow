package com.yuepong.workflow.dto;

import lombok.Data;

/**
 * TaskParam
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/10/30 16:43:11
 **/
@Data
public class TaskParam {

    private String type;
    private String route;
    private String userId;
    private String dataId;

}
