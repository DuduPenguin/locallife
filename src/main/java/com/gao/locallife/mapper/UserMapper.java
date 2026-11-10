package com.gao.locallife.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gao.locallife.entity.User;
import org.apache.ibatis.annotations.Mapper;

/** 继承 BaseMapper 即拥有单表 CRUD，无需写 XML。 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
