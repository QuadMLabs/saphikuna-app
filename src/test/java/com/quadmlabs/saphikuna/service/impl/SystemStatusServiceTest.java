package com.quadmlabs.saphikuna.service.impl;

import com.quadmlabs.saphikuna.repository.ProductRepository;
import com.quadmlabs.saphikuna.service.StorageService;
import com.quadmlabs.saphikuna.service.SystemStatusService.SystemStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Estado del sistema: nunca debe lanzar excepciones aunque algo falle. */
class SystemStatusServiceTest {

    private DataSource dataSource;
    private Connection connection;
    private StorageService storage;
    private ProductRepository products;
    private SystemStatusServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        storage = mock(StorageService.class);
        products = mock(ProductRepository.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(3)).thenReturn(true);
        when(storage.ping()).thenReturn(true);
        when(products.count()).thenReturn(20L);
        when(products.countImages()).thenReturn(75L);

        Clock clock = Clock.fixed(LocalDate.of(2026, 11, 7).atTime(10, 0).atZone(ZoneId.of("America/Guayaquil")).toInstant(),
                ZoneId.of("America/Guayaquil"));
        service = new SystemStatusServiceImpl(dataSource, storage, products, clock);
    }

    @Test
    void everythingWorking() {
        SystemStatus s = service.check();

        assertTrue(s.dbOk());
        assertTrue(s.storageOk());
        assertTrue(s.allOk());
        assertEquals(20, s.products());
        assertEquals(75, s.images());
        assertEquals("America/Guayaquil", s.zone());
        assertEquals(LocalDate.of(2026, 11, 7), s.today());
    }

    @Test
    void estimatesTheSpaceUsedByThePhotos() {
        SystemStatus s = service.check();

        assertEquals(6.75, s.estimatedStorageMb(), 0.001);                  // 75 x 0,09 MB
        assertEquals(6.75 / 1024 * 100, s.estimatedStoragePercent(), 0.001);
    }

    @Test
    void aDatabaseThatCannotConnectIsReportedNotThrown() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("sin conexión"));

        SystemStatus s = service.check();

        assertFalse(s.dbOk());
        assertFalse(s.allOk());
        assertEquals(-1, s.products());
        assertEquals(-1, s.images());
        verify(products, never()).count();      // no se intenta contar sin base
        assertTrue(s.storageOk());              // lo demás se revisa igual
    }

    @Test
    void anInvalidConnectionCountsAsDown() throws Exception {
        when(connection.isValid(3)).thenReturn(false);

        assertFalse(service.check().dbOk());
    }

    @Test
    void storageThatDoesNotRespondIsReported() {
        when(storage.ping()).thenReturn(false);

        SystemStatus s = service.check();

        assertFalse(s.storageOk());
        assertFalse(s.allOk());
        assertTrue(s.dbOk());
    }

    @Test
    void failingCountsDoNotBreakTheCheck() {
        when(products.count()).thenThrow(new IllegalStateException("tabla no disponible"));

        SystemStatus s = service.check();

        assertTrue(s.dbOk());
        assertEquals(-1, s.products());
    }

    @Test
    void noImagesMeansNoEstimatedSpace() {
        SystemStatus s = new SystemStatus(true, 1, true, 1, 0, -1, "UTC", LocalDate.now());
        assertEquals(0.0, s.estimatedStorageMb(), 0.0);
    }
}
