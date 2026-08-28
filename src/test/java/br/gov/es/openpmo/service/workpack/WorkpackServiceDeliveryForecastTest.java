package br.gov.es.openpmo.service.workpack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.when;

import br.gov.es.openpmo.dto.workpack.DeliveryForecastDto;
import br.gov.es.openpmo.model.workpacks.Deliverable;
import br.gov.es.openpmo.model.workpacks.Milestone;
import br.gov.es.openpmo.model.workpacks.Project;
import br.gov.es.openpmo.model.workpacks.Workpack;
import br.gov.es.openpmo.repository.WorkpackRepository;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * US-003 — previsao de entrega nos cards e tiras da EAP.
 *
 * Cobre o roteamento por tipo e o contrato do mapa devolvido a
 * {@code GET /workpacks/parent}. O calculo em si mora no Cypher e foi conferido contra a
 * base do SIPGR; aqui o que importa e que Deliverable e Project caiam em consultas
 * distintas, que Milestone nao entre em nenhuma delas, e que a listagem gaste no maximo
 * duas consultas independente da quantidade de cards.
 */
public class WorkpackServiceDeliveryForecastTest {

  private WorkpackRepository workpackRepository;
  private WorkpackService service;

  private static Deliverable deliverable(final Long id) {
    final Deliverable deliverable = new Deliverable();
    deliverable.setId(id);
    return deliverable;
  }

  private static Project project(final Long id) {
    final Project project = new Project();
    project.setId(id);
    return project;
  }

  private static Milestone milestone(final Long id) {
    final Milestone milestone = new Milestone();
    milestone.setId(id);
    return milestone;
  }

  @Before
  public void setUp() {
    this.workpackRepository = mock(WorkpackRepository.class);
    this.service = mock(WorkpackService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
    ReflectionTestUtils.setField(this.service, "workpackRepository", this.workpackRepository);
  }

  @Test
  public void deveDevolverAPrevisaoDoDeliverable() {
    final List<Workpack> workpacks = Collections.singletonList(deliverable(1467L));
    when(this.workpackRepository.findDeliverableDeliveryForecast(Collections.singletonList(1467L)))
      .thenReturn(Collections.singletonList(new DeliveryForecastDto(1467L, "2026-08-14")));

    final Map<Long, LocalDate> forecasts = this.service.getDeliveryForecastMap(workpacks);

    assertEquals(LocalDate.of(2026, 8, 14), forecasts.get(1467L));
  }

  @Test
  public void deveDevolverAPrevisaoAgregadaDoProject() {
    final List<Workpack> workpacks = Collections.singletonList(project(95L));
    when(this.workpackRepository.findProjectDeliveryForecast(Collections.singletonList(95L)))
      .thenReturn(Collections.singletonList(new DeliveryForecastDto(95L, "2027-06-30")));

    final Map<Long, LocalDate> forecasts = this.service.getDeliveryForecastMap(workpacks);

    assertEquals(LocalDate.of(2027, 6, 30), forecasts.get(95L));
  }

  @Test
  public void deveUsarNoMaximoDuasConsultasParaAListagemInteira() {
    final List<Workpack> workpacks = Arrays.asList(
      deliverable(1L), deliverable(2L), deliverable(3L),
      project(10L), project(11L),
      milestone(20L)
    );
    when(this.workpackRepository.findDeliverableDeliveryForecast(Arrays.asList(1L, 2L, 3L)))
      .thenReturn(Collections.emptyList());
    when(this.workpackRepository.findProjectDeliveryForecast(Arrays.asList(10L, 11L)))
      .thenReturn(Collections.emptyList());

    this.service.getDeliveryForecastMap(workpacks);

    verify(this.workpackRepository).findDeliverableDeliveryForecast(Arrays.asList(1L, 2L, 3L));
    verify(this.workpackRepository).findProjectDeliveryForecast(Arrays.asList(10L, 11L));
  }

  @Test
  public void naoDeveConsultarQuandoNaoHaDeliverableNemProject() {
    final Map<Long, LocalDate> forecasts =
      this.service.getDeliveryForecastMap(Collections.singletonList(milestone(20L)));

    assertTrue(forecasts.isEmpty());
    verify(this.workpackRepository, never()).findDeliverableDeliveryForecast(Collections.<Long>emptyList());
    verify(this.workpackRepository, never()).findProjectDeliveryForecast(Collections.<Long>emptyList());
  }

  @Test
  public void deliverableSemPrevisaoNaoEntraNoMapa() {
    when(this.workpackRepository.findDeliverableDeliveryForecast(Collections.singletonList(1468L)))
      .thenReturn(Collections.singletonList(new DeliveryForecastDto(1468L, null)));

    final Map<Long, LocalDate> forecasts =
      this.service.getDeliveryForecastMap(Collections.singletonList(deliverable(1468L)));

    assertFalse(forecasts.containsKey(1468L));
    assertNull(forecasts.get(1468L));
  }

  @Test
  public void dataForaDoPadraoNaoDerrubaAListagem() {
    when(this.workpackRepository.findDeliverableDeliveryForecast(Arrays.asList(1L, 2L)))
      .thenReturn(Arrays.asList(
        new DeliveryForecastDto(1L, "14/08/2026"),
        new DeliveryForecastDto(2L, "2026-08-14")
      ));

    final Map<Long, LocalDate> forecasts =
      this.service.getDeliveryForecastMap(Arrays.asList(deliverable(1L), deliverable(2L)));

    assertFalse("data invalida deve ser ignorada", forecasts.containsKey(1L));
    assertEquals("os demais cards seguem com previsao", LocalDate.of(2026, 8, 14), forecasts.get(2L));
  }
}
