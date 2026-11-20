package com.backend.sigcapi.services;

import java.util.List;
import com.backend.sigcapi.dto.RequestDTO;
import com.backend.sigcapi.entities.Rule;

public interface IExpertEngineService {
    List<String> executeRulesBroken(RequestDTO request, int documentSize, List<Rule> rules);
}