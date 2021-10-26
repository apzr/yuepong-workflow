package com.yuepong.workflow;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * @author : Apr
 * description : TestSuite编排测试
 */

public class ActivitiTestSuite extends TestSuite {
    public static Test suite() {
        //创建TestSuite对象
        TestSuite suite = new TestSuite();
        //为TestSuite添加一个测试用例集合，参数为：ClasstestClass

        //通过参数可以知道，其实该参数就是TestCase的子类
        //suite.addTestSuite(DeployControllerTest.class);

        //添加一个具体的测试用例
        // Test test1 = TestSuite.createTest(DeployControllerTest.class, "t1");
        // suite.addTest(test1);
        // Test test2 = TestSuite.createTest(DeployControllerTest.class, "t2");
        // suite.addTest(test2);
        // Test test3 = TestSuite.createTest(DeployControllerTest.class, "t3");
        // suite.addTest(test3);
        // Test test4 = TestSuite.createTest(DeployControllerTest.class, "t4");
        // suite.addTest(test4);

        return suite;

    }
}