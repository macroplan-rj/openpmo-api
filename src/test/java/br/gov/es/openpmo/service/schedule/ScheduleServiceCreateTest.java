package br.gov.es.openpmo.service.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.gov.es.openpmo.dto.schedule.CostSchedule;
import br.gov.es.openpmo.dto.schedule.DistributionStrategy;
import br.gov.es.openpmo.dto.schedule.ScheduleParamDto;
import br.gov.es.openpmo.model.relations.Consumes;
import br.gov.es.openpmo.model.schedule.Schedule;
import br.gov.es.openpmo.model.schedule.Step;
import br.gov.es.openpmo.repository.BaselineRepository;
import br.gov.es.openpmo.repository.CostAccountRepository;
import br.gov.es.openpmo.repository.ScheduleRepository;
import br.gov.es.openpmo.repository.StepRepository;
import br.gov.es.openpmo.service.workpack.CostAccountService;
import br.gov.es.openpmo.service.workpack.WorkpackService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.modelmapper.ModelMapper;

/**
 * SD-10605: na criacao, o realizado era dividido em N meses mas so N-1 eram gravados
 * (currentMonth < actualWorkMonths), perdendo a parte do mes corrente.
 */
public class ScheduleServiceCreateTest {

  private ScheduleRepository scheduleRepository;
  private ScheduleService service;

  @Before
  public void setUp() {
    this.scheduleRepository = mock(ScheduleRepository.class);
    final UpdateStatusService updateStatusService = mock(UpdateStatusService.class);
    final GetUnitMeasureScaleByWorkpack scale = mock(GetUnitMeasureScaleByWorkpack.class);
    when(scale.execute(anyLong())).thenReturn(2);
    final CostAccountService costAccountService = mock(CostAccountService.class);
    when(costAccountService.findById(7L)).thenReturn(RedistributeScheduleStepsTest.costAccount(7L));
    when(this.scheduleRepository.findAllByWorkpack(anyLong())).thenReturn(Collections.emptyList());
    when(this.scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(updateStatusService.getDeliverablesByScheduleId(any())).thenReturn(Collections.emptyList());

    this.service = new ScheduleService(
      mock(StepRepository.class),
      this.scheduleRepository,
      mock(WorkpackService.class),
      new ModelMapper(),
      mock(CostAccountRepository.class),
      updateStatusService,
      new GetCostAccountGroupedByDistributedParts(costAccountService),
      scale,
      mock(BaselineRepository.class),
      new RedistributeScheduleSteps()
    );
  }

  private Schedule create(final DistributionStrategy distribution) {
    final LocalDate start = LocalDate.now().withDayOfMonth(1).minusMonths(2);
    final ScheduleParamDto dto = new ScheduleParamDto();
    dto.setIdWorkpack(1L);
    dto.setStart(start);
    dto.setEnd(start.plusMonths(5));
    dto.setPlannedWork(new BigDecimal("120"));
    dto.setActualWork(new BigDecimal("30"));
    dto.setDistribution(distribution);
    final CostSchedule cost = new CostSchedule();
    cost.setId(7L);
    cost.setPlannedCost(new BigDecimal("600"));
    cost.setActualCost(new BigDecimal("90"));
    dto.setCosts(Collections.singleton(cost));

    this.service.save(dto);
    final ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
    verify(this.scheduleRepository).save(captor.capture());
    return captor.getValue();
  }

  private static void assertActualTotals(final Schedule schedule) {
    BigDecimal actualWork = BigDecimal.ZERO;
    BigDecimal actualCost = BigDecimal.ZERO;
    BigDecimal currentMonthWork = BigDecimal.ZERO;
    for (final Step step : schedule.getSteps()) {
      actualWork = actualWork.add(step.getActualWork());
      if (step.getPeriodFromStart() == 2) {
        currentMonthWork = step.getActualWork();
      }
      for (final Consumes consumes : step.getConsumes()) {
        actualCost = actualCost.add(consumes.getActualCost());
      }
    }
    assertEquals(0, new BigDecimal("30").compareTo(actualWork));
    assertEquals(0, new BigDecimal("90").compareTo(actualCost));
    assertTrue("mes corrente recebe sua parte do realizado", currentMonthWork.signum() > 0);
  }

  @Test
  public void criacaoLinearGravaTodoORealizadoInclusiveMesCorrente() {
    assertActualTotals(this.create(DistributionStrategy.LINEAR));
  }

  @Test
  public void criacaoSigmoidalGravaTodoORealizadoInclusiveMesCorrente() {
    assertActualTotals(this.create(DistributionStrategy.SIGMOIDAL));
  }
}
