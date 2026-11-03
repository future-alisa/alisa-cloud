package com.alisa.user.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserMapperTest {
    @Test void relocatedMapperLoadsAndFindsUsersByTheExistingPrimaryKey() throws Exception {
        Configuration configuration = new Configuration();
        try (var resource = getClass().getResourceAsStream("/mapper/UserMapper.xml")) {
            assertNotNull(resource);
            new XMLMapperBuilder(resource, configuration, "mapper/UserMapper.xml", configuration.getSqlFragments()).parse();
        }
        assertTrue(configuration.hasMapper(UserMapper.class));
        assertTrue(configuration.getMappedStatement(UserMapper.class.getName() + ".findUserByID")
                .getBoundSql("existing-id").getSql().contains("user_id = ?"));
        assertTrue(configuration.hasStatement(UserMapper.class.getName() + ".updatePasswordIfMatches"));
    }
}
