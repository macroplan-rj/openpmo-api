package br.gov.es.openpmo.utils;

import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.gov.es.openpmo.dto.dashboards.DashboardMonthDto;
import br.gov.es.openpmo.repository.WorkpackRepository;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * US-003 — de onde vem a previsao de entrega dos cards da EAP.
 *
 * O caminho obvio seria dashboard.tripleConstraint.scheduleForeseenEndDate, que
 * GET /workpacks/parent monta via WorkpackService.getDashboardMonthDto ->
 * DashboardCacheUtil.getDashboardMonthDto. O campo existe no contrato, mas o commit
 * b2791140 ("preparar pro cypher do vagner", nov/2025) removeu loadAllCache() e o
 * seu @PostConstruct. Desde entao nada popula mapWorkpackDetail: o mapa vazio produz
 * um DashboardDto sem workpacks, e o metodo devolve null para Deliverable e Project.
 *
 * Estes testes fixam esse comportamento. Se algum deles falhar, o cache voltou a ser
 * alimentado e scheduleForeseenEndDate passa a ser fonte valida — o que tornaria a
 * parte de API de US-003 (MT-US003-01/02) desnecessaria.
 */
public class DashboardCacheUtilTest {

  private ApplicationContext applicationContext;
  private WorkpackRepository workpackRepository;
  private DashboardCacheUtil cacheUtil;

  @Before
  public void setUp() {
    this.applicationContext = mock(ApplicationContext.class);
    this.workpackRepository = mock(WorkpackRepository.class);
    when(this.applicationContext.getBean(WorkpackRepository.class)).thenReturn(this.workpackRepository);

    this.cacheUtil = new DashboardCacheUtil();
    ReflectionTestUtils.setField(this.cacheUtil, "applicationContext", this.applicationContext);
  }

  @Test
  public void deliverableNaoTemDashboardPorqueOCacheNaoEPopulado() {
    final DashboardMonthDto dashboard = this.cacheUtil.getDashboardMonthDto(42L, true, 1L);

    assertNull(
      "cache voltou a ser populado — reavaliar MT-US003-01 antes de seguir",
      dashboard
    );
  }

  @Test
  public void projectComFilhosNaoTemDashboardPorqueOCacheNaoEPopulado() {
    final Set<Long> children = new HashSet<>();
    children.add(101L);
    children.add(102L);
    when(this.workpackRepository.findAllChildren(7L)).thenReturn(children);

    final DashboardMonthDto dashboard = this.cacheUtil.getDashboardMonthDto(7L, false, 1L);

    assertNull(
      "cache voltou a ser populado — reavaliar MT-US003-02 antes de seguir",
      dashboard
    );
  }

  @Test
  public void projectSemFilhosNaoTemDashboard() {
    when(this.workpackRepository.findAllChildren(7L)).thenReturn(Collections.emptySet());

    assertNull(this.cacheUtil.getDashboardMonthDto(7L, false, 1L));
  }
}
