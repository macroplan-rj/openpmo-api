package br.gov.es.openpmo.service.properties.dynamicselection;

import br.gov.es.openpmo.exception.NegocioException;
import br.gov.es.openpmo.model.properties.dynamicselection.DynamicSelectionList;
import br.gov.es.openpmo.model.properties.dynamicselection.DynamicSelectionSource;
import br.gov.es.openpmo.repository.DynamicSelectionListRepository;
import br.gov.es.openpmo.repository.DynamicSelectionSourceRepository;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** US-021 — descoberta de provedores: beans + listas internas, chave unica. */
public class DynamicSelectionProviderRegistryTest {

  private static DynamicSelectionList lista(final String key, final String label, final String pai) {
    final DynamicSelectionList l = new DynamicSelectionList();
    l.setKey(key);
    l.setLabel(label);
    l.setDependsOnKey(pai);
    return l;
  }

  private static DynamicSelectionProvider bean(final String key) {
    return new DynamicSelectionProvider() {
      @Override public String key() { return key; }
      @Override public String label() { return "Plugin " + key; }
      @Override public Optional<String> dependsOnKey() { return Optional.empty(); }
      @Override public List<Option> options(final List<String> parentCodes) { return Collections.emptyList(); }
      @Override public List<Option> resolve(final List<String> codes) { return Collections.emptyList(); }
    };
  }

  @Test
  public void juntaBeansEListasInternasEmOrdem() {
    final DynamicSelectionListRepository listas = mock(DynamicSelectionListRepository.class);
    when(listas.findAllOrdered()).thenReturn(Arrays.asList(
      lista("ppa.programa", "PPA — Programas", null),
      lista("ppa.indicador", "PPA — Indicadores", "ppa.programa")));
    final DynamicSelectionProviderRegistry registry = new DynamicSelectionProviderRegistry(
      Optional.of(Collections.singletonList(bean("uo"))), listas, mock(DynamicSelectionSourceRepository.class));

    assertEquals(Arrays.asList("uo", "ppa.programa", "ppa.indicador"), Arrays.asList(registry.all().keySet().toArray()));
    assertEquals("ppa.programa", registry.get("ppa.indicador").dependsOnKey().get());
    assertFalse(registry.get("ppa.programa").dependsOnKey().isPresent());
  }

  @Test(expected = NegocioException.class)
  public void chaveDuplicadaEErroDeConfiguracao() {
    final DynamicSelectionListRepository listas = mock(DynamicSelectionListRepository.class);
    when(listas.findAllOrdered()).thenReturn(Collections.singletonList(lista("uo", "UO interna", null)));
    new DynamicSelectionProviderRegistry(Optional.of(Collections.singletonList(bean("uo"))), listas,
      mock(DynamicSelectionSourceRepository.class)).all();
  }

  @Test(expected = NegocioException.class)
  public void provedorInexistente() {
    final DynamicSelectionListRepository listas = mock(DynamicSelectionListRepository.class);
    when(listas.findAllOrdered()).thenReturn(Collections.emptyList());
    new DynamicSelectionProviderRegistry(Optional.empty(), listas, mock(DynamicSelectionSourceRepository.class)).get("x");
  }

  @Test
  public void listaInternaFiltraPeloPaiSoQuandoDependeDeOutra() {
    final DynamicSelectionSourceRepository fontes = mock(DynamicSelectionSourceRepository.class);
    final DynamicSelectionSource ind = new DynamicSelectionSource();
    ind.setListKey("ppa.indicador"); ind.setCode("I1"); ind.setLabel("Indicador 5001 - X"); ind.setParentCode("5001");
    when(fontes.findOptions(eq("ppa.indicador"), anyList())).thenReturn(Collections.singletonList(ind));
    when(fontes.findOptions(eq("ppa.programa"), anyList())).thenReturn(Collections.emptyList());

    final InternalListProvider indicador = new InternalListProvider(lista("ppa.indicador", "Ind", "ppa.programa"), fontes);
    final List<DynamicSelectionProvider.Option> opcoes = indicador.options(Collections.singletonList("5001"));
    assertEquals(1, opcoes.size());
    assertEquals("5001", opcoes.get(0).getParentCode());
    verify(fontes).findOptions("ppa.indicador", Collections.singletonList("5001"));

    // Lista sem pai ignora o parent recebido: programa nao filtra por ninguem.
    new InternalListProvider(lista("ppa.programa", "Prog", null), fontes).options(Collections.singletonList("5001"));
    verify(fontes).findOptions("ppa.programa", Collections.emptyList());
  }

  @Test
  public void resolveSemCodigosNaoConsultaOBanco() {
    final DynamicSelectionSourceRepository fontes = mock(DynamicSelectionSourceRepository.class);
    assertTrue(new InternalListProvider(lista("ppa.acao", "Acao", "ppa.programa"), fontes).resolve(Collections.emptyList()).isEmpty());
    verifyNoInteractions(fontes);
  }

}
