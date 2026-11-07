package com.quadmlabs.saphikuna.service.impl;

import com.quadmlabs.saphikuna.repository.ProductRepository;
import com.quadmlabs.saphikuna.service.StorageService;
import com.quadmlabs.saphikuna.service.SystemStatusService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Clock;
import java.time.LocalDate;

@Service
public class SystemStatusServiceImpl implements SystemStatusService {

    private static final Logger log = LoggerFactory.getLogger(SystemStatusServiceImpl.class);

    private final DataSource dataSource;
    private final StorageService storageService;
    private final ProductRepository productRepository;
    private final Clock clock;

    public SystemStatusServiceImpl(
            DataSource dataSource,
            StorageService storageService,
            ProductRepository productRepository,
            Clock clock) {

        this.dataSource = dataSource;
        this.storageService = storageService;
        this.productRepository = productRepository;
        this.clock = clock;
    }

    @Override
    public SystemStatus check() {

        // Base de datos
        boolean dbOk;
        long started = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            dbOk = connection.isValid(3);
        } catch (Exception e) {
            log.warn("La base de datos no responde: {}", e.getMessage());
            dbOk = false;
        }
        long dbMillis = elapsedMillis(started);

        // Totales (solo si la base responde)
        long products = -1;
        long images = -1;
        if (dbOk) {
            try {
                products = productRepository.count();
                images = productRepository.countImages();
            } catch (RuntimeException e) {
                log.warn("No se pudieron contar los productos y fotos: {}", e.getMessage());
            }
        }

        // Supabase Storage
        started = System.nanoTime();
        boolean storageOk = storageService.ping();
        long storageMillis = elapsedMillis(started);

        return new SystemStatus(
                dbOk, dbMillis, storageOk, storageMillis,
                products, images,
                clock.getZone().getId(), LocalDate.now(clock));
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
