package com.quadmlabs.saphikuna.service.impl;

import com.quadmlabs.saphikuna.entity.Size;
import com.quadmlabs.saphikuna.repository.SizeRepository;
import com.quadmlabs.saphikuna.service.SizeService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SizeServiceImpl implements SizeService {
    
    private final SizeRepository sizeRepository;

    public SizeServiceImpl(SizeRepository sizeRepository) {
        this.sizeRepository = sizeRepository;
    }

    @Override
    public List<Size> findAll() {
        return sizeRepository.findAll();
    }
}
