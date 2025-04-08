package com.sky.mapper;

import com.sky.annotation.AutoFill;
import com.sky.entity.User;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    /**
     * 根据openid查询用户
     * @param openid
     * @return
     */
    @Select("select * from user where openid=#{openid}")
    User getByOpenid(String openid);

    /**
     * 新增用户
     * @param user
     */
    @Insert("insert into user (openid, name,create_time,sex,phone,id_number,avatar)" +
            "values (#{openid},#{name},#{createTime},#{sex},#{phone},#{idNumber},#{avatar})")
    @Options(useGeneratedKeys = true,keyProperty = "id")
    void insert(User user);
}
