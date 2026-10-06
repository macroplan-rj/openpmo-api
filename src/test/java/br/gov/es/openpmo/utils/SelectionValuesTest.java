package br.gov.es.openpmo.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/** SD #10628: opcoes da Selecao separadas por ';' (a virgula faz parte do texto). */
public class SelectionValuesTest {

  @Test
  public void separadorEhPontoEVirgula() {
    assertEquals(";", SelectionValues.SEPARATOR);
  }

  @Test
  public void virgulaInternaNaoQuebraAOpcao() {
    assertEquals(
      Collections.singletonList("Tornar as cidades sustentaveis, resilientes e inteligentes"),
      SelectionValues.split("Tornar as cidades sustentaveis, resilientes e inteligentes"));
  }

  @Test
  public void pontoEVirgulaSeparaApararEDescartaVazios() {
    assertEquals(Arrays.asList("A", "B, C", "D"), SelectionValues.split(" A ;B, C;; D ;"));
  }

  @Test
  public void valorNuloOuVazioViraListaVazia() {
    assertTrue(SelectionValues.split(null).isEmpty());
    assertTrue(SelectionValues.split("").isEmpty());
    assertTrue(SelectionValues.split(" ; ").isEmpty());
  }

  @Test
  public void parametroJasperComVirgulaInternaViraUmUnicoItem() {
    assertEquals("'Saude, educacao'", SelectionValues.toQuotedList("Saude, educacao"));
    assertEquals("'Saude, educacao','Seguranca'", SelectionValues.toQuotedList("Saude, educacao;Seguranca"));
  }

  @Test
  public void parametroJasperSemOpcaoEhNulo() {
    assertNull(SelectionValues.toQuotedList(null));
    assertNull(SelectionValues.toQuotedList(";"));
  }
}
