package com.backend.sigcapi.services;

import java.util.List;
import com.backend.sigcapi.entities.Standard;

public interface IStandardService {
    List<Standard> all();
    Standard save(Standard standard);
}