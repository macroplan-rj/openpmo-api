package br.gov.es.openpmo.service.schedule;

import br.gov.es.openpmo.dto.schedule.DistributionStrategy;
import br.gov.es.openpmo.exception.NegocioException;
import br.gov.es.openpmo.model.relations.Consumes;
import br.gov.es.openpmo.model.schedule.Schedule;
import br.gov.es.openpmo.model.schedule.Step;
import br.gov.es.openpmo.model.workpacks.CostAccount;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static br.gov.es.openpmo.utils.ApplicationMessage.SCHEDULE_START_DATE_AFTER_DATE_ERROR;

/**
 * Recalcula os steps de um cronograma existente a partir de novo inicio, novo fim, novos totais e distribuicao (SD-10605).
 * <p>
 * Regras:
 * <ul>
 *   <li>Os meses vao do mes do inicio ao mes do novo fim. Se o inicio muda, cada step mantem seu mes no calendario
 *   (periodFromStart e deslocado); quem sabe se o inicio pode mudar e o chamador (nao pode com baseline).</li>
 *   <li>Steps existentes sao atualizados no lugar (mantem id e, portanto, o vinculo com snapshots de baseline);
 *   meses novos ganham steps novos; meses que saem do intervalo sao devolvidos em {@link Result#getRemoved()}.</li>
 *   <li>Cada serie (Un planejado, Un realizado, e por conta de custo $ planejado e $ realizado) so e redistribuida se
 *   algo que a afeta mudou: a distribuicao, o total, ou (para o planejado) o conjunto de meses. Caso contrario os
 *   valores mes a mes ajustados manualmente sao preservados.</li>
 *   <li>O planejado e distribuido em todos os meses; o realizado nos meses do inicio ate o mes corrente (limitado ao
 *   fim). Se o cronograma ainda nao comecou e ha realizado, ele vai para o primeiro mes, para nao perder o total.</li>
 *   <li>Snapshots de baseline nunca sao tocados.</li>
 * </ul>
 */
@Component
public class RedistributeScheduleSteps {

  private static final int COST_SCALE = 2;

  private static long monthsBetween(final LocalDate start, final LocalDate end) {
    return ChronoUnit.MONTHS.between(start.withDayOfMonth(1), end.withDayOfMonth(1));
  }

  static long actualMonths(final LocalDate start, final LocalDate end, final LocalDate today) {
    final long months = monthsBetween(start, end) + 1L;
    if (today.withDayOfMonth(1).isBefore(start.withDayOfMonth(1))) {
      return 0L;
    }
    return Math.min(months, monthsBetween(start, today) + 1L);
  }

  private static Map<Long, BigDecimal> getParts(
    final long months,
    final BigDecimal value,
    final int scale,
    final DistributionStrategy distribution
  ) {
    if (months <= 0) {
      return Collections.emptyMap();
    }
    switch (distribution) {
      case LINEAR:
        return new StepValueLinearAllocator().execute(months, value, scale);
      case SIGMOIDAL:
        return new StepValueSigmoidalAllocator().execute(months, value, scale);
      default:
        throw new NegocioException();
    }
  }

  private static BigDecimal nonNull(final BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private static BigDecimal sum(final Collection<Step> steps, final Function<Step, BigDecimal> getter) {
    return steps.stream().map(getter).map(RedistributeScheduleSteps::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static boolean sameValue(final BigDecimal a, final BigDecimal b) {
    return nonNull(a).compareTo(nonNull(b)) == 0;
  }

  private static Optional<Consumes> findConsumes(final Step step, final Long idCostAccount) {
    return step.getConsumes().stream()
      .filter(consumes -> Objects.equals(consumes.getIdCostAccount(), idCostAccount))
      .findFirst();
  }

  private static BigDecimal sumCost(
    final Collection<Step> steps,
    final Long idCostAccount,
    final Function<Consumes, BigDecimal> getter
  ) {
    return steps.stream()
      .map(step -> findConsumes(step, idCostAccount).map(getter).orElse(BigDecimal.ZERO))
      .map(RedistributeScheduleSteps::nonNull)
      .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /**
   * Totais atuais por conta de custo consumida pelos steps do cronograma.
   */
  public static Map<Long, CostTarget> currentCostTotals(final Schedule schedule) {
    final Map<Long, CostTarget> totals = new HashMap<>();
    for (final Step step : Optional.ofNullable(schedule.getSteps()).orElse(Collections.emptySet())) {
      for (final Consumes consumes : step.getConsumes()) {
        if (consumes.getCostAccount() == null) {
          continue;
        }
        final CostTarget target = totals.computeIfAbsent(
          consumes.getIdCostAccount(),
          id -> new CostTarget(consumes.getCostAccount(), BigDecimal.ZERO, BigDecimal.ZERO)
        );
        target.plannedCost = target.plannedCost.add(nonNull(consumes.getPlannedCost()));
        target.actualCost = target.actualCost.add(nonNull(consumes.getActualCost()));
      }
    }
    return totals;
  }

  public Result execute(
    final Schedule schedule,
    final LocalDate newEnd,
    final BigDecimal plannedWork,
    final BigDecimal actualWork,
    final DistributionStrategy distribution,
    final Collection<CostTarget> costTargets,
    final int scale,
    final LocalDate today
  ) {
    return this.execute(schedule, schedule.getStart(), newEnd, plannedWork, actualWork, distribution, costTargets, scale, today);
  }

  public Result execute(
    final Schedule schedule,
    final LocalDate start,
    final LocalDate newEnd,
    final BigDecimal plannedWork,
    final BigDecimal actualWork,
    final DistributionStrategy distribution,
    final Collection<CostTarget> costTargets,
    final int scale,
    final LocalDate today
  ) {
    if (newEnd.isBefore(start)) {
      throw new NegocioException(SCHEDULE_START_DATE_AFTER_DATE_ERROR);
    }

    final long months = monthsBetween(start, newEnd) + 1L;
    final long actualMonths = actualMonths(start, newEnd, today);

    final List<Step> existing = new ArrayList<>(Optional.ofNullable(schedule.getSteps()).orElse(Collections.emptySet()));
    existing.sort(Comparator.comparing(Step::getPeriodFromStart));

    // Inicio mudou de mes: o periodo e contado a partir do inicio, entao desloca para cada step ficar no mesmo mes.
    final long shift = monthsBetween(start, schedule.getStart());
    if (shift != 0L) {
      existing.stream()
        .filter(step -> step.getPeriodFromStart() != null)
        .forEach(step -> step.setPeriodFromStart(step.getPeriodFromStart() + shift));
    }

    final Map<Long, Step> byPeriod = new HashMap<>();
    final List<Step> removed = new ArrayList<>();
    for (final Step step : existing) {
      final Long period = step.getPeriodFromStart();
      if (period == null || period < 0 || period >= months || byPeriod.containsKey(period)) {
        removed.add(step);
      } else {
        byPeriod.put(period, step);
      }
    }

    final List<Step> created = new ArrayList<>();
    final List<Step> steps = new ArrayList<>();
    for (long period = 0; period < months; period++) {
      Step step = byPeriod.get(period);
      if (step == null) {
        step = new Step();
        step.setPeriodFromStart(period);
        step.setPlannedWork(BigDecimal.ZERO);
        step.setActualWork(BigDecimal.ZERO);
        step.setSchedule(schedule);
        step.setConsumes(new HashSet<>());
        created.add(step);
      }
      steps.add(step);
    }

    final boolean distributionChanged = schedule.getDistributionOrDefault() != distribution;
    final boolean monthsChanged = !removed.isEmpty() || !created.isEmpty();

    // Un planejado
    final BigDecimal plannedTarget = plannedWork == null ? sum(existing, Step::getPlannedWork) : plannedWork;
    if (distributionChanged || monthsChanged || !sameValue(sum(existing, Step::getPlannedWork), plannedTarget)) {
      final Map<Long, BigDecimal> parts = getParts(months, plannedTarget, scale, distribution);
      for (final Step step : steps) {
        step.setPlannedWork(parts.getOrDefault(step.getPeriodFromStart() + 1, BigDecimal.ZERO));
      }
    }

    // Un realizado
    final BigDecimal actualTarget = actualWork == null ? sum(existing, Step::getActualWork) : actualWork;
    if (distributionChanged || !sameValue(sum(steps, Step::getActualWork), actualTarget)) {
      final long monthsWithActual = actualTarget.signum() > 0 ? Math.max(actualMonths, 1L) : actualMonths;
      final Map<Long, BigDecimal> parts = getParts(monthsWithActual, actualTarget, scale, distribution);
      for (final Step step : steps) {
        step.setActualWork(parts.getOrDefault(step.getPeriodFromStart() + 1, BigDecimal.ZERO));
      }
    }

    // $ por conta de custo
    for (final CostTarget target : costTargets) {
      final Long idCostAccount = target.getCostAccount().getId();
      for (final Step step : steps) {
        if (!findConsumes(step, idCostAccount).isPresent()) {
          step.getConsumes().add(new Consumes(null, BigDecimal.ZERO, BigDecimal.ZERO, target.getCostAccount(), step));
        }
      }

      final BigDecimal plannedCost = target.getPlannedCost() == null
        ? sumCost(existing, idCostAccount, Consumes::getPlannedCost)
        : target.getPlannedCost();
      if (distributionChanged || monthsChanged
          || !sameValue(sumCost(existing, idCostAccount, Consumes::getPlannedCost), plannedCost)) {
        final Map<Long, BigDecimal> parts = getParts(months, plannedCost, COST_SCALE, distribution);
        for (final Step step : steps) {
          findConsumes(step, idCostAccount)
            .ifPresent(c -> c.setPlannedCost(parts.getOrDefault(step.getPeriodFromStart() + 1, BigDecimal.ZERO)));
        }
      }

      final BigDecimal actualCost = target.getActualCost() == null
        ? sumCost(existing, idCostAccount, Consumes::getActualCost)
        : target.getActualCost();
      if (distributionChanged || !sameValue(sumCost(steps, idCostAccount, Consumes::getActualCost), actualCost)) {
        final long monthsWithActual = actualCost.signum() > 0 ? Math.max(actualMonths, 1L) : actualMonths;
        final Map<Long, BigDecimal> parts = getParts(monthsWithActual, actualCost, COST_SCALE, distribution);
        for (final Step step : steps) {
          findConsumes(step, idCostAccount)
            .ifPresent(c -> c.setActualCost(parts.getOrDefault(step.getPeriodFromStart() + 1, BigDecimal.ZERO)));
        }
      }
    }

    // Step e Consumes usam valores mutaveis no hashCode: reconstroi os conjuntos depois das alteracoes.
    for (final Step step : steps) {
      step.setConsumes(new HashSet<>(step.getConsumes()));
    }
    schedule.setStart(start);
    schedule.setEnd(newEnd);
    schedule.setDistribution(distribution);
    schedule.setSteps(new HashSet<>(steps));

    return new Result(steps, created, removed);
  }

  public static final class CostTarget {

    private final CostAccount costAccount;

    private BigDecimal plannedCost;

    private BigDecimal actualCost;

    public CostTarget(final CostAccount costAccount, final BigDecimal plannedCost, final BigDecimal actualCost) {
      this.costAccount = costAccount;
      this.plannedCost = plannedCost;
      this.actualCost = actualCost;
    }

    public CostAccount getCostAccount() {
      return this.costAccount;
    }

    public BigDecimal getPlannedCost() {
      return this.plannedCost;
    }

    public void setPlannedCost(final BigDecimal plannedCost) {
      this.plannedCost = plannedCost;
    }

    public BigDecimal getActualCost() {
      return this.actualCost;
    }

    public void setActualCost(final BigDecimal actualCost) {
      this.actualCost = actualCost;
    }

  }

  public static final class Result {

    private final List<Step> steps;

    private final List<Step> created;

    private final List<Step> removed;

    public Result(final List<Step> steps, final List<Step> created, final List<Step> removed) {
      this.steps = steps;
      this.created = created;
      this.removed = removed;
    }

    /**
     * Steps do cronograma depois da edicao, ordenados por mes (existentes atualizados + criados).
     */
    public List<Step> getSteps() {
      return this.steps;
    }

    public List<Step> getCreated() {
      return this.created;
    }

    public List<Step> getRemoved() {
      return this.removed;
    }

  }

}
