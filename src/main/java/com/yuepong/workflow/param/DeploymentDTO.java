package com.yuepong.workflow.param;

import lombok.Data;

/**
 * DeplomentDTO
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/10/26 15:31:37
 **/
@Data
public class DeploymentDTO {
    private String id;
    private String name;

    public DeploymentDTO(){

    }

    public DeploymentDTO(String id, String name){
        this.id = id;
        this.name = name;
    }
}
