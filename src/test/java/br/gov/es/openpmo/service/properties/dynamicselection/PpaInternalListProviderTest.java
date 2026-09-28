package br.gov.es.openpmo.service.properties.dynamicselection;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/** SPIKE US-020 — o provedor do PPA filtra pelo programa sem nenhuma regra fora dele. */
public class PpaInternalListProviderTest {

  private static final List<String> PROGRAMAS = Arrays.asList(
    "Programa 5001 - Gestao Dinamica e Eficiente",
    "Programa 5002 - Economia Sustentavel e Competitiva");

  private static final List<String> INDICADORES = Arrays.asList(
    "Indicador 5001 - Implementacao de ferramentas de gestao de TIC",
    "Indicador 5002 - Empresas parceiras",
    "Indicador 5002 - Arranjos produtivos apoiados");

  private static final List<String> ACOES = Arrays.asList(
    "Acao 1074 - 5002 - Estruturacao e Organizacao dos Arranjos Produtivos Locais",
    "Acao 1496 - 5001 - Ampliacao e Manutencao da Radio e TV Tabajara",
    "Acao 1640 - 5001 - Educacao Fiscal");

  @Test
  public void programasNaoDependemDeNinguem() {
    final PpaInternalListProvider p = new PpaInternalListProvider(PpaInternalListProvider.Nivel.PROGRAMA, () -> PROGRAMAS);
    assertEquals("ppa.programa", p.key());
    assertFalse(p.dependsOnKey().isPresent());
    assertEquals(2, p.options(null).size());
    assertEquals("5001", p.options(null).get(0).getCode());
  }

  @Test
  public void indicadoresFiltramPeloPrograma() {
    final PpaInternalListProvider p = new PpaInternalListProvider(PpaInternalListProvider.Nivel.INDICADOR, () -> INDICADORES);
    assertEquals("ppa.programa", p.dependsOnKey().get());
    assertEquals(3, p.options(null).size());
    assertEquals(2, p.options("5002").size());
    assertEquals(1, p.options("5001").size());
    assertEquals(0, p.options("9999").size());
  }

  @Test
  public void acoesFiltramPeloProgramaEGuardamOProprioCodigo() {
    final PpaInternalListProvider p = new PpaInternalListProvider(PpaInternalListProvider.Nivel.ACAO, () -> ACOES);
    final List<DynamicSelectionProvider.Option> do5001 = p.options("5001");
    assertEquals(2, do5001.size());
    assertEquals("1496", do5001.get(0).getCode());
    assertEquals("5001", do5001.get(0).getParentCode());
    assertEquals("Acao 1640 - 5001 - Educacao Fiscal", p.resolve("1640").get().getLabel());
    assertFalse(p.resolve("0000").isPresent());
  }

  @Test
  public void itemForaDoFormatoEIgnoradoSemQuebrar() {
    final PpaInternalListProvider p = new PpaInternalListProvider(PpaInternalListProvider.Nivel.PROGRAMA, () -> Arrays.asList("qualquer coisa", "Programa 7 - X"));
    assertEquals(1, p.options(null).size());
    assertEquals("7", p.options(null).get(0).getCode());
  }
}
