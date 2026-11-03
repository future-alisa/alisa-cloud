package com.alisa.user.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.alisa.user.entity.ExternalIdentityLink;

@Mapper
public interface ExternalIdentityMapper {
    String findUserId(@Param("providerId") String providerId, @Param("issuer") String issuer, @Param("subject") String subject);
    int insertLink(ExternalIdentityLink link);
    List<ExternalIdentityLink> findByUserId(@Param("userId") String userId);
    int deleteOwned(@Param("providerId") String providerId, @Param("issuer") String issuer,
            @Param("subject") String subject, @Param("userId") String userId);
}
