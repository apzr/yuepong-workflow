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

    APPROVE{//next
        public String getMsg(){
            return "同意";
        }
        public String getCode(){
            return "1";
        }
    },
    RECALL{//prev
        public String getMsg(){
            return "撤回";
        }
        public String getCode(){
            return "2";
        }
    },
    REJECT{//start
        public String getMsg(){
            return "驳回";
        }
        public String getCode(){
            return "3";
        }
    },
    CANCEL{//end
        public String getMsg(){
            return "作废";
        }
        public String getCode(){
            return "2";
        }
    };

    public abstract String getMsg();//定义抽象方法
    public abstract String getCode();//定义抽象方法
}
