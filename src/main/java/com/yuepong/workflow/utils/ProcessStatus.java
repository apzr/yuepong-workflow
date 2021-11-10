package com.yuepong.workflow.utils;

/**
 * Opinion
 * 操作方式
 * <br/>
 *
 * @author apr
 * @date 2021/10/28 13:56:56
 **/
public enum ProcessStatus {

    ACTIVE{
        public String getMsg(){
            return "激活";
        }
        public String getCode(){
            return "1";
        }
    },
    SUSPENDED{
        public String getMsg(){
            return "挂起";
        }
        public String getCode(){
            return "2";
        }
    },
    COMPLETE{
        public String getMsg(){
            return "完成";
        }
        public String getCode(){
            return "3";
        }
    },
    SHUTDOWN{
        public String getMsg(){
            return "作废";
        }
        public String getCode(){
            return "4";
        }
    };

    public abstract String getMsg();//定义抽象方法
    public abstract String getCode();//定义抽象方法
}
