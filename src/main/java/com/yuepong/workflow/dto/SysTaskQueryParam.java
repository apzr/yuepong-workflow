package com.yuepong.workflow.dto;

import lombok.Data;

import java.util.List;

@Data
public class SysTaskQueryParam {

    public String id;
    public String sKey;
    public String sId;
    public String taskId;
    public String route;
    public List<String> status;

}
