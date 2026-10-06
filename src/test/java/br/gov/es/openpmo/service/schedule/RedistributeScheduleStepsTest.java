package br.gov.es.openpmo.service.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import br.gov.es.openpmo.dto.schedule.DistributionStrategy;
import br.gov.es.openpmo.exception.NegocioException;
import br.gov.es.openpmo.model.relations.Consumes;
import br.gov.es.openpmo.model.schedule.Schedule;
import br.gov.es.openpmo.model.schedule.Step;
import br.gov.es.openpmo.model.workpacks.CostAccount;
import br.gov.es.openpmo.model.workpacks.Deliverable;
import br.gov.es.openpmo.utils.ApplicationMessage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

/**
 * SD-10605: redistribuicao dos steps na edicao do cronograma.
 * Cenario base (como na referencia do ticket): maio a setembro de 2026, 120 Un planejadas, 44 realizadas em maio/junho,
 * hoje = 15/07/2026, uma conta de custo com $ 1000 planejado e $ 300 realizado.
 */
public class RedistributeScheduleStepsTest {

  static final LocalDate TODAY = LocalDate.of(2026, 7, 15);
  static final int SCALE = 2;

  private RedistributeScheduleSteps redistribute;
  private Schedule schedule;
  private CostAccount costAccount;

  static BigDecimal bd(final String value) {
    return new BigDecimal(value);
  }

  static BigDecimal sumPlanned(final Collection<Step> steps) {
    return steps.stream().map(Step::getPlannedWork).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  static BigDecimal sumActual(final Collection<Step> steps) {
    return steps.stream().map(Step::getActualWork).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  static BigDecimal sumPlannedCost(final Collection<Step> steps, final Long idCostAccount) {
    return steps.stream().flatMap(s -> s.getConsumes().stream())
      .filter(c -> idCostAccount.equals(c.getIdCostAccount()))
      .map(Consumes::getPlannedCost).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  static BigDecimal sumActualCost(final Collection<Step> steps, final Long idCostAccount) {
    return steps.stream().flatMap(s -> s.getConsumes().stream())
      .filter(c -> idCostAccount.equals(c.getIdCostAccount()))
      .map(Consumes::getActualCost).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  static void assertSame(final String expected, final BigDecimal actual) {
    assertEquals("esperado " + expected + " mas foi " + actual, 0, bd(expected).compareTo(actual));
  }

  static CostAccount costAccount(final long id) {
    final CostAccount costAccount = new CostAccount();
    costAccount.setId(id);
    return costAccount;
  }

  static Schedule baseSchedule(final CostAccount costAccount) {
    final Deliverable deliverable = new Deliverable();
    deliverable.setId(10L);
    final Schedule schedule = new Schedule();
    schedule.setId(1L);
    schedule.setWorkpack(deliverable);
    schedule.setStart(LocalDate.of(2026, 5, 1));
    schedule.setEnd(LocalDate.of(2026, 9, 30));
    final String[] planned = {"20", "24", "23.89", "23.78", "28.33"};
    final String[] actual = {"20", "24", "0", "0", "0"};
    final String[] plannedCost = {"200", "200", "200", "200", "200"};
    final String[] actualCost = {"100", "200", "0", "0", "0"};
    final Collection<Step> steps = new ArrayList<>();
    for (int i = 0; i < planned.length; i++) {
      final Step step = new Step();
      step.setId(100L + i);
      step.setPeriodFromStart((long) i);
      step.setPlannedWork(bd(planned[i]));
      step.setActualWork(bd(actual[i]));
      step.setSchedule(schedule);
      step.getConsumes().add(new Consumes(1000L + i, bd(actualCost[i]), bd(plannedCost[i]), costAccount, step));
      steps.add(step);
    }
    schedule.setSteps(new HashSet<>(steps));
    return schedule;
  }

  @Before
  public void setUp() {
    this.redistribute = new RedistributeScheduleSteps();
    this.costAccount = costAccount(7L);
    this.schedule = baseSchedule(this.costAccount);
  }

  private RedistributeScheduleSteps.Result run(
    final LocalDate end,
    final String planned,
    final String actual,
    final DistributionStrategy distribution
  ) {
    final Map<Long, RedistributeScheduleSteps.CostTarget> costs = RedistributeScheduleSteps.currentCostTotals(this.schedule);
    return this.run(end, planned, actual, distribution, costs.values());
  }

  private RedistributeScheduleSteps.Result run(
    final LocalDate end,
    final String planned,
    final String actual,
    final DistributionStrategy distribution,
    final Collection<RedistributeScheduleSteps.CostTarget> costs
  ) {
    return this.redistribute.execute(
      this.schedule,
      end,
      planned == null ? null : bd(planned),
      actual == null ? null : bd(actual),
      distribution,
      costs,
      SCALE,
      TODAY
    );
  }

  private static List<String> values(final List<Step> steps, final boolean planned) {
    return steps.stream()
      .map(s -> (planned ? s.getPlannedWork() : s.getActualWork()).stripTrailingZeros().toPlainString())
      .collect(Collectors.toList());
  }

  @Test
  public void currentCostTotalsSumsConsumesByCostAccount() {
    final RedistributeScheduleSteps.CostTarget target = RedistributeScheduleSteps.currentCostTotals(this.schedule).get(7L);
    assertSame("1000", target.getPlannedCost());
    assertSame("300", target.getActualCost());
  }

  @Test
  public void nothingChangedPreservesManualMonthlyValues() {
    final RedistributeScheduleSteps.Result result = this.run(LocalDate.of(2026, 9, 30), "120", "44", DistributionStrategy.SIGMOIDAL);

    assertEquals(Arrays.asList("20", "24", "23.89", "23.78", "28.33"), values(result.getSteps(), true));
    assertEquals(Arrays.asList("20", "24", "0", "0", "0"), values(result.getSteps(), false));
    assertTrue(result.getCreated().isEmpty());
    assertTrue(result.getRemoved().isEmpty());
    assertSame("1000", sumPlannedCost(result.getSteps(), 7L));
    assertSame("300", sumActualCost(result.getSteps(), 7L));
    assertSame("100", result.getSteps().get(0).getConsumes().iterator().next().getActualCost());
  }

  @Test
  public void legacyScheduleWithoutDistributionIsTreatedAsSigmoidal() {
    assertEquals(null, this.schedule.getDistribution());
    final RedistributeScheduleSteps.Result result = this.run(LocalDate.of(2026, 9, 30), "120", "44", DistributionStrategy.SIGMOIDAL);

    assertEquals(Arrays.asList("20", "24", "23.89", "23.78", "28.33"), values(result.getSteps(), true));
    assertEquals(DistributionStrategy.SIGMOIDAL, this.schedule.getDistribution());
  }

  @Test
  public void shorteningRemovesTrailingMonthAndRedistributesPlannedKeepingTotals() {
    final RedistributeScheduleSteps.Result result = this.run(LocalDate.of(2026, 8, 31), "120", "44", DistributionStrategy.SIGMOIDAL);

    assertEquals(4, result.getSteps().size());
    assertEquals(1, result.getRemoved().size());
    assertEquals(Long.valueOf(104L), result.getRemoved().get(0).getId());
    assertEquals(Arrays.asList(100L, 101L, 102L, 103L), result.getSteps().stream().map(Step::getId).collect(Collectors.toList()));
    assertSame("120", sumPlanned(result.getSteps()));
    assertSame("44", sumActual(result.getSteps()));
    // realizado nao mudou: preserva o lancado mes a mes
    assertEquals(Arrays.asList("20", "24", "0", "0"), values(result.getSteps(), false));
    assertSame("1000", sumPlannedCost(result.getSteps(), 7L));
    assertSame("300", sumActualCost(result.getSteps(), 7L));
    assertEquals(LocalDate.of(2026, 8, 31), this.schedule.getEnd());
    assertEquals(4, this.schedule.getSteps().size());
  }

  @Test
  public void extendingCreatesStepsForNewMonthsWithConsumes() {
    final RedistributeScheduleSteps.Result result = this.run(LocalDate.of(2026, 11, 30), "120", "44", DistributionStrategy.LINEAR);

    assertEquals(7, result.getSteps().size());
    assertEquals(2, result.getCreated().size());
    assertTrue(result.getRemoved().isEmpty());
    assertEquals(Arrays.asList(5L, 6L), result.getCreated().stream().map(Step::getPeriodFromStart).collect(Collectors.toList()));
    for (final Step created : result.getCreated()) {
      assertEquals(this.schedule, created.getSchedule());
      assertEquals(1, created.getConsumes().size());
      assertEquals(Long.valueOf(7L), created.getConsumes().iterator().next().getIdCostAccount());
    }
    assertSame("120", sumPlanned(result.getSteps()));
    assertSame("44", sumActual(result.getSteps()));
    assertSame("1000", sumPlannedCost(result.getSteps(), 7L));
    assertSame("300", sumActualCost(result.getSteps(), 7L));
    assertEquals(7, this.schedule.getSteps().size());
  }

  @Test
  public void changingDistributionRedistributesEverything() {
    final RedistributeScheduleSteps.Result result = this.run(LocalDate.of(2026, 9, 30), "120", "44", DistributionStrategy.LINEAR);

    assertEquals(Arrays.asList("24", "24", "24", "24", "24"), values(result.getSteps(), true));
    // realizado de maio ate julho (mes corrente)
    assertEquals(Arrays.asList("14.67", "14.67", "14.66", "0", "0"), values(result.getSteps(), false));
    assertSame("44", sumActual(result.getSteps()));
    assertSame("1000", sumPlannedCost(result.getSteps(), 7L));
    assertSame("300", sumActualCost(result.getSteps(), 7L));
    assertSame("200", result.getSteps().get(4).getConsumes().iterator().next().getPlannedCost());
    assertSame("0", result.getSteps().get(4).getConsumes().iterator().next().getActualCost());
    assertEquals(DistributionStrategy.LINEAR, this.schedule.getDistribution());
  }

  @Test
  public void changingTotalsRedistributesAndSumsMatch() {
    final Map<Long, RedistributeScheduleSteps.CostTarget> costs = RedistributeScheduleSteps.currentCostTotals(this.schedule);
    costs.get(7L).setPlannedCost(bd("1500.50"));
    costs.get(7L).setActualCost(bd("410"));

    final RedistributeScheduleSteps.Result result = this.run(
      LocalDate.of(2026, 9, 30), "150", "50", DistributionStrategy.SIGMOIDAL, costs.values());

    assertSame("150", sumPlanned(result.getSteps()));
    assertSame("50", sumActual(result.getSteps()));
    assertSame("1500.50", sumPlannedCost(result.getSteps(), 7L));
    assertSame("410", sumActualCost(result.getSteps(), 7L));
    // realizado nunca cai em mes futuro
    assertSame("0", result.getSteps().get(3).getActualWork());
    assertSame("0", result.getSteps().get(4).getActualWork());
  }

  @Test
  public void onlyActualChangedKeepsPlannedMonthlyValues() {
    final RedistributeScheduleSteps.Result result = this.run(LocalDate.of(2026, 9, 30), "120", "60", DistributionStrategy.SIGMOIDAL);

    assertEquals(Arrays.asList("20", "24", "23.89", "23.78", "28.33"), values(result.getSteps(), true));
    assertSame("60", sumActual(result.getSteps()));
  }

  @Test
  public void newCostAccountIsDistributedOverAllMonths() {
    final Map<Long, RedistributeScheduleSteps.CostTarget> costs = RedistributeScheduleSteps.currentCostTotals(this.schedule);
    final CostAccount another = costAccount(8L);
    costs.put(8L, new RedistributeScheduleSteps.CostTarget(another, bd("500"), bd("0")));

    final RedistributeScheduleSteps.Result result = this.run(
      LocalDate.of(2026, 9, 30), "120", "44", DistributionStrategy.SIGMOIDAL, costs.values());

    for (final Step step : result.getSteps()) {
      assertEquals(2, step.getConsumes().size());
    }
    assertSame("500", sumPlannedCost(result.getSteps(), 8L));
    assertSame("1000", sumPlannedCost(result.getSteps(), 7L));
    assertSame("100", result.getSteps().get(0).getConsumes().stream()
      .filter(c -> c.getIdCostAccount().equals(7L)).findFirst().get().getActualCost());
  }

  @Test
  public void shorteningBeforeMonthsWithActualMovesActualIntoRemainingMonths() {
    final RedistributeScheduleSteps.Result result = this.run(LocalDate.of(2026, 5, 31), "120", "44", DistributionStrategy.SIGMOIDAL);

    assertEquals(1, result.getSteps().size());
    assertEquals(4, result.getRemoved().size());
    assertSame("120", sumPlanned(result.getSteps()));
    assertSame("44", sumActual(result.getSteps()));
    assertSame("1000", sumPlannedCost(result.getSteps(), 7L));
    assertSame("300", sumActualCost(result.getSteps(), 7L));
  }

  @Test
  public void actualBeforeScheduleStartGoesToFirstMonth() {
    this.schedule.setStart(LocalDate.of(2026, 8, 1));
    final RedistributeScheduleSteps.Result result = this.run(LocalDate.of(2026, 12, 31), "120", "10", DistributionStrategy.LINEAR,
      Collections.emptyList());

    assertSame("10", result.getSteps().get(0).getActualWork());
    assertSame("10", sumActual(result.getSteps()));
  }

  @Test
  public void endBeforeStartIsRefused() {
    try {
      this.run(LocalDate.of(2026, 4, 30), "120", "44", DistributionStrategy.SIGMOIDAL);
      fail("esperava NegocioException");
    } catch (final NegocioException e) {
      assertEquals(ApplicationMessage.SCHEDULE_START_DATE_AFTER_DATE_ERROR, e.getMessage());
    }
    assertEquals(LocalDate.of(2026, 9, 30), this.schedule.getEnd());
  }

  @Test
  public void actualMonthsIsLimitedByTodayAndEnd() {
    final LocalDate start = LocalDate.of(2026, 5, 1);
    assertEquals(3L, RedistributeScheduleSteps.actualMonths(start, LocalDate.of(2026, 9, 30), TODAY));
    assertEquals(2L, RedistributeScheduleSteps.actualMonths(start, LocalDate.of(2026, 6, 30), TODAY));
    assertEquals(0L, RedistributeScheduleSteps.actualMonths(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30), TODAY));
  }

}
