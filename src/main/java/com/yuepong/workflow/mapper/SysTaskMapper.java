package com.yuepong.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuepong.workflow.dto.SysTask;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SysFlowMapper
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/10/26 16:25:29
 **/
@Mapper
@Component
public interface SysTaskMapper extends BaseMapper<SysTask> {

}
