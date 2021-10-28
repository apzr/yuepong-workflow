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
    APPROVE{
        public String getMsg(){
            return "同意";
        }
        public String getCode(){
            return "1";
        }
    },
    CANCEL{
        public String getMsg(){
            return "作废";
        }
        public String getCode(){
            return "2";
        }
    },
    REJECT{
        public String getMsg(){
            return "驳回";
        }
        public String getCode(){
            return "3";
        }
    };
    public abstract String getMsg();//定义抽象方法
    public abstract String getCode();//定义抽象方法
}
