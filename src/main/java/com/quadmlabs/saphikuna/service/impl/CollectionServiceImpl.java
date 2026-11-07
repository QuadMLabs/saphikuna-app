package com.quadmlabs.saphikuna.service.impl;

import org.springframework.stereotype.Service;
import com.quadmlabs.saphikuna.entity.Collection;
import com.quadmlabs.saphikuna.repository.CollectionRepository;
import com.quadmlabs.saphikuna.service.CollectionService;

import java.util.List;

@Service
public class CollectionServiceImpl implements CollectionService{
 
    private final CollectionRepository collectionRepository;

    public CollectionServiceImpl(CollectionRepository collectionRepository) {
        this.collectionRepository = collectionRepository;
    }

    @Override
    public List<Collection> findAll() {
        return collectionRepository.findAll();
    }
}
