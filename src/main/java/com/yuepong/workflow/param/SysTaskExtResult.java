package com.yuepong.workflow.param;

import com.yuepong.workflow.dto.SysTaskExt;
import lombok.Data;

/**
 * SysTaskExtResult
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/11/16 18:09:11
 **/
@Data
public class SysTaskExtResult implements Comparable<SysTaskExtResult>{
    /*
     * uuid
     */
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
     * 流程节点(发起人为开始节点)
     */
    private String nodeName;
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
    private long time;
	/*
     * 停留时长(从上个节点结束的时间到当前节点操作的时间)
     */
    private String operTime;

    public SysTaskExtResult(){

    }

    public static SysTaskExtResult newInstance(SysTaskExt taskInfo, String nodeName){
        SysTaskExtResult s = new SysTaskExtResult();
        s.id = taskInfo.getId();
        s.hId = taskInfo.getHId();
        s.node = taskInfo.getNode();
        s.nodeName = nodeName;
        s.user = taskInfo.getUser();;
        s.userName = taskInfo.getUserName();;
        s.userType = taskInfo.getUserType();
        s.record = taskInfo.getRecord();
        s.opinion = taskInfo.getOpinion();
        try{
            s.time = Long.parseLong(taskInfo.getTime());
        }catch(Exception e){
            s.time = 0L;
        }
        s.operTime = taskInfo.getOperTime();

        return s;
    }

    @Override
    public int compareTo(SysTaskExtResult s) {
        if(this.time == s.getTime())
            return 0;
        else if(this.time > s.getTime())
            return -1;
        else
            return 1;
    }
}
