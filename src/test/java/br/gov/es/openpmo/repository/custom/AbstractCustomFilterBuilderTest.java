package br.gov.es.openpmo.repository.custom;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** SD #10628: valor da regra do filtro so e quebrado por ';' (separador da Selecao). */
public class AbstractCustomFilterBuilderTest {

  @Test
  public void virgulaInternaFicaNumUnicoValor() {
    assertEquals("Tornar as cidades sustentaveis, resilientes e inteligentes",
      AbstractCustomFilterBuilder.toParamValue("Tornar as cidades sustentaveis, resilientes e inteligentes"));
  }

  @Test
  public void pontoEVirgulaMontaALista() {
    assertEquals("['Saude, educacao','Seguranca']",
      AbstractCustomFilterBuilder.toParamValue("Saude, educacao;Seguranca"));
  }

  @Test
  public void numeroEBooleanoContinuamTipados() {
    assertEquals(42, AbstractCustomFilterBuilder.toParamValue("42"));
    assertEquals(Boolean.TRUE, AbstractCustomFilterBuilder.toParamValue("true"));
  }
}
