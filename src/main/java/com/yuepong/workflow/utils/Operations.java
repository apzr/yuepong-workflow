package com.yuepong.workflow.utils;

/**
 * Opinion
 * 操作方式
 * <br/>
 *
 * @author apr
 * @date 2021/10/28 13:56:56
 **/
public enum Operations{

    APPROVE{//next: 下一个节点
        public String getMsg(){
            return "同意";
        }
        public String getCode(){
            return "1";
        }
    },
    RECALL{//prev: 上一个节点
        public String getMsg(){
            return "重审";
        }
        public String getCode(){
            return "2";
        }
    },
    REJECT{//start: 第一个节点
        public String getMsg(){
            return "重启";
        }
        public String getCode(){
            return "3";
        }
    },
    CANCEL1{//end:  最后一个节点
        public String getMsg(){
            return "驳回";
        }
        public String getCode(){
            return "4";
        }
    },
    CANCEL2{//end:  最后一个节点
        public String getMsg(){
            return "撤回";
        }
        public String getCode(){
            return "5";
        }
    };

    public abstract String getMsg();//定义抽象方法
    public abstract String getCode();//定义抽象方法
}
