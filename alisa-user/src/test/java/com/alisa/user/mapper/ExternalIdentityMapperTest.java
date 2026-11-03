package com.alisa.user.mapper;

import com.alisa.user.entity.ExternalIdentityLink;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.apache.ibatis.session.SqlSession;
import static org.junit.jupiter.api.Assertions.*;

class ExternalIdentityMapperTest {
    SingleConnectionDataSource dataSource;
    SqlSession session;
    ExternalIdentityMapper mapper;
    @BeforeEach void setup() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";MODE=MySQL", true);
        ScriptUtils.executeSqlScript(dataSource.getConnection(), new FileSystemResource("sql/external-identity-schema.sql"));
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new ClassPathResource("mapper/ExternalIdentityMapper.xml"));
        session = factory.getObject().openSession(true);
        mapper = session.getMapper(ExternalIdentityMapper.class);
    }
    @AfterEach void close() {
        if (session != null) session.close();
        if (dataSource != null) dataSource.destroy();
    }
    void link(String provider, String issuer, String subject, String user) {
        ExternalIdentityLink identity = new ExternalIdentityLink();
        identity.setProviderId(provider); identity.setIssuer(issuer); identity.setSubject(subject); identity.setUserId(user);
        mapper.insertLink(identity);
    }
    @Test void providerIssuerAndSubjectFormTheUniqueIdentityKey() {
        String alice = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", bob = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        link("company", "issuer-a", "Subject", alice);
        link("company", "issuer-b", "Subject", bob);
        link("company", "issuer-a", "subject", bob);
        assertEquals(alice, mapper.findUserId("company", "issuer-a", "Subject"));
        assertEquals(bob, mapper.findUserId("company", "issuer-b", "Subject"));
        assertEquals(bob, mapper.findUserId("company", "issuer-a", "subject"));
        assertThrows(org.apache.ibatis.exceptions.PersistenceException.class, () -> link("company", "issuer-a", "Subject", bob));
    }
    @Test void deletingAnotherUsersBindingDoesNotUnlinkIt() {
        String alice = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        link("company", "issuer-a", "subject", alice);
        assertEquals(0, mapper.deleteOwned("company", "issuer-a", "subject", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        assertEquals(1, mapper.findByUserId(alice).size());
        assertEquals(1, mapper.deleteOwned("company", "issuer-a", "subject", alice));
        assertNull(mapper.findUserId("company", "issuer-a", "subject"));
    }
}
