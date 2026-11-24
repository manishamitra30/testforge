package com.testforge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.testforge.backend.dto.DashboardDtos.Activity;
import com.testforge.backend.repository.DashboardRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock ProjectService projectService;
    @Mock DashboardQueryService queries;
    @Mock DashboardRepository repository;
    @InjectMocks DashboardService service;

    @Test
    void summary_checksOwnershipBeforeReadingTheCachedNumbers() {
        service.summary(1L);

        InOrder order = inOrder(projectService, queries);
        order.verify(projectService).getOwnedProject(1L);
        order.verify(queries).summary(1L);
    }

    @Test
    void everyEndpoint_refusesAProjectTheUserDoesNotOwn_beforeTouchingAnyData() {
        doThrow(new AccessDeniedException("no access")).when(projectService).getOwnedProject(2L);

        assertThatThrownBy(() -> service.summary(2L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.passRateTrend(2L, 30)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.defectsBySeverity(2L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.recentActivity(2L, 10)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.flakyTests(2L, 10)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(queries, repository);
    }

    @ParameterizedTest(name = "days={0} is used as {1}")
    @CsvSource({"-5,1", "0,1", "1,1", "30,30", "365,365", "1000,365"})
    void passRateTrend_keepsTheWindowBetweenOneAndAYear(int requested, int used) {
        service.passRateTrend(1L, requested);

        verify(queries).passRateTrend(1L, used);
    }

    @ParameterizedTest(name = "runs={0} is used as {1}")
    @CsvSource({"-1,2", "1,2", "2,2", "10,10", "50,50", "500,50"})
    void flakyTests_keepsTheRunWindowBetweenTwoAndFifty(int requested, int used) {
        service.flakyTests(1L, requested);

        verify(queries).flakyTests(1L, used);
    }

    @ParameterizedTest(name = "limit={0} is used as {1}")
    @CsvSource({"-3,1", "0,1", "15,15", "50,50", "999,50"})
    void recentActivity_keepsTheLimitBetweenOneAndFifty(int requested, int used) {
        service.recentActivity(1L, requested);

        verify(repository).recentActivity(1L, used);
    }

    @Test
    void recentActivity_mapsDatabaseRowsToActivityItems() {
        when(repository.recentActivity(1L, 15)).thenReturn(List.of(row("RUN", 5L, "Live demo", "COMPLETED", "2026-11-24T10:29:30")));

        List<Activity> items = service.recentActivity(1L, 15);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.type()).isEqualTo("RUN");
            assertThat(item.refId()).isEqualTo(5L);
            assertThat(item.title()).isEqualTo("Live demo");
            assertThat(item.detail()).isEqualTo("COMPLETED");
            assertThat(item.at()).isEqualTo("2026-11-24T10:29:30");
        });
    }

    private static DashboardRepository.ActivityRow row(String type, long refId, String title, String detail, String at) {
        return new DashboardRepository.ActivityRow() {
            @Override public String getType() { return type; }
            @Override public long getRefId() { return refId; }
            @Override public String getTitle() { return title; }
            @Override public String getDetail() { return detail; }
            @Override public String getAt() { return at; }
        };
    }
}