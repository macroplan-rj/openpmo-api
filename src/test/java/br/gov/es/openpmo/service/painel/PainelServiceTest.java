package br.gov.es.openpmo.service.painel;

import br.gov.es.openpmo.exception.RegistroNaoEncontradoException;
import br.gov.es.openpmo.repository.painel.PainelRepository;
import br.gov.es.openpmo.service.properties.dynamicselection.DynamicSelectionProvider;
import br.gov.es.openpmo.service.properties.dynamicselection.DynamicSelectionProviderRegistry;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Regras do Painel Estrategico (SD #10795-#10798) com fatos simulados da carteira. */
public class PainelServiceTest {

  private static final LocalDate POSICAO = LocalDate.of(2026, 11, 6);

  private PainelRepository repository;
  private PainelService service;

  private static Map<String, Object> m(final Object... kv) {
    final Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  private static Map<String, Object> prop(final String n, final String v) {
    return m("n", n, "v", v, "bruto", v);
  }

  private static Map<String, Object> projeto(final long id, final String nome, final long areaId, final String objetivos, final String instituicao) {
    final List<Map<String, Object>> props = new ArrayList<>();
    props.add(prop("situação", "Execução"));
    props.add(prop("objetivo(s) estratégico(s) associado(s)", objetivos));
    props.add(prop("unidade gestora", instituicao));
    return m("id", id, "nome", nome, "areaId", areaId, "area", "Área " + areaId, "props", props,
      "partes", List.of(m("nome", "Ana", "papel", "gerente", "organizacao", false)));
  }

  private static Map<String, Object> entrega(final long projeto, final long id, final String tipo, final String situacao,
                                             final String termino, final Long local) {
    final List<Map<String, Object>> props = new ArrayList<>();
    props.add(m("n", "tipo", "v", tipo));
    props.add(m("n", "situação", "v", situacao));
    if (local != null) {
      props.add(m("n", "local da entrega", "sel", "Cidade " + local, "selId", local, "selTipo", "CITY"));
    }
    return m("projetoId", projeto, "id", id, "nome", "Entrega " + id, "termino", termino, "props", props, "gerentes", List.of());
  }

  private static Map<String, Object> mes(final long entrega, final int anomes, final double estimado, final double planejado, final double realizado) {
    return m("entregaId", entrega, "anomes", anomes, "fisicoPlanejado", 1.0, "fisicoRealizado", realizado > 0 ? 1.0 : 0.0,
      "fisicoEstimado", 1.0, "consumos", List.of(m("uoCodigo", 21101, "uo", "SEPLAG", "estimado", estimado, "planejado", planejado, "realizado", realizado)));
  }

  private static Map<String, Object> marco(final long projeto, final long id, final String data, final boolean concluido, final Long entrega) {
    return m("projetoId", projeto, "id", id, "nome", "Marco " + id, "tipo", "Obra", "data", data, "dataLinhaBase", data,
      "concluido", concluido, "cancelado", false, "entregaId", entrega);
  }

  @Before
  public void setUp() {
    this.repository = mock(PainelRepository.class);
    when(this.repository.projetos()).thenReturn(List.of(
      projeto(1, "Alfa", 10, "OBJ01", "SEPLAG"),
      projeto(2, "Beta", 20, "OBJ02,OBJ03", "DOCAS"),
      projeto(3, "Gama", 10, "", "SEPLAG")));
    when(this.repository.entregas(anyCollection())).thenReturn(List.of(
      entrega(1, 11, "Obra", "Concluída", "2026-08-31", (Long) 101L),
      entrega(1, 12, "Serviço", "Em execução", "2026-10-31", 101L),
      entrega(2, 21, "Obra", "Planejamento", "2026-12-15", 102L),
      entrega(3, 31, "Norma", "Cancelada", "2026-09-30", null)));
    when(this.repository.custos(anyCollection())).thenReturn(List.of(
      mes(11, 202608, 100, 120, 130),
      mes(12, 202611, 50, 50, 10),
      mes(12, 202612, 50, 60, 0),
      mes(21, 202701, 200, 200, 0)));
    when(this.repository.marcos(anyCollection())).thenReturn(List.of(
      marco(1, 101, "2026-07-01", true, 11L),
      marco(1, 102, "2026-10-01", false, 12L),
      marco(2, 201, "2026-06-01", true, 21L),
      marco(2, 202, "2027-01-01", false, 21L),
      marco(3, 301, "2027-03-01", false, null)));
    when(this.repository.indicadores(anyLong())).thenReturn(List.of());
    when(this.repository.riscos(anyLong())).thenReturn(List.of());
    when(this.repository.ocorrencias(anyLong())).thenReturn(List.of());
    final DynamicSelectionProviderRegistry registry = mock(DynamicSelectionProviderRegistry.class);
    final DynamicSelectionProvider provider = mock(DynamicSelectionProvider.class);
    when(registry.get("pelp.objetivo")).thenReturn(provider);
    when(provider.resolve(anyList())).thenReturn(List.of(new DynamicSelectionProvider.Option("OBJ01", "Objetivo um", null)));
    this.service = new PainelService(this.repository, registry);
  }

  private static PainelFiltro filtro(final String horizonte) {
    return PainelFiltro.de(null, null, null, null, horizonte, POSICAO, null, null, null);
  }

  @SuppressWarnings("unchecked")
  private static <T> T get(final Map<String, Object> m, final String... caminho) {
    Object atual = m;
    for (final String c : caminho) {
      atual = ((Map<String, Object>) atual).get(c);
    }
    return (T) atual;
  }

  @Test
  public void carteiraContaEntregasEMarcosComAtrasos() {
    final Map<String, Object> r = this.service.carteira(filtro(null));
    assertEquals(3, (int) get(r, "projetosNaCarteira", "totalProjetos"));
    assertEquals(1L, (long) get(r, "entregas", "concluidas"));
    // 12 venceu em 31/10 e nao concluiu; 31 esta cancelada e nao conta como atrasada
    assertEquals(1L, (long) get(r, "entregas", "atrasadas"));
    assertEquals(1L, (long) get(r, "marcosCriticos", "atrasados"));
  }

  @Test
  public void horizonteAteHojeCortaEstimadoEReprogramadoMasNaoORealizado() {
    final Map<String, Object> ateHoje = this.service.carteira(filtro("ATE_HOJE"));
    assertEquals(new BigDecimal("150.00"), get(ateHoje, "financeiro", "estimado"));
    assertEquals(new BigDecimal("170.00"), get(ateHoje, "financeiro", "reprogramado"));
    assertEquals(new BigDecimal("140.00"), get(ateHoje, "financeiro", "realizado"));
    final Map<String, Object> total = this.service.carteira(filtro("TOTAL"));
    assertEquals(new BigDecimal("400.00"), get(total, "financeiro", "estimado"));
    assertEquals(new BigDecimal("430.00"), get(total, "financeiro", "reprogramado"));
    assertEquals(new BigDecimal("140.00"), get(total, "financeiro", "realizado"));
  }

  @Test
  public void filtrosSaoCumulativos() {
    final PainelFiltro f = PainelFiltro.de("10", "OBJ01", null, "seplag", null, POSICAO, null, null, null);
    final Map<String, Object> r = this.service.carteira(f);
    assertEquals(1, (int) get(r, "projetosNaCarteira", "totalProjetos"));
  }

  @Test
  public void indiceDeCumprimentoComFaixasEProjetoForaDoUniverso() {
    final Map<String, Object> r = this.service.executivoOndeAtuar(filtro(null));
    // Alfa: 1 de 2 vencidos (50%) critico; Beta: 1 de 1 (100%) no prazo; Gama: nenhum marco vencido
    assertEquals(1L, (long) get(r, "indiceCumprimento", "critico"));
    assertEquals(1L, (long) get(r, "indiceCumprimento", "noPrazo"));
    assertEquals(1L, (long) get(r, "indiceCumprimento", "foraDoUniverso"));
    final List<Map<String, Object>> projetos = get(r, "projetos");
    assertEquals(2, projetos.size());
    assertEquals("Alfa", projetos.get(0).get("nome"));
    assertEquals("CRITICO", projetos.get(0).get("faixa"));
  }

  @Test
  public void entregasConcluidasNoPeriodoEProximosDias() {
    final PainelFiltro periodo = PainelFiltro.de(null, null, null, null, null, POSICAO,
      LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "CONCLUIDAS");
    final Map<String, Object> r = this.service.executivoEntregas(periodo);
    assertEquals(1, (int) get(r, "concluidasNoPeriodo", "concluidas"));
    final PainelFiltro proximos = PainelFiltro.de(null, null, null, null, null, POSICAO, null, null, "PROXIMOS_DIAS");
    final List<Map<String, Object>> projetos = get(this.service.executivoEntregas(proximos), "projetos");
    assertEquals(1, projetos.size());
    assertEquals("Beta", projetos.get(0).get("nome"));
    assertEquals(LocalDate.of(2026, 12, 15), projetos.get(0).get("dataProximaEntrega"));
  }

  @Test
  public void visaoPorProjetoTrazIndiceIgualAoOndeAtuarEObjetivoResolvido() {
    final Map<String, Object> r = this.service.projeto(1L, filtro(null));
    assertEquals(new BigDecimal("50.00"), get(r, "indiceCumprimento", "indice"));
    assertEquals(1, (int) get(r, "posicaoNaCarteira", "indice"));
    final List<Map<String, Object>> objetivos = get(r, "alinhamento", "objetivos");
    assertEquals("Objetivo um", objetivos.get(0).get("nome"));
    final List<Map<String, Object>> entregas = get(r, "entregasEMarcos");
    assertTrue((Boolean) entregas.get(1).get("atrasada"));
  }

  @Test(expected = RegistroNaoEncontradoException.class)
  public void projetoForaDoEscopoE404() {
    this.service.projeto(999L, filtro(null));
  }

  @Test
  public void composicaoMantemListaOficialESomaValoresForaDela() {
    final Map<String, Object> r = this.service.carteira(filtro(null));
    final List<Map<String, Object>> porTipo = get(r, "composicaoEntregas", "porTipo");
    assertEquals("Obra", porTipo.get(0).get("tipo"));
    assertEquals(2L, porTipo.get(0).get("quantidade"));
    assertTrue(porTipo.stream().anyMatch(t -> "Norma".equals(t.get("tipo"))));
  }

}
