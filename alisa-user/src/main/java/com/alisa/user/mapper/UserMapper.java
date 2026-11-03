package com.alisa.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.alisa.user.entity.User;

@Mapper
public interface UserMapper {
    User findUserByID(String id);

    User findUserByName(String name);

    User findUserByEmail(String name);

    boolean saveUser(User user);
    int updatePasswordIfMatches(@Param("id") String id, @Param("previous") String previous,
            @Param("password") String password);
}

