package br.gov.es.openpmo.service.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.gov.es.openpmo.dto.schedule.CostSchedule;
import br.gov.es.openpmo.dto.schedule.DistributionStrategy;
import br.gov.es.openpmo.dto.schedule.ScheduleUpdateParamDto;
import br.gov.es.openpmo.exception.NegocioException;
import br.gov.es.openpmo.model.schedule.Schedule;
import br.gov.es.openpmo.model.schedule.Step;
import br.gov.es.openpmo.model.workpacks.CostAccount;
import br.gov.es.openpmo.repository.BaselineRepository;
import br.gov.es.openpmo.repository.CostAccountRepository;
import br.gov.es.openpmo.repository.ScheduleRepository;
import br.gov.es.openpmo.repository.StepRepository;
import br.gov.es.openpmo.service.workpack.WorkpackService;
import br.gov.es.openpmo.utils.ApplicationMessage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.modelmapper.ModelMapper;

/**
 * SD-10605: PUT /schedules/{id} - orquestracao do ScheduleService.update.
 */
public class ScheduleServiceUpdateTest {

  private StepRepository stepRepository;
  private ScheduleRepository scheduleRepository;
  private CostAccountRepository costAccountRepository;
  private UpdateStatusService updateStatusService;
  private ScheduleService service;
  private Schedule schedule;

  @Before
  public void setUp() {
    this.stepRepository = mock(StepRepository.class);
    this.scheduleRepository = mock(ScheduleRepository.class);
    this.costAccountRepository = mock(CostAccountRepository.class);
    this.updateStatusService = mock(UpdateStatusService.class);
    final GetUnitMeasureScaleByWorkpack scale = mock(GetUnitMeasureScaleByWorkpack.class);
    when(scale.execute(anyLong())).thenReturn(2);

    this.service = new ScheduleService(
      this.stepRepository,
      this.scheduleRepository,
      mock(WorkpackService.class),
      new ModelMapper(),
      this.costAccountRepository,
      this.updateStatusService,
      mock(GetCostAccountGroupedByDistributedParts.class),
      scale,
      mock(BaselineRepository.class),
      new RedistributeScheduleSteps()
    );

    this.schedule = RedistributeScheduleStepsTest.baseSchedule(RedistributeScheduleStepsTest.costAccount(7L));
    when(this.scheduleRepository.findByIdSchedule(1L)).thenReturn(Optional.of(this.schedule));
    when(this.scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(this.updateStatusService.getDeliverablesByScheduleId(1L)).thenReturn(Collections.emptyList());
  }

  private static ScheduleUpdateParamDto param(final LocalDate end) {
    final ScheduleUpdateParamDto dto = new ScheduleUpdateParamDto();
    dto.setEnd(end);
    dto.setPlannedWork(new BigDecimal("120"));
    dto.setActualWork(new BigDecimal("44"));
    dto.setDistribution(DistributionStrategy.SIGMOIDAL);
    return dto;
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shorteningDeletesRemovedStepsWithoutBaselineSnapshot() {
    when(this.stepRepository.anyHasSnapshotInActiveOrProposedBaseline(anyList())).thenReturn(false);

    final Schedule saved = this.service.update(1L, param(LocalDate.of(2026, 8, 31)));

    assertSame(this.schedule, saved);
    final ArgumentCaptor<List<Step>> removed = ArgumentCaptor.forClass(List.class);
    verify(this.stepRepository).deleteAll(removed.capture());
    assertEquals(1, removed.getValue().size());
    assertEquals(Long.valueOf(104L), removed.getValue().get(0).getId());
    verify(this.stepRepository).anyHasSnapshotInActiveOrProposedBaseline(Collections.singletonList(104L));
    verify(this.scheduleRepository).save(this.schedule);
    verify(this.updateStatusService).update(Collections.emptyList());
    assertEquals(4, this.schedule.getSteps().size());
    assertEquals(DistributionStrategy.SIGMOIDAL, this.schedule.getDistribution());
  }

  @Test
  public void removingMonthWithBaselineSnapshotIsRefusedAndNothingIsPersisted() {
    when(this.stepRepository.anyHasSnapshotInActiveOrProposedBaseline(anyList())).thenReturn(true);

    try {
      this.service.update(1L, param(LocalDate.of(2026, 8, 31)));
      fail("esperava NegocioException");
    } catch (final NegocioException e) {
      assertEquals(ApplicationMessage.SCHEDULE_UPDATE_REMOVES_BASELINE_STEP, e.getMessage());
    }
    verify(this.stepRepository, never()).deleteAll(anyList());
    verify(this.scheduleRepository, never()).save(any(Schedule.class));
  }

  @Test
  public void extendingDoesNotCheckSnapshotsNorDeleteSteps() {
    this.service.update(1L, param(LocalDate.of(2026, 10, 31)));

    verify(this.stepRepository, never()).anyHasSnapshotInActiveOrProposedBaseline(anyList());
    verify(this.stepRepository, never()).deleteAll(anyList());
    verify(this.scheduleRepository).save(this.schedule);
    assertEquals(6, this.schedule.getSteps().size());
  }

  @Test
  public void costsFromBodyOverrideTotalsAndUnknownAccountIsLoaded() {
    final CostAccount another = RedistributeScheduleStepsTest.costAccount(8L);
    when(this.costAccountRepository.findById(8L)).thenReturn(Optional.of(another));
    final ScheduleUpdateParamDto dto = param(LocalDate.of(2026, 9, 30));
    final CostSchedule changed = new CostSchedule();
    changed.setId(7L);
    changed.setPlannedCost(new BigDecimal("2000"));
    final CostSchedule added = new CostSchedule();
    added.setId(8L);
    added.setPlannedCost(new BigDecimal("50"));
    added.setActualCost(BigDecimal.ZERO);
    dto.setCosts(new java.util.HashSet<>(java.util.Arrays.asList(changed, added)));

    this.service.update(1L, dto);

    assertEquals(0, new BigDecimal("2000").compareTo(RedistributeScheduleStepsTest.sumPlannedCost(this.schedule.getSteps(), 7L)));
    // actualCost nulo no corpo mantem o total atual
    assertEquals(0, new BigDecimal("300").compareTo(RedistributeScheduleStepsTest.sumActualCost(this.schedule.getSteps(), 7L)));
    assertEquals(0, new BigDecimal("50").compareTo(RedistributeScheduleStepsTest.sumPlannedCost(this.schedule.getSteps(), 8L)));
  }

  @Test
  public void unknownCostAccountIsRefused() {
    when(this.costAccountRepository.findById(9L)).thenReturn(Optional.empty());
    final ScheduleUpdateParamDto dto = param(LocalDate.of(2026, 9, 30));
    final CostSchedule unknown = new CostSchedule();
    unknown.setId(9L);
    dto.setCosts(Collections.singleton(unknown));

    try {
      this.service.update(1L, dto);
      fail("esperava NegocioException");
    } catch (final NegocioException e) {
      assertEquals(ApplicationMessage.COST_ACCOUNT_NOT_FOUND, e.getMessage());
    }
    verify(this.scheduleRepository, never()).save(any(Schedule.class));
  }

  @Test
  public void changingStartWithBaselineIsRefusedAndNothingIsPersisted() {
    when(this.stepRepository.anyHasSnapshotInActiveOrProposedBaseline(anyList())).thenReturn(true);
    final ScheduleUpdateParamDto dto = param(LocalDate.of(2026, 9, 30));
    dto.setStart(LocalDate.of(2026, 4, 1));

    try {
      this.service.update(1L, dto);
      fail("esperava NegocioException");
    } catch (final NegocioException e) {
      assertEquals(ApplicationMessage.SCHEDULE_UPDATE_START_HAS_BASELINE, e.getMessage());
    }
    assertEquals(LocalDate.of(2026, 5, 1), this.schedule.getStart());
    verify(this.scheduleRepository, never()).save(any(Schedule.class));
  }

  @Test
  public void changingStartWithoutBaselineIsSaved() {
    when(this.stepRepository.anyHasSnapshotInActiveOrProposedBaseline(anyList())).thenReturn(false);
    final ScheduleUpdateParamDto dto = param(LocalDate.of(2026, 9, 30));
    dto.setStart(LocalDate.of(2026, 4, 1));

    final Schedule saved = this.service.update(1L, dto);

    assertEquals(LocalDate.of(2026, 4, 1), saved.getStart());
    assertEquals(6, saved.getSteps().size());
  }

  @Test
  public void nullStartKeepsCurrentStart() {
    final Schedule saved = this.service.update(1L, param(LocalDate.of(2026, 9, 30)));

    assertEquals(LocalDate.of(2026, 5, 1), saved.getStart());
    verify(this.stepRepository, never()).anyHasSnapshotInActiveOrProposedBaseline(anyList());
  }

}
