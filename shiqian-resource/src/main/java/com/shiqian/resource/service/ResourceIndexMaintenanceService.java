package com.shiqian.resource.service;

import com.shiqian.resource.dto.IndexConsistencyVO;

public interface ResourceIndexMaintenanceService {

    long rebuildIndex();

    IndexConsistencyVO checkConsistency();
}
