package br.gov.es.openpmo.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

/** SD-10604 — lista "Tipos de situacao" a partir do possibleValues do SelectionModel. */
public class SelectionPossibleValuesTest {

  @Test
  public void deveSepararPorVirgulaMantendoAOrdemDoModelo() {
    assertEquals(
      Arrays.asList("Cancelada", "Concluída", "Em execução", "Paralisada", "Em planejamento"),
      SelectionPossibleValues.split("Cancelada,Concluída, Em execução ,Paralisada,Em planejamento")
    );
  }

  @Test
  public void devePreferirPontoEVirgulaQuandoPresente() {
    assertEquals(
      Arrays.asList("Obra, fase 1", "Obra, fase 2"),
      SelectionPossibleValues.split("Obra, fase 1;Obra, fase 2")
    );
  }

  @Test
  public void deveIgnorarVaziosENulos() {
    assertTrue(SelectionPossibleValues.split(null).isEmpty());
    assertTrue(SelectionPossibleValues.split("  ").isEmpty());
    assertEquals(Arrays.asList("A", "B"), SelectionPossibleValues.split("A,,B,"));
  }
}
