package com.backend.sigcapi.services;

import java.util.List;
import com.backend.sigcapi.dto.RequestDTO;
import com.backend.sigcapi.dto.RuleDTO;
import com.backend.sigcapi.entities.Rule;

public interface IRuleService {
    List<Rule> all();
    Rule save(RuleDTO ruleDto);
    List<String> consultLegalDiagnosis(RequestDTO request, int documentSize);
}