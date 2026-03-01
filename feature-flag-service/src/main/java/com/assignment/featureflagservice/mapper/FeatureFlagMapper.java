package com.assignment.featureflagservice.mapper;

import com.assignment.featureflagservice.dto.AuditResponse;
import com.assignment.featureflagservice.dto.Environments;
import com.assignment.featureflagservice.dto.FlagResponse;
import com.assignment.featureflagservice.entity.AuditHistory;
import com.assignment.featureflagservice.entity.FeatureFlag;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface FeatureFlagMapper {

    @Mapping(target = "environments", expression = "java(toEnvironments(flag))")
    FlagResponse toFlagResponse(FeatureFlag flag);
    List<FlagResponse> toFlagResponseList(List<FeatureFlag> flags);
    AuditResponse toAuditResponse(AuditHistory auditHistory);
    List<AuditResponse> toAuditResponseList(List<AuditHistory> auditHistories);
    default Environments toEnvironments(FeatureFlag flag) {
        Environments environments = new Environments();
        environments.setDev(flag.isDevEnabled());
        environments.setStaging(flag.isStagingEnabled());
        environments.setProd(flag.isProdEnabled());
        return environments;
    }
}